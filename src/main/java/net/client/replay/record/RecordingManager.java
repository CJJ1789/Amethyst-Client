package net.client.replay.record;

import net.client.replay.ReplayClient;
import net.client.replay.util.ReplayPaths;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceOrbSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.MobSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PaintingSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.world.chunk.ClientChunkProvider;
import net.minecraft.client.sound.Sound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.EndCrystalEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;

public final class RecordingManager {
    private final net.minecraft.client.MinecraftClient client;
    private RecordingWriter writer;
    private SnapshotSampler sampler;
    private net.minecraft.network.ClientConnection connection;
    private long startNanos;
    private String requestedName;
    private volatile boolean active;
    private long firstChunkNanos = -1L;
    private final java.util.List<SoundEventRecord> soundEvents = new java.util.ArrayList<SoundEventRecord>();
    private Thread saveThread;

    public RecordingManager(net.minecraft.client.MinecraftClient client) {
        this.client = client;
    }

    public boolean isActive() {
        return active;
    }

    public long elapsedNanos() {
        return active ? System.nanoTime() - startNanos : 0L;
    }

    public synchronized void start(String name) {
        if (active) {
            ReplayClient.chat("已经在录制，先执行 .replay record stop。");
            return;
        }
        if (ReplayClient.isReplayActive() || (ReplayClient.getRenderManager().isActive()
                && !ReplayClient.getRenderManager().isBackgroundRendering())) {
            ReplayClient.chat("先停止回放/渲染任务再录制。");
            return;
        }
        if (client.world == null || client.player == null) {
            ReplayClient.chat("只能在进入游戏世界后录制；主菜单只用于编辑/渲染回放。");
            return;
        }
        net.minecraft.network.ClientConnection current = null;
        if (client.getNetworkHandler() != null) {
            current = client.getNetworkHandler().getClientConnection();
        }
        startWithConnection(current, name, true);
    }

    private void startWithConnection(net.minecraft.network.ClientConnection connection, String name, boolean allowSyntheticState) {
        try {
            writer = new RecordingWriter();
            sampler = new SnapshotSampler(ReplayClient.getConfig().sampleHz);
            this.connection = connection;
            this.requestedName = name;
            this.startNanos = System.nanoTime();
            this.firstChunkNanos = -1L;
            this.soundEvents.clear();
            this.active = true;
            if (allowSyntheticState) writeInitialState();
            ReplayClient.chat("开始录制" + (name == null || name.isEmpty() ? "" : "（" + name + "）")
                    + "，采样 " + ReplayClient.getConfig().sampleHz + " Hz。按 " + ReplayClient.stopKeyName() + " 停止。");
        } catch (Throwable t) {
            ReplayClient.LOGGER.error("Could not start recording", t);
            ReplayClient.chat("无法开始录制：" + t.getMessage());
            active = false;
        }
    }

    /**
     * Starting the recorder mid-world means no GameJoin/chunk burst will ever
     * arrive.  Capture a synthetic join + current chunks/entities so the replay
     * can still be opened without returning the player to the title screen.
     */
    private void writeInitialState() {
        if (client.world == null || client.player == null || writer == null) return;
        try {
            long t = 0L;
            net.minecraft.world.level.LevelInfo.GameMode gameMode = client.interactionManager == null
                    ? net.minecraft.world.level.LevelInfo.GameMode.SURVIVAL : client.interactionManager.getCurrentGameMode();
            if (gameMode == null) gameMode = net.minecraft.world.level.LevelInfo.GameMode.SURVIVAL;
            net.minecraft.world.Difficulty difficulty = client.world.getLevelProperties().getDifficulty();
            if (difficulty == null) difficulty = net.minecraft.world.Difficulty.NORMAL;
            net.minecraft.world.level.LevelGeneratorType generatorType = client.world.getLevelProperties().getGeneratorType();
            if (generatorType == null) generatorType = net.minecraft.world.level.LevelGeneratorType.DEFAULT;
            net.minecraft.network.packet.s2c.play.GameJoinS2CPacket join = new net.minecraft.network.packet.s2c.play.GameJoinS2CPacket(
                    client.player.getEntityId(),
                    gameMode,
                    client.world.getLevelProperties().isHardcore(),
                    // This int is the dimension id, not a view distance; the
                    // packet field is misleadingly named. Passing the option
                    // view distance makes Dimension.getById() return null and
                    // ClientWorld's constructor crashes.
                    client.world.dimension.getType(),
                    difficulty,
                    100,
                    generatorType,
                    client.player.getReducedDebugInfo());
            writer.writePacket(t, join);
            writer.writePacket(t, new net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket(client.player.x, client.player.y, client.player.z,
                    client.player.yaw, client.player.pitch, Collections.<PlayerPositionLookS2CPacket.Flag>emptySet()));

            // Other players need a tab-list profile before PlayerSpawnS2CPacket is applied.
            for (net.minecraft.entity.Entity entity : client.world.getLoadedEntities()) {
                if (!(entity instanceof net.minecraft.entity.player.PlayerEntity) || entity == client.player) continue;
                net.minecraft.entity.player.PlayerEntity player = (net.minecraft.entity.player.PlayerEntity) entity;
                if (player.getGameProfile() == null) continue;
                writePlayerListAdd(player);
                writer.writePacket(t, new net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket(player));
            }

            if (client.world.getChunkProvider() instanceof net.minecraft.world.chunk.ClientChunkProvider) {
                List<net.minecraft.world.chunk.Chunk> chunks = ((net.client.mixin.ReplayClientChunkProviderAccessor) client.world.getChunkProvider()).client$getChunks();
                int index = 0;
                for (net.minecraft.world.chunk.Chunk chunk : chunks) {
                    if (chunk == null || chunk.isEmpty()) continue;
                    writer.writePacket(index++, new net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket(chunk, true, 0xFFFF));
                }
                if (index > 0) this.firstChunkNanos = 0L;
            }

            for (net.minecraft.entity.Entity entity : client.world.getLoadedEntities()) {
                if (entity == null || entity == client.player || entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
                writeInitialEntitySpawn(t, entity);
            }
            ReplayClient.LOGGER.info("Captured initial world state for mid-world recording: {} chunks", writer.getPacketCount());
        } catch (Throwable t) {
            ReplayClient.LOGGER.warn("Could not capture initial world state", t);
        }
    }

    /**
     * Mid-world recordings have no server spawn burst to replay, so the
     * currently loaded entities are synthesized.  Living entities MUST use
     * MobSpawnS2CPacket: EntitySpawnS2CPacket is the *object* spawn packet and
     * maps type 51 to an End Crystal, type 50 to TNT, and leaves slimes/zombies
     * as null.  That was the "slime becomes end crystal / zombie disappears"
     * bug.  Objects use their object-packet type, not EntityType's raw id.
     */
    private void writeInitialEntitySpawn(long timeNanos, net.minecraft.entity.Entity entity) throws IOException {
        if (entity instanceof net.minecraft.entity.LivingEntity) {
            writer.writePacket(timeNanos, new net.minecraft.network.packet.s2c.play.MobSpawnS2CPacket((net.minecraft.entity.LivingEntity) entity));
            return;
        }
        if (entity instanceof net.minecraft.entity.ExperienceOrbEntity) {
            writer.writePacket(timeNanos, new net.minecraft.network.packet.s2c.play.ExperienceOrbSpawnS2CPacket((net.minecraft.entity.ExperienceOrbEntity) entity));
            return;
        }
        if (entity instanceof net.minecraft.entity.decoration.painting.PaintingEntity) {
            writer.writePacket(timeNanos, new net.minecraft.network.packet.s2c.play.PaintingSpawnS2CPacket((net.minecraft.entity.decoration.painting.PaintingEntity) entity));
            return;
        }
        int objectType = getObjectSpawnType(entity);
        if (objectType <= 0) {
            // Unsupported objects are better omitted than synthesized as a
            // wrong entity (e.g. an item frame becoming an end crystal).
            return;
        }
        int dataId = 0;
        if (entity instanceof net.minecraft.entity.ItemEntity) {
            net.minecraft.entity.ItemEntity item = (net.minecraft.entity.ItemEntity) entity;
            dataId = item.getItemStack() == null ? 0 : net.minecraft.item.Item.getRawId(item.getItemStack().getItem());
        }
        writer.writePacket(timeNanos, new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(entity, objectType, dataId));
    }

    /** SpawnObject packet ids (not EntityType raw ids). */
    private static int getObjectSpawnType(net.minecraft.entity.Entity entity) {
        if (entity instanceof net.minecraft.entity.vehicle.BoatEntity) return 1;
        if (entity instanceof net.minecraft.entity.ItemEntity) return 2;
        if (entity instanceof net.minecraft.entity.TntEntity) return 50;
        if (entity instanceof net.minecraft.entity.EndCrystalEntity) return 51;
        if (entity instanceof net.minecraft.entity.FallingBlockEntity) return 70;
        if (entity instanceof net.minecraft.entity.decoration.ItemFrameEntity) return 71;
        if (entity instanceof net.minecraft.entity.decoration.LeashKnotEntity) return 77;
        if (entity instanceof net.minecraft.entity.decoration.ArmorStandEntity) return 78; // normally LivingEntity
        return 0;
    }

    private void writePlayerListAdd(net.minecraft.entity.player.PlayerEntity player) {
        try {
            net.minecraft.network.packet.s2c.play.PlayerListS2CPacket packet = new net.minecraft.network.packet.s2c.play.PlayerListS2CPacket();
            ((net.client.mixin.ReplayPlayerListS2CPacketAccessor) packet).client$setAction(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action.ADD_PLAYER);
            ((net.client.mixin.ReplayPlayerListS2CPacketAccessor) packet).client$getEntries().add(packet.new Entry(
                    player.getGameProfile(), 0, net.minecraft.world.level.LevelInfo.GameMode.SURVIVAL, null));
            writer.writePacket(0L, packet);
        } catch (Throwable t) {
            ReplayClient.LOGGER.warn("Could not add player-list entry for {}", player.getName().asFormattedString(), t);
        }
    }

    /**
     * Called at the head of ClientConnection#channelRead0. The connection is this
     * exact instance, so integrated-server traffic cannot be confused with the
     * client's own stream.
     */
    public synchronized void onPacket(net.minecraft.network.ClientConnection from, net.minecraft.network.Packet<?> packet) {
        if (!active && ReplayClient.getConfig().autoRecord
                && !ReplayClient.isReplayActive()
                && packet instanceof net.minecraft.network.packet.s2c.play.GameJoinS2CPacket) {
            startWithConnection(from, "auto", false);
        }
        if (!active || writer == null) return;
        if (connection == null) connection = from;
        if (from != connection) return;
        long elapsed = System.nanoTime() - startNanos;
        if (firstChunkNanos < 0L && packet instanceof net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket) {
            firstChunkNanos = elapsed;
        }
        boolean written = writer.writePacket(elapsed, packet);
        if (!written && packet instanceof net.minecraft.network.packet.s2c.play.GameJoinS2CPacket) {
            // Some modded/optimized servers leave a nullable field null, which
            // makes the packet's own write() throw. Build a sanitized join so
            // the replay can still install a world.
            net.minecraft.network.packet.s2c.play.GameJoinS2CPacket join = (net.minecraft.network.packet.s2c.play.GameJoinS2CPacket) packet;
            try {
                net.minecraft.network.packet.s2c.play.GameJoinS2CPacket fallback = new net.minecraft.network.packet.s2c.play.GameJoinS2CPacket(
                        join.getEntityId(),
                        join.getGameMode() == null ? net.minecraft.world.level.LevelInfo.GameMode.SURVIVAL : join.getGameMode(),
                        join.isHardcore(),
                        join.getChunkLoadDistance(),
                        join.getDifficulty() == null ? net.minecraft.world.Difficulty.NORMAL : join.getDifficulty(),
                        join.getMaxPlayers() <= 0 ? 100 : join.getMaxPlayers(),
                        join.getGeneratorType() == null ? net.minecraft.world.level.LevelGeneratorType.DEFAULT : join.getGeneratorType(),
                        join.hasReducedDebugInfo());
                writer.writePacket(elapsed, fallback);
            } catch (Throwable t) {
                ReplayClient.LOGGER.warn("Could not build fallback GameJoin", t);
            }
        }
    }

    /**
     * Called from SoundManager#play for every sound actually started.  The
     * original player mix is reconstructed later from these events; no system
     * audio permission is involved.
     */
    public synchronized void onSound(net.minecraft.client.sound.SoundInstance instance) {
        if (!active || writer == null || instance == null) return;
        if (ReplayClient.isReplayActive() || ReplayClient.isReplayCaptureActive()) return;
        if (!ReplayClient.getConfig().recordGameAudio) return;
        try {
            String path = instance.getIdentifier().toString();
            net.minecraft.client.sound.SoundManager manager = client.getSoundManager();
            net.minecraft.client.sound.WeightedSoundSet set = manager == null ? null : manager.get(instance.getIdentifier());
            net.minecraft.client.sound.Sound sound = set == null ? null : set.getSound();
            if (sound != null) path = sound.getIdentifier().toString();
            net.minecraft.client.sound.SoundCategory category = set == null ? net.minecraft.client.sound.SoundCategory.MASTER : set.getSoundCategory();

            float volume = instance.getVolume();
            if (sound != null) volume = (float) Math.min(1.0D, Math.max(0.0D, volume * sound.getVolume()));
            volume *= client.options == null ? 1.0F : client.options.getSoundVolume(category);
            float pitch = instance.getPitch();
            if (sound != null) pitch = (float) Math.min(2.0D, Math.max(0.5D, pitch * sound.getPitch()));

            boolean attenuated = instance.getAttenuationType() == net.minecraft.client.sound.SoundInstance.AttenuationType.LINEAR;
            if (attenuated && client.player != null) {
                double dx = instance.getX() - client.player.x;
                double dy = instance.getY() - client.player.y;
                double dz = instance.getZ() - client.player.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double rolloff = 16.0D * Math.max(1.0D, instance.getVolume());
                volume *= (float) Math.max(0.0D, 1.0D - distance / rolloff);
            }
            if (volume <= 0.0001F) return;

            SoundEventRecord record = new SoundEventRecord();
            record.timeNanos = System.nanoTime() - startNanos;
            // The event id is what SoundManager#get can resolve, so the replay
            // can play this sound back in game (soundPath is the resolved file
            // and is only used by the offline audio mix).
            record.eventPath = instance.getIdentifier().toString();
            record.soundPath = path;
            record.x = instance.getX();
            record.y = instance.getY();
            record.z = instance.getZ();
            record.volume = volume;
            record.pitch = pitch;
            record.attenuated = attenuated;
            record.repeatable = instance.isRepeatable();
            soundEvents.add(record);
            ReplayClient.noteSoundRecorded();
            writer.writeSoundEvent(record);
        } catch (Throwable t) {
            ReplayClient.LOGGER.debug("Could not capture sound {}", instance.getIdentifier(), t);
        }
    }

    public synchronized void sample(long nowNanos, float tickDelta) {
        if (!active || sampler == null || writer == null) return;
        if (!sampler.shouldSample(nowNanos)) return;
        writer.writeSnapshot(sampler.sample(nowNanos - startNanos, client.world, tickDelta));
    }

    public void clientTick() {
        if (!active) return;
        // Leaving a world (or disconnecting) must finalize the .lcr immediately.
        // Otherwise the recorder would keep running invisibly on the title
        // screen, which is exactly the "main menu can still record" bug.
        if (client.world == null || client.player == null) {
            stop();
            return;
        }
        if (connection != null && client.getNetworkHandler() != null
                && client.getNetworkHandler().getClientConnection() != connection) {
            stop();
        }
    }

    public synchronized void stop() {
        if (!active || writer == null) {
            if (!active) ReplayClient.chat("当前没有在录制。");
            return;
        }
        active = false;
        final long duration = System.nanoTime() - startNanos;
        // Mixing every recorded .ogg used to run on the client thread and was
        // the main reason saving a long replay froze for seconds/minutes.
        // Keep the event list for the background saver: it decodes and mixes
        // there, while the player keeps playing.
        final List<SoundEventRecord> eventsToMix = new ArrayList<SoundEventRecord>(soundEvents);
        soundEvents.clear();
        final ReplayMeta meta = new ReplayMeta();
        meta.modVersion = net.client.util.ModVersion.get();
        meta.minecraftVersion = "1.8.9";
        meta.recordedAtMillis = System.currentTimeMillis();
        meta.durationNanos = duration;
        meta.sampleHz = ReplayClient.getConfig().sampleHz;
        meta.playerName = client.getSession() == null ? "" : client.getSession().getUsername();
        // 皮肤必须跟着回放一起存：回放是从主菜单启动的，那时没有"真实玩家"可供借用皮肤。
        try {
            com.mojang.authlib.GameProfile profile = null;
            if (client.player != null) {
                profile = client.player.getGameProfile();
            } else if (client.getSession() != null) {
                profile = client.getSession().getProfile();
            }
            if (profile != null) {
                if (profile.getId() != null) meta.playerUuid = profile.getId().toString();
                if (profile.getName() != null && !profile.getName().isEmpty()) meta.playerName = profile.getName();
                java.util.Collection<com.mojang.authlib.properties.Property> props =
                        profile.getProperties().get("textures");
                if (props != null) {
                    for (com.mojang.authlib.properties.Property prop : props) {
                        if (prop != null && prop.getValue() != null) {
                            meta.playerSkin = prop.getValue();
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 皮肤只是锦上添花，取不到也照常保存回放
        }
        meta.worldName = requestedName != null && !requestedName.isEmpty() ? requestedName : "replay";
        if (client.world != null && client.world.getLevelProperties() != null) {
            String levelName = client.world.getLevelProperties().getLevelName();
            if (levelName != null && !levelName.isEmpty()) {
                meta.worldName = levelName;
            }
        }
        meta.contentStartNanos = firstChunkNanos >= 0L ? firstChunkNanos : 0L;
        meta.fpsHint = ReplayClient.getConfig().defaultFps;
        meta.notes = "Recorded with Client replay.";

        final RecordingWriter toSave = writer;
        // Detach immediately: the player can keep playing (or start another
        // recording) while the ZIP is copied on a background thread.
        writer = null;
        soundEvents.clear();
        sampler = null;
        connection = null;
        requestedName = null;
        Thread saver = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!eventsToMix.isEmpty()) {
                        GameAudioMixer mixer = new GameAudioMixer(client);
                        mixer.mix(eventsToMix, duration, toSave.getGameAudioFile());
                    }
                    // 1.0.2：这行日志原来写在 finish() 之前，meta.soundEventCount 还是 0，
                    // 永远打印 "kept=0"，排查音频问题时误导性很强。
                    File file = toSave.finish(meta);
                    ReplayClient.LOGGER.info("[replay] sound: play() seen={} recorded={} kept={}",
                            ReplayClient.soundSeen, ReplayClient.soundRecorded, meta.soundEventCount);
                    ReplayClient.chat("回放已保存：" + file.getName() + "（" + meta.packetCount + " 个包，"
                            + meta.snapshotCount + " 个快照" + (meta.soundEventCount > 0 ? "，含声音事件" : "") + "）");
                } catch (Throwable t) {
                    ReplayClient.LOGGER.error("Could not save replay", t);
                    ReplayClient.chat("回放保存失败：" + t.getMessage());
                }
            }
        }, "ReplayClient-replay-save");
        saver.setDaemon(true);
        this.saveThread = saver;
        saver.start();
    }

    public void shutdown() {
        if (active) stop();
        Thread saver = saveThread;
        if (saver != null && saver.isAlive()) {
            try {
                saver.join(15000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (writer != null) writer.abort();
    }

    public String statusLine() {
        if (!active) return "Idle";
        // 1.0.2：writer 会在 stop()（另一条线程）里被置空，原来先判空再调用，
        // 中间被置空就会 NPE。先取本地引用。
        RecordingWriter current = writer;
        int packets = current == null ? 0 : current.getPacketCount();
        int samples = current == null ? 0 : current.getSnapshotCount();
        return String.format(java.util.Locale.US, "%.1f 秒，%d 包，%d 快照",
                elapsedNanos() / 1.0e9D, packets, samples);
    }

    public void status() {
        if (!active) {
            ReplayClient.chat("录制器空闲。");
            return;
        }
        double seconds = elapsedNanos() / 1.0e9D;
        RecordingWriter current = writer;
        ReplayClient.chat(String.format("Recording %.1fs, %d packets, %d samples, target %s",
                seconds,
                current == null ? 0 : current.getPacketCount(),
                current == null ? 0 : current.getSnapshotCount(),
                requestedName == null ? "auto" : requestedName));
        ReplayClient.chat("Replay folder: " + ReplayPaths.replaysDir().getAbsolutePath());
    }
}
