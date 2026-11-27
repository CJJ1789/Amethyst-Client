package net.client.replay.session;

import net.client.replay.ReplayClient;
import net.client.replay.record.EntitySnapshot;
import net.client.replay.record.PacketCodec;
import net.client.replay.record.SnapshotSample;
import net.client.mixin.ReplayClientPlayNetworkHandlerAccessor;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.stat.StatHandler;
import net.minecraft.network.Packet;
import net.minecraft.entity.Entity;

public final class ReplaySession implements AutoCloseable {
    private final net.minecraft.client.MinecraftClient client;
    private final ReplayReader reader;
    private net.minecraft.client.network.ClientPlayNetworkHandler handler;
    private ReplayClientConnection connection;
    private int packetIndex;
    private long currentTime;
    private final Map<Integer, EntitySnapshot> baseStates = new HashMap<Integer, EntitySnapshot>();

    private int cachedA = -1;
    private int cachedB = -1;
    private Map<Integer, EntitySnapshot> mapA;
    private Map<Integer, EntitySnapshot> mapB;

    private CameraController cameraController;
    private boolean closed;
    private boolean replayWorldReady;

    // ---- recorded sound playback (foreground preview only) ----------------
    private List<net.client.replay.record.SoundEventRecord> soundEvents;
    private int soundIndex;
    private long soundCursorNanos;
    private boolean soundEventsLoaded;

    // ---- background (dual-world) rendering state -------------------------
    private boolean background;
    private boolean backgroundFrame;
    private net.minecraft.client.world.ClientWorld backgroundLiveWorld;
    private net.minecraft.entity.player.ClientPlayerEntity backgroundLivePlayer;
    private net.minecraft.client.network.ClientPlayerInteractionManager backgroundLiveInteraction;
    private net.minecraft.client.render.WorldRenderer backgroundLiveRenderer;
    private net.minecraft.client.particle.ParticleManager backgroundLiveParticles;
    private net.minecraft.entity.Entity backgroundLiveCamera;
    private net.minecraft.client.world.ClientWorld replayWorld;
    private net.minecraft.entity.player.ClientPlayerEntity replayPlayer;
    private net.minecraft.client.network.ClientPlayerInteractionManager replayInteraction;
    private net.minecraft.client.render.WorldRenderer replayRenderer;
    private net.minecraft.client.particle.ParticleManager replayParticles;

    public ReplaySession(net.minecraft.client.MinecraftClient client, ReplayReader reader) {
        this.client = client;
        this.reader = reader;
    }

    public void start(long startTimeNanos) throws IOException {
        start(startTimeNanos, false);
    }

    /**
     * @param preserveScreen keep the calling editor/progress screen instead of
     *                       falling through to the terrain/downloading screen.
     */
    public void start(long startTimeNanos, boolean preserveScreen) throws IOException {
        background = false;
        // Validate BEFORE touching the live world.  A bad replay must never
        // unload the player's current world and land them on the title screen.
        if (!reader.hasGameJoin()) {
            throw new IOException("Replay 里没有 GameJoin 数据包（录制是从世界中途开始的）。请用新版重新录制，或选择包含加入世界记录的 .lcr。");
        }
        net.minecraft.client.gui.screen.Screen previousScreen = client.currentScreen;
        // Tear down the real connection without letting a replay packet escape.
        try {
            if (client.getNetworkHandler() != null && client.getNetworkHandler().getClientConnection() != null) {
                client.getNetworkHandler().getClientConnection().disconnect(new net.minecraft.text.LiteralText("[回放] Client"));
            }
        } catch (Throwable ignored) {
        }
        try {
            if (client.world != null) client.world.disconnect();
        } catch (Throwable ignored) {
        }
        client.world = null;
        client.player = null;
        replayWorldReady = false;

        connection = new ReplayClientConnection();
        handler = new net.minecraft.client.network.ClientPlayNetworkHandler(client, null, connection, client.getSession().getProfile());
        connection.setPacketListener(handler);
        // The vanilla handler closes the current screen on the first
        // PlayerPositionLook packet.  While rendering that screen is the
        // progress page, and its removed() would dispose the replay world mid-
        // load.  Replay sessions already install the player themselves, so the
        // vanilla "first position packet" screen transition must be disabled.
        ((ReplayClientPlayNetworkHandlerAccessor) handler).client$setPositionLookSetup(true);
        packetIndex = 0;
        currentTime = 0L;
        baseStates.clear();
        loadSoundEvents(startTimeNanos);
        try {
            // Some recordings place the join packet slightly after the first
            // chunk timestamp. Apply it explicitly first, so the world exists
            // even when startTime only reaches later packets.
            applyFirstJoin(startTimeNanos);
            advanceTo(startTimeNanos);
        } catch (Throwable t) {
            abortStart(previousScreen);
            throw new IOException("回放世界加载失败：" + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
        if (client.world == null) {
            abortStart(previousScreen);
            throw new IOException("回放没有成功创建世界（GameJoin 数据包可能损坏）。");
        }
        cameraController = new CameraController(client, reader);
        if (preserveScreen && previousScreen != null) {
            client.currentScreen = previousScreen;
        } else {
            client.setScreen(null);
        }
    }

    /**
     * Loads the replay into a second world while keeping the live world intact.
     * The replay world is only installed on MinecraftClient during a render
     * frame; between frames the live world/player/renderer are restored.  This
     * is what lets the user keep playing and recording during an export.
     */
    public void startBackground(long startTimeNanos) throws IOException {
        if (!reader.hasGameJoin()) {
            throw new IOException("这个 .lcr 没有 GameJoin 记录，无法载入回放世界；请用新版重新录制。");
        }
        if (client.world == null || client.player == null) {
            throw new IOException("后台渲染需要先进入一个游戏世界。");
        }
        captureLiveState();
        background = true;
        // Loading the replay applies PlaySound packets before the first render
        // frame; suppress them so they do not leak into the live world mix.
        ReplayClient.setReplayCaptureActive(true);
        connection = new ReplayClientConnection();
        handler = new net.minecraft.client.network.ClientPlayNetworkHandler(client, null, connection, client.getSession().getProfile());
        connection.setPacketListener(handler);
        ((ReplayClientPlayNetworkHandlerAccessor) handler).client$setPositionLookSetup(true);
        packetIndex = 0;
        currentTime = 0L;
        baseStates.clear();
        try {
            applyFirstJoin(startTimeNanos);
            advanceTo(startTimeNanos);
        } catch (Throwable t) {
            abortStartBackground();
            throw new IOException("回放世界加载失败：" + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
        if (replayWorld == null) {
            abortStartBackground();
            throw new IOException("回放没有成功创建世界（GameJoin 数据包可能损坏）。");
        }
        cameraController = new CameraController(client, reader);
        // Do not leave the replay world installed while the player is just
        // walking around; every frame will swap it in/out around capture.
        restoreLiveState();
        ReplayClient.setReplayCaptureActive(false);
    }

    public boolean isBackground() {
        return background;
    }

    /** False once the user has left/changed the live world under a background job. */
    public boolean isLiveStateCurrent() {
        if (!background) return true;
        if (backgroundFrame) return true;
        return client.world == backgroundLiveWorld && client.player == backgroundLivePlayer;
    }

    public boolean isBackgroundFrameActive() {
        return backgroundFrame;
    }

    private void captureLiveState() {
        backgroundLiveWorld = client.world;
        backgroundLivePlayer = client.player;
        backgroundLiveInteraction = client.interactionManager;
        backgroundLiveRenderer = client.worldRenderer;
        backgroundLiveParticles = client.particleManager;
        backgroundLiveCamera = client.getCameraEntity();
    }

    /** Install the replay world for one capture step. */
    public void beginBackgroundFrame() {
        if (!background || backgroundFrame || replayWorld == null) return;
        client.world = replayWorld;
        client.player = replayPlayer;
        client.interactionManager = replayInteraction;
        client.worldRenderer = replayRenderer;
        client.particleManager = replayParticles;
        client.getEntityRenderManager().setWorld(replayWorld);
        client.setCameraEntity(replayPlayer);
        ReplayClient.setReplayCaptureActive(true);
        backgroundFrame = true;
    }

    /** Restore the live world after a capture step. */
    public void endBackgroundFrame() {
        if (!backgroundFrame) return;
        try {
            if (cameraController != null) cameraController.restore();
        } catch (Throwable ignored) {
        }
        restoreLiveState();
        ReplayClient.setReplayCaptureActive(false);
        backgroundFrame = false;
    }

    private void restoreLiveState() {
        if (!background) return;
        client.world = backgroundLiveWorld;
        client.player = backgroundLivePlayer;
        client.interactionManager = backgroundLiveInteraction;
        client.worldRenderer = backgroundLiveRenderer;
        client.particleManager = backgroundLiveParticles;
        client.getEntityRenderManager().setWorld(backgroundLiveWorld);
        client.setCameraEntity(backgroundLiveCamera);
    }

    private void abortStartBackground() {
        try {
            if (handler != null) handler.clearWorld();
        } catch (Throwable ignored) {
        }
        try {
            if (replayWorld != null) replayWorld.disconnect();
        } catch (Throwable ignored) {
        }
        try {
            if (replayRenderer != null) {
                replayRenderer.setWorld(null);
                replayRenderer = null;
            }
        } catch (Throwable ignored) {
        }
        replayWorld = null;
        replayPlayer = null;
        replayInteraction = null;
        replayParticles = null;
        replayWorldReady = false;
        restoreLiveState();
        ReplayClient.setReplayCaptureActive(false);
        backgroundFrame = false;
        // Keep background=true: RenderJob's fail path will call close(), and
        // close must not take the non-background branch and disconnect the
        // live world when the replay simply failed to load.
    }

    /**
     * Old mid-world recordings built a synthetic GameJoin using the client
     * render distance in the dimension slot.  GameJoin's int field is named
     * viewDistance but on the wire it is the dimension id; an invalid value
     * makes ClientWorld's Dimension null and crashes.  Repair such records on
     * the fly so old .lcr files remain playable.
     */
    private net.minecraft.network.Packet<?> sanitizeGameJoin(net.minecraft.network.Packet<?> packet) {
        if (!(packet instanceof net.minecraft.network.packet.s2c.play.GameJoinS2CPacket)) return packet;
        net.minecraft.network.packet.s2c.play.GameJoinS2CPacket join =
                (net.minecraft.network.packet.s2c.play.GameJoinS2CPacket) packet;
        int dimension = join.getChunkLoadDistance();
        if (dimension >= -1 && dimension <= 1) return packet;
        net.minecraft.world.level.LevelInfo.GameMode mode = join.getGameMode();
        if (mode == null) mode = net.minecraft.world.level.LevelInfo.GameMode.SURVIVAL;
        net.minecraft.world.Difficulty difficulty = join.getDifficulty();
        if (difficulty == null) difficulty = net.minecraft.world.Difficulty.NORMAL;
        net.minecraft.world.level.LevelGeneratorType generator = join.getGeneratorType();
        if (generator == null) generator = net.minecraft.world.level.LevelGeneratorType.DEFAULT;
        ReplayClient.LOGGER.warn("Repairing GameJoin dimension {} -> 0 (old synthetic recording)", dimension);
        return new net.minecraft.network.packet.s2c.play.GameJoinS2CPacket(
                join.getEntityId(), mode, join.isHardcore(), 0, difficulty,
                join.getMaxPlayers() <= 0 ? 100 : join.getMaxPlayers(), generator, join.hasReducedDebugInfo());
    }

    /**
     * 1.0.2：原实现把流里**所有** GameJoin 依次应用一遍。单世界回放里最后一条等于唯一一条，
     * 看上去没事；但一次录制跨了两个服务器/维度时，最终生效的是「最后那条 join」——从开头播放
     * 也会直接落到最后那个世界，前面的实体/区块全对不上。
     * 现在按目标时间挑：优先用「时间 <= 目标时刻里最晚的一条」，失败再往前退，最后才用第一条兜底。
     * GameJoin 的下标由 ReplayReader 建索引时缓存，这里不再全量扫 packet 列表。
     */
    private void applyFirstJoin(long targetNanos) {
        java.util.List<Integer> joins = reader.joinPacketIndexes();
        if (joins.isEmpty()) return;
        java.util.List<Integer> atOrBefore = new java.util.ArrayList<Integer>();
        for (int index : joins) {
            if (reader.packet(index).timeNanos <= targetNanos) atOrBefore.add(index);
        }
        for (int k = atOrBefore.size() - 1; k >= 0; k--) {
            if (applyJoinRecord(atOrBefore.get(k))) return;
        }
        if (applyJoinRecord(joins.get(0))) return;
        // 兜底：其余 join 也试一遍（例如某条 join 的 packet 本身损坏，但后面还有可用的）。
        for (int index : joins) {
            if (index == joins.get(0) || atOrBefore.contains(index)) continue;
            if (applyJoinRecord(index)) return;
        }
    }

    private boolean applyJoinRecord(int index) {
        ReplayReader.PacketRecord record = reader.packet(index);
        try {
            net.minecraft.network.Packet<?> packet = sanitizeGameJoin(
                    PacketCodec.decode(record.className, reader.readPacketPayload(record)));
            applyPacket(packet);
            ReplayClient.LOGGER.info("Replay GameJoin applied at {}ns, world={}", record.timeNanos, client.world != null);
            return replayWorldReady;
        } catch (Throwable t) {
            ReplayClient.LOGGER.error("Replay GameJoin decode/apply failed ({} @ {}ns)",
                    record.className, record.timeNanos, t);
            return false;
        }
    }

    private void abortStart(net.minecraft.client.gui.screen.Screen previousScreen) {
        try {
            if (handler != null) handler.clearWorld();
        } catch (Throwable ignored) {
        }
        try {
            if (client.world != null) client.world.disconnect();
        } catch (Throwable ignored) {
        }
        client.world = null;
        client.player = null;
        if (previousScreen != null) {
            client.currentScreen = previousScreen;
        } else {
            client.setScreen(null);
        }
    }

    public net.minecraft.client.world.ClientWorld getWorld() {
        return client.world;
    }

    public net.minecraft.client.MinecraftClient getClient() {
        return client;
    }

    public ReplayReader getReader() {
        return reader;
    }

    public long currentTime() {
        return currentTime;
    }

    private void restoreBaseStates() {
        if (client.world == null) return;
        List<net.minecraft.entity.Entity> entities = client.world.getLoadedEntities();
        if (entities == null) return;
        for (net.minecraft.entity.Entity entity : entities) {
            EntitySnapshot base = baseStates.get(entity.getEntityId());
            if (base != null) base.applyTo(entity);
        }
    }

    private void captureBaseStates() {
        baseStates.clear();
        if (client.world == null) return;
        List<net.minecraft.entity.Entity> entities = client.world.getLoadedEntities();
        if (entities == null) return;
        for (net.minecraft.entity.Entity entity : entities) {
            baseStates.put(entity.getEntityId(), EntitySnapshot.capture(entity, 1.0F));
        }
    }

    /**
     * Applies every packet up to {@code timeNanos}, then puts entities into the
     * interpolated visual state for that instant.
     */
    public void seekTo(long timeNanos) {
        if (closed) return;
        if (timeNanos < currentTime) {
            resetWorld(timeNanos);
            return;
        }
        advanceTo(timeNanos);
    }

    /** Rebuilds the replay world when scrubbing backwards. */
    private void resetWorld(long timeNanos) {
        net.minecraft.client.gui.screen.Screen previousScreen = client.currentScreen;
        try {
            if (handler != null) handler.clearWorld();
        } catch (Throwable ignored) {
        }
        try {
            if (client.world != null) client.world.disconnect();
        } catch (Throwable ignored) {
        }
        client.world = null;
        client.player = null;
        replayWorldReady = false;
        connection = new ReplayClientConnection();
        handler = new net.minecraft.client.network.ClientPlayNetworkHandler(client, null, connection, client.getSession().getProfile());
        connection.setPacketListener(handler);
        // The vanilla handler closes the current screen on the first
        // PlayerPositionLook packet.  While rendering that screen is the
        // progress page, and its removed() would dispose the replay world mid-
        // load.  Replay sessions already install the player themselves, so the
        // vanilla "first position packet" screen transition must be disabled.
        ((ReplayClientPlayNetworkHandlerAccessor) handler).client$setPositionLookSetup(true);
        packetIndex = 0;
        currentTime = 0L;
        baseStates.clear();
        cachedA = -1;
        cachedB = -1;
        loadSoundEvents(timeNanos);
        if (cameraController != null) cameraController.resetForWorld();
        // Rebuild the world explicitly before replaying packets; starting at
        // a chunk/entity packet on some streams otherwise leaves client.world
        // null for the rest of the preview.
        applyFirstJoin(timeNanos);
        advanceTo(timeNanos);
        if (previousScreen != null) client.currentScreen = previousScreen;
    }

    public void advanceTo(long timeNanos) {
        if (closed) return;
        int count = reader.getPacketCount();
        if (packetIndex < count && reader.packet(packetIndex).timeNanos <= timeNanos) {
            // Packet handlers must see packet-space positions, not the visual
            // positions applied for the previous rendered frame.
            restoreBaseStates();
            while (packetIndex < count) {
                ReplayReader.PacketRecord record = reader.packet(packetIndex);
                if (record.timeNanos > timeNanos) break;
                packetIndex++;
                try {
                    byte[] payload = reader.readPacketPayload(record);
                    net.minecraft.network.Packet<?> packet = sanitizeGameJoin(PacketCodec.decode(record.className, payload));
                    applyPacket(packet);
                } catch (Throwable t) {
                    if (ReplayClient.getConfig() != null && (ReplayClient.getConfig().debug
                            || client.world == null || (record.className != null && record.className.contains("GameJoin")))) {
                        ReplayClient.LOGGER.warn("Replay packet failed: {} @ {}ns", record.className, record.timeNanos, t);
                    }
                }
            }
            captureBaseStates();
        }
        applySnapshots(timeNanos);
        currentTime = timeNanos;
        playSoundsTo(timeNanos);
        if (cameraController != null) cameraController.update(timeNanos / 1.0e9D);
        if (cameraController != null) cameraController.syncFirstPersonHand();
    }

    @SuppressWarnings("unchecked")
    private void applyPacket(net.minecraft.network.Packet<?> packet) {
        if (packet instanceof net.minecraft.network.packet.s2c.play.GameJoinS2CPacket) {
            // applyFirstJoin() already installed the world; replaying the same
            // join packet in advanceTo() would replace it a second time and
            // discard entities spawned in between.
            if (!replayWorldReady) applyReplayGameJoin((net.minecraft.network.packet.s2c.play.GameJoinS2CPacket) packet);
            return;
        }
        // Server-side container/inventory screens must not take over the
        // playback editor.  Vanilla handles these packets by calling
        // ClientPlayerEntity.openInventory(), which replaces the current
        // ReplayEditorScreen; after that T is consumed by the container screen
        // and the editor appears to be dead.  Replay only needs the world and
        // entities, not an interactive GUI.
        if (packet instanceof net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket || packet instanceof net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket) {
            return;
        }
        ((net.minecraft.network.Packet) packet).apply(handler);
    }

    /**
     * Install a replay world without calling ClientPlayNetworkHandler#onGameJoin.
     * On some launchers/client states MinecraftClient.connect() leaves player
     * null there (the vanilla method immediately dereferences client.player),
     * which made rendering fail with "GameJoin 数据包可能损坏".  Doing the setup
     * ourselves also lets us guarantee that a player exists for the camera.
     */
    private void applyReplayGameJoin(net.minecraft.network.packet.s2c.play.GameJoinS2CPacket join) {
        // 回放世界的本地玩家没有 PlayerListEntry，1.8.9 会因此退回默认皮肤（史蒂夫）。
        // 在把 client.player 换成回放玩家之前，先把真实玩家已经加载好的皮肤记下来。
        ReplayLocalSkin.capture(client.player);
        ReplayLocalSkin.installEntry(handler,
                reader == null ? null : reader.meta.playerUuid,
                reader == null ? null : reader.meta.playerName,
                reader == null ? null : reader.meta.playerSkin);
        net.minecraft.world.level.LevelInfo.GameMode mode = join.getGameMode();
        if (mode == null) mode = net.minecraft.world.level.LevelInfo.GameMode.SURVIVAL;
        net.minecraft.world.Difficulty difficulty = join.getDifficulty();
        if (difficulty == null) difficulty = net.minecraft.world.Difficulty.NORMAL;
        net.minecraft.world.level.LevelGeneratorType generator = join.getGeneratorType();
        if (generator == null) generator = net.minecraft.world.level.LevelGeneratorType.DEFAULT;
        int dimension = join.getChunkLoadDistance();
        if (dimension < -1 || dimension > 1) dimension = 0;

        net.minecraft.client.network.ClientPlayerInteractionManager interaction = new net.minecraft.client.network.ClientPlayerInteractionManager(client, handler);
        net.minecraft.client.world.ClientWorld world = new net.minecraft.client.world.ClientWorld(handler,
                new net.minecraft.world.level.LevelInfo(0L, mode, false, join.isHardcore(), generator),
                dimension, difficulty, client.profiler);
        ((ReplayClientPlayNetworkHandlerAccessor) handler).client$setWorld(world);

        if (background) {
            // Never call client.connect() here: it calls soundManager.stopAll()
            // and reparents the live player.  Build the replay world directly,
            // keep a dedicated WorldRenderer/ParticleManager, and swap them in
            // only for the capture frame.
            replayWorld = world;
            replayInteraction = interaction;
            if (replayRenderer == null) {
                replayRenderer = new net.minecraft.client.render.WorldRenderer(client);
                replayRenderer.setWorld(world);
            } else {
                replayRenderer.setWorld(world);
            }
            replayParticles = new net.minecraft.client.particle.ParticleManager(world, client.getTextureManager());
            replayPlayer = new net.minecraft.entity.player.ClientPlayerEntity(client, world, handler, new net.minecraft.stat.StatHandler());
            ReplayLocalSkin.applyModelParts(replayPlayer);
            client.interactionManager = interaction;
            client.world = world;
            client.player = replayPlayer;
            client.worldRenderer = replayRenderer;
            client.particleManager = replayParticles;
            world.spawnEntity(replayPlayer);
            replayPlayer.afterSpawn();
            client.setCameraEntity(replayPlayer);
        } else {
            client.interactionManager = interaction;
            client.connect(world);
            if (client.player == null) {
                client.player = new net.minecraft.entity.player.ClientPlayerEntity(client, world, handler, new net.minecraft.stat.StatHandler());
                ReplayLocalSkin.applyModelParts(client.player);
                world.spawnEntity(client.player);
                client.player.afterSpawn();
                client.setCameraEntity(client.player);
            }
        }
        client.player.dimension = dimension;
        client.player.setEntityId(join.getEntityId());
        client.player.setReducedDebugInfo(join.hasReducedDebugInfo());
        client.interactionManager.setGameMode(mode);
        if (client.options != null) client.options.onPlayerModelPartChange();
        replayWorldReady = true;
    }

    private void applySnapshots(long timeNanos) {
        if (client.world == null || reader.getSnapshotCount() == 0) return;
        int index = reader.findSnapshotIndex(timeNanos);
        if (index < 0) index = 0;
        SnapshotSample a;
        SnapshotSample b;
        try {
            a = reader.snapshot(index);
            b = index + 1 < reader.getSnapshotCount() ? reader.snapshot(index + 1) : null;
        } catch (IOException e) {
            return;
        }
        if (a == null) return;
        if (index != cachedA) {
            cachedA = index;
            mapA = toMap(a);
        }
        if (b != null && index + 1 != cachedB) {
            cachedB = index + 1;
            mapB = toMap(b);
        } else if (b == null) {
            cachedB = -1;
            mapB = null;
        }
        float progress = 1.0F;
        if (b != null && b.timeNanos > a.timeNanos) {
            progress = (float) ((timeNanos - a.timeNanos) * 1.0D / (b.timeNanos - a.timeNanos));
            if (progress < 0.0F) progress = 0.0F;
            if (progress > 1.0F) progress = 1.0F;
        }

        List<net.minecraft.entity.Entity> entities = client.world.getLoadedEntities();
        if (entities == null) return;
        for (net.minecraft.entity.Entity entity : entities) {
            EntitySnapshot sa = mapA.get(entity.getEntityId());
            EntitySnapshot sb = mapB == null ? null : mapB.get(entity.getEntityId());
            EntitySnapshot visual = interpolate(sa, sb, progress);
            if (visual != null) visual.applyTo(entity);
        }
    }

    private static Map<Integer, EntitySnapshot> toMap(SnapshotSample sample) {
        Map<Integer, EntitySnapshot> map = new HashMap<Integer, EntitySnapshot>();
        if (sample != null) {
            for (EntitySnapshot entity : sample.entities) {
                map.put(entity.entityId, entity);
            }
        }
        return map;
    }

    private static EntitySnapshot interpolate(EntitySnapshot a, EntitySnapshot b, float progress) {
        if (a == null) return b;
        if (b == null || progress >= 1.0F) return a;
        if (progress <= 0.0F) return a;
        EntitySnapshot s = new EntitySnapshot();
        s.entityId = a.entityId;
        s.x = lerp(a.x, b.x, progress);
        s.y = lerp(a.y, b.y, progress);
        s.z = lerp(a.z, b.z, progress);
        s.yaw = EntitySnapshot.lerpAngle(a.yaw, b.yaw, progress);
        s.pitch = lerp(a.pitch, b.pitch, progress);
        s.headYaw = EntitySnapshot.lerpAngle(a.headYaw, b.headYaw, progress);
        s.bodyYaw = EntitySnapshot.lerpAngle(a.bodyYaw, b.bodyYaw, progress);
        s.velocityX = lerp(a.velocityX, b.velocityX, progress);
        s.velocityY = lerp(a.velocityY, b.velocityY, progress);
        s.velocityZ = lerp(a.velocityZ, b.velocityZ, progress);
        s.limbA = lerp(a.limbA, b.limbA, progress);
        s.limbB = lerp(a.limbB, b.limbB, progress);
        s.limbC = lerp(a.limbC, b.limbC, progress);
        s.limbD = lerp(a.limbD, b.limbD, progress);
        s.swingProgress = lerp(a.swingProgress, b.swingProgress, progress);
        s.flags = progress < 0.5F ? a.flags : b.flags;
        s.hurtTime = progress < 0.5F ? a.hurtTime : b.hurtTime;
        s.width = lerp(a.width, b.width, progress);
        s.height = lerp(a.height, b.height, progress);
        // 物品不是连续量，直接取最近的关键帧，别让 u8 数组丢失。
        EntitySnapshot equip = progress < 0.5F ? a : b;
        s.hotbarId = equip.hotbarId;
        s.hotbarDamage = equip.hotbarDamage;
        s.hotbarCount = equip.hotbarCount;
        s.selectedSlot = equip.selectedSlot;
        return s;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Loads the recorded sound events and moves the cursor to the start time.
     * Background exports silently mix their own audio track, so they never
     * load (and never play) sound events.
     */
    private void loadSoundEvents(long startTimeNanos) {
        soundEventsLoaded = true;
        soundIndex = 0;
        soundCursorNanos = startTimeNanos;
        if (background) {
            soundEvents = null;
            return;
        }
        soundEvents = reader.hasSoundEvents() ? reader.readSoundEvents()
                                              : java.util.Collections.<net.client.replay.record.SoundEventRecord>emptyList();
        if (soundEvents == null) return;
        while (soundIndex < soundEvents.size() && soundEvents.get(soundIndex).timeNanos <= startTimeNanos) {
            soundIndex++;
        }
    }

    /**
     * Plays every event between the previous frame and {@code timeNanos}.
     * A backwards jump or a long stall (loading hitch, big scrub) moves the
     * cursor without firing a burst of stale sounds.
     */
    private void playSoundsTo(long timeNanos) {
        if (closed || background || !soundEventsLoaded) return;
        if (soundEvents == null || soundEvents.isEmpty()) return;
        if (timeNanos < soundCursorNanos) {
            soundIndex = 0;
            while (soundIndex < soundEvents.size() && soundEvents.get(soundIndex).timeNanos <= timeNanos) {
                soundIndex++;
            }
            soundCursorNanos = timeNanos;
            return;
        }
        if (timeNanos - soundCursorNanos > 400000000L) {
            while (soundIndex < soundEvents.size() && soundEvents.get(soundIndex).timeNanos <= timeNanos) {
                soundIndex++;
            }
            soundCursorNanos = timeNanos;
            return;
        }
        while (soundIndex < soundEvents.size() && soundEvents.get(soundIndex).timeNanos <= timeNanos) {
            playRecordedSound(soundEvents.get(soundIndex));
            soundIndex++;
        }
        soundCursorNanos = timeNanos;
    }

    private void playRecordedSound(net.client.replay.record.SoundEventRecord event) {
        if (event == null || client.getSoundManager() == null) return;
        if (event.volume <= 0.0001F) return;
        String id = event.eventPath != null && !event.eventPath.isEmpty() ? event.eventPath : event.soundPath;
        if (id == null || id.isEmpty()) return;
        try {
            // 1.0.2：这里原来对每个声音无条件打两行 System.out（一次在 play 前、一次在
            // play 后），一场战斗就能往 stdout 灌几千行，还把日志文件顶满。改成只在调试开关
            // 打开时走 logger。
            if (ReplayClient.debugEnabled()) {
                ReplayClient.LOGGER.info("[ReplaySound] play {} @{}ns vol={} pitch={}",
                        id, event.timeNanos, event.volume, event.pitch);
            }
            client.getSoundManager().play(new ReplaySoundInstance(
                    new net.minecraft.util.Identifier(id), event.volume, event.pitch, event.x, event.y, event.z));
        } catch (Throwable ignored) {
        }
    }

    public CameraController getCameraController() {
        return cameraController;
    }

    public void close() {
        if (closed) return;
        closed = true;
        ReplayLocalSkin.clear();
        if (background && !backgroundFrame && !isLiveStateCurrent()) {
            // The player left or changed worlds while a background job was
            // running.  Never restore the stale saved world over the new one.
            try {
                if (handler != null) handler.clearWorld();
            } catch (Throwable ignored) {
            }
            try {
                if (replayWorld != null) replayWorld.disconnect();
            } catch (Throwable ignored) {
            }
            try {
                if (replayRenderer != null) {
                    replayRenderer.setWorld(null);
                    replayRenderer = null;
                }
                if (client.world != null) client.getEntityRenderManager().setWorld(client.world);
            } catch (Throwable ignored) {
            }
            replayWorld = null;
            replayPlayer = null;
            replayInteraction = null;
            replayParticles = null;
            replayWorldReady = false;
            background = false;
            try {
                reader.close();
            } catch (IOException ignored) {
            }
            return;
        }
        if (background) {
            // Restore camera/player fields while the replay world is installed,
            // then remove the replay world without ever disconnecting the live
            // world or leaving the client pointing at a null world.
            beginBackgroundFrame();
            if (cameraController != null) {
                try {
                    cameraController.restore();
                } catch (Throwable ignored) {
                }
            }
            try {
                if (handler != null) handler.clearWorld();
            } catch (Throwable ignored) {
            }
            try {
                if (replayWorld != null) replayWorld.disconnect();
            } catch (Throwable ignored) {
            }
            try {
                if (replayRenderer != null) {
                    replayRenderer.setWorld(null);
                    replayRenderer = null;
                }
            } catch (Throwable ignored) {
            }
            endBackgroundFrame();
            replayWorld = null;
            replayPlayer = null;
            replayInteraction = null;
            replayParticles = null;
            replayWorldReady = false;
            background = false;
            try {
                reader.close();
            } catch (IOException ignored) {
            }
            return;
        }
        // Restore the player/camera before the replay world is torn down;
        // otherwise a free/target view leaves renderHand/fov globally changed.
        if (cameraController != null) {
            try {
                cameraController.restore();
            } catch (Throwable ignored) {
            }
        }
        try {
            if (handler != null) handler.clearWorld();
        } catch (Throwable ignored) {
        }
        try {
            if (client.world != null) client.world.disconnect();
        } catch (Throwable ignored) {
        }
        client.world = null;
        client.player = null;
        replayWorldReady = false;
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }
}
