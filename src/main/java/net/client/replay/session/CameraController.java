package net.client.replay.session;

import net.client.replay.ReplayClient;
import net.client.mixin.ReplayGameRendererAccessor;
import java.io.File;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.stat.StatHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Drives the real GameRenderer camera.  Minecraft 1.8.9 derives the camera
 * position from client.player in GameRenderer.renderWorld, so a fake camera
 * entity alone is not enough: the player fields are moved (and restored) while
 * the fake entity supplies a render position without showing the skin.
 */
public final class CameraController {
    public static final int MODE_PLAYER = 0;
    public static final int MODE_FREE = 1;
    public static final int MODE_ENTITY = 2;

    private final net.minecraft.client.MinecraftClient client;
    private CameraPath path;
    private final File pathFile;
    private ReplayCameraEntity camera;
    /** Invisible player used only to feed yaw/pitch into 1.8.9 Camera.update. */
    private net.minecraft.entity.player.ClientPlayerEntity cameraActor;
    private int mode = MODE_PLAYER;
    private int perspective = 0;
    private int targetEntityId = -1;
    private double freeX;
    private double freeY;
    private double freeZ;
    private float freeYaw;
    private float freePitch;
    private float freeFov = 70.0F;
    private boolean pathEnabled;
    private boolean saved;
    private double savedX;
    private double savedY;
    private double savedZ;
    private double savedPrevX;
    private double savedPrevY;
    private double savedPrevZ;
    private float savedYaw;
    private float savedPitch;
    private float savedPrevYaw;
    private float savedPrevPitch;
    private boolean savedInvisible;
    private float savedFov;
    private net.minecraft.entity.Entity hiddenTarget;
    private boolean hiddenTargetWasInvisible;
    private boolean handHidden;
    private boolean savedRenderHand;
    /**
     * The target player whose first-person arm should be rendered.  Only set
     * while a target view is in first person; clear in every other mode so the
     * vanilla local-player hand is used again.
     */
    private static net.minecraft.client.network.AbstractClientPlayerEntity firstPersonHandPlayer;
    private static net.minecraft.entity.player.PlayerEntity cameraOverridePlayer;

    public static net.minecraft.client.network.AbstractClientPlayerEntity getFirstPersonHandPlayer() {
        return firstPersonHandPlayer;
    }

    /** Camera.update() uses MinecraftClient.player; target view overrides that argument. */
    public static net.minecraft.entity.player.PlayerEntity getCameraOverridePlayer() {
        return cameraOverridePlayer;
    }

    public CameraController(net.minecraft.client.MinecraftClient client, ReplayReader reader) {
        this.client = client;
        String configured = ReplayClient.getConfig() == null ? "" : ReplayClient.getConfig().cameraFile;
        File file;
        if (configured != null && !configured.isEmpty()) {
            file = new File(configured);
        } else {
            String name = reader.file.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            file = new File(reader.file.getParentFile(), base + ".camera.json");
        }
        this.pathFile = file;
        this.path = CameraPath.load(file);
        if (this.path == null) this.path = new CameraPath();
        this.pathEnabled = hasPath();
        if (client.options != null) this.perspective = client.options.perspective;
        if (client.player != null) {
            this.freeX = client.player.x;
            this.freeY = client.player.y + client.player.getEyeHeight();
            this.freeZ = client.player.z;
            this.freeYaw = client.player.yaw;
            this.freePitch = client.player.pitch;
        }
    }

    /** 保存时一起记下播放头，下次打开剪辑台能回到同一帧。 */
    public void setPlayhead(double seconds) {
        if (path == null) path = new CameraPath();
        path.editorSeconds = Math.max(0.0D, seconds);
    }

    /** 轨道文件里记录的播放头；没有记录时返回 -1。 */
    public double getSavedPlayhead() {
        return path == null ? -1.0D : path.editorSeconds;
    }

    public File getPathFile() {
        return pathFile;
    }

    public boolean hasPath() {
        return path != null && !path.keyframes.isEmpty();
    }

    public boolean isPathActive() {
        return pathEnabled && hasPath();
    }

    public int getMode() {
        return mode;
    }

    public int getPerspective() {
        return perspective;
    }

    public void setPerspective(int perspective) {
        this.perspective = Math.max(0, Math.min(2, perspective));
    }

    public int getTargetEntityId() {
        return targetEntityId;
    }

    /** Copy the editor's selected view into a freshly started render session. */
    public void copyStateFrom(CameraController other) {
        if (other == null) return;
        this.mode = other.mode;
        this.perspective = other.perspective;
        this.pathEnabled = other.pathEnabled;
        this.targetEntityId = other.targetEntityId;
        this.freeX = other.freeX;
        this.freeY = other.freeY;
        this.freeZ = other.freeZ;
        this.freeYaw = other.freeYaw;
        this.freePitch = other.freePitch;
        this.freeFov = other.freeFov;
        this.path = other.path;
    }

    public double getFreeX() { return freeX; }
    public double getFreeY() { return freeY; }
    public double getFreeZ() { return freeZ; }
    public float getFreeYaw() { return freeYaw; }
    public float getFreePitch() { return freePitch; }
    public float getFreeFov() { return freeFov; }

    public void setMode(int mode) {
        if (this.mode == mode) {
            this.pathEnabled = false;
            return;
        }
        restore();
        restoreHiddenTarget();
        this.mode = mode;
        this.pathEnabled = false;
    }

    public void setFreeTransform(double x, double y, double z, float yaw, float pitch, float fov) {
        setMode(MODE_FREE);
        this.freeX = x;
        this.freeY = y;
        this.freeZ = z;
        this.freeYaw = yaw;
        this.freePitch = Math.max(-89.9F, Math.min(89.9F, pitch));
        this.freeFov = Math.max(10.0F, Math.min(160.0F, fov));
    }

    /** Mouse/touch drag in the editor: positive dx turns right. */
    public void rotateFree(float deltaYaw, float deltaPitch) {
        if (this.mode != MODE_FREE) return;
        this.pathEnabled = false;
        this.freeYaw = wrapYaw(this.freeYaw + deltaYaw);
        this.freePitch = Math.max(-89.9F, Math.min(89.9F, this.freePitch + deltaPitch));
    }

    /** WASD movement for the free camera. One call represents one client tick. */
    public void moveFree(double forward, double strafe, double vertical) {
        moveFree(forward, strafe, vertical, 1.0D);
    }

    /** @param scale 每帧调用时传 dt*20，保证移动速度和帧率无关。 */
    public void moveFree(double forward, double strafe, double vertical, double scale) {
        if (this.mode != MODE_FREE || forward == 0.0D && strafe == 0.0D && vertical == 0.0D) return;
        this.pathEnabled = false;
        double yawRad = Math.toRadians(this.freeYaw);
        double pitchRad = Math.toRadians(this.freePitch);
        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);
        double speed = 0.38D * Math.max(0.05D, scale);
        this.freeX += (lookX * forward + rightX * strafe) * speed;
        this.freeY += lookY * forward * speed + vertical * 0.45D * Math.max(0.05D, scale);
        this.freeZ += (lookZ * forward + rightZ * strafe) * speed;
    }

    /** 切入自由相机时把机位对齐当前画面，避免从 0,0,0 开始漂移。 */
    public void resetFreeFromPlayer() {
        if (this.client.player == null) return;
        this.freeX = this.client.player.x;
        this.freeY = this.client.player.y + this.client.player.getEyeHeight();
        this.freeZ = this.client.player.z;
        this.freeYaw = this.client.player.yaw;
        this.freePitch = this.client.player.pitch;
    }

    private static float wrapYaw(float yaw) {
        while (yaw < -180.0F) yaw += 360.0F;
        while (yaw >= 180.0F) yaw -= 360.0F;
        return yaw;
    }

    public void setTargetEntity(int entityId) {
        setMode(MODE_ENTITY);
        this.targetEntityId = entityId;
    }

    public void usePath(boolean enabled) {
        if (enabled) {
            restore();
            restoreHiddenTarget();
        }
        this.pathEnabled = enabled && hasPath();
    }

    public CameraPath getPath() {
        return path;
    }

    /** 删除指定关键帧并立即写盘；轨道空了就关闭路径。 */
    public boolean deleteKeyframe(CameraPath.Keyframe frame) {
        if (path == null || frame == null) return false;
        boolean removed = path.keyframes.remove(frame);
        if (removed) {
            if (path.keyframes.isEmpty()) {
                pathEnabled = false;
            }
            savePath();
        }
        return removed;
    }

    public void clearPath() {
        if (path != null) path.keyframes.clear();
        this.pathEnabled = false;
    }

    public void addKeyframe(double time, double x, double y, double z, float yaw, float pitch, float fov) {
        CameraPath target = path;
        if (target == null) return;
        CameraPath.Keyframe frame = new CameraPath.Keyframe();
        frame.time = time;
        frame.mode = MODE_FREE;
        frame.targetEntityId = -1;
        frame.perspective = this.perspective;
        frame.x = x;
        frame.y = y;
        frame.z = z;
        frame.yaw = yaw;
        frame.pitch = pitch;
        frame.fov = fov;
        target.add(frame);
        this.pathEnabled = true;
    }

    /**
     * Capture the current editor view.  Unlike the old free-camera-only key
     * button, this records whether the key is a player view, a free camera or
     * another player's eyes, plus the selected F5 perspective.  The replay
     * camera follows the recorded entity at playback time, so camera keys stay
     * attached to a moving player instead of freezing their coordinates.
     */
    public void addCurrentKeyframe(double time) {
        CameraPath target = path;
        if (target == null) return;
        CameraPath.Keyframe frame = new CameraPath.Keyframe();
        frame.time = time;
        frame.mode = this.mode;
        frame.targetEntityId = this.mode == MODE_ENTITY ? this.targetEntityId : -1;
        frame.perspective = client.options != null ? client.options.perspective : this.perspective;
        frame.fov = client.options != null ? client.options.fov : this.freeFov;

        if (this.mode == MODE_FREE) {
            frame.x = this.freeX;
            frame.y = this.freeY;
            frame.z = this.freeZ;
            frame.yaw = this.freeYaw;
            frame.pitch = this.freePitch;
            frame.fov = this.freeFov;
        } else if (this.mode == MODE_ENTITY) {
            net.minecraft.entity.Entity entity = findTarget(this.targetEntityId);
            if (entity != null) {
                frame.x = entity.x;
                frame.y = entity.y + entity.getEyeHeight();
                frame.z = entity.z;
                frame.yaw = entity.yaw;
                frame.pitch = entity.pitch;
            }
        } else if (client.player != null) {
            frame.x = client.player.x;
            frame.y = client.player.y + client.player.getEyeHeight();
            frame.z = client.player.z;
            frame.yaw = client.player.yaw;
            frame.pitch = client.player.pitch;
            frame.perspective = client.options != null ? client.options.perspective : this.perspective;
        }
        target.add(frame);
        this.pathEnabled = true;
    }

    /** Perspective selected by a keyframe at this absolute replay time. */
    public int getPerspectiveAt(double seconds) {
        if (isPathActive() && path != null) {
            CameraPath.Transform transform = path.at(seconds);
            if (transform != null) return transform.perspective;
        }
        return perspective;
    }

    /** 把导出片段起止写进轨道文件；start/end 为 -1 表示清掉。 */
    public void setTrimRange(double start, double end) {
        if (this.path == null) return;
        this.path.trimStartSeconds = start;
        this.path.trimEndSeconds = end;
    }

    public double getSavedTrimStart() {
        return this.path == null ? -1.0D : this.path.trimStartSeconds;
    }

    public double getSavedTrimEnd() {
        return this.path == null ? -1.0D : this.path.trimEndSeconds;
    }

    public boolean savePath() {
        if (path == null || pathFile == null) return false;
        path.save(pathFile);
        ReplayClient.chat("Camera track saved: " + pathFile.getName());
        return true;
    }

    /** Called when the replay world is rebuilt after a backwards seek. */
    public void resetForWorld() {
        firstPersonHandPlayer = null;
        cameraOverridePlayer = null;
        cameraActor = null;
        restoreHiddenTarget();
        setHandHidden(false);
        this.camera = null;
        this.saved = false;
        if (client.player != null) {
            this.freeX = client.player.x;
            this.freeY = client.player.y + client.player.getEyeHeight();
            this.freeZ = client.player.z;
            this.freeYaw = client.player.yaw;
            this.freePitch = client.player.pitch;
        }
    }

    public void update(double seconds) {
        if (client.player == null) return;
        if (isPathActive()) {
            CameraPath.Segment seg = path.segmentAt(seconds);
            if (seg != null) {
                CameraPath.Keyframe a = seg.a;
                CameraPath.Keyframe b = seg.b;
                boolean sameView = a.mode == b.mode
                        && (a.mode != MODE_ENTITY || a.targetEntityId == b.targetEntityId);
                if (sameView) {
                    if (a.mode == MODE_FREE) {
                        // 自由到自由：正常插值飞行。
                        setHandHidden(true);
                        if (client.options != null) client.options.perspective = a.perspective;
                        CameraPath.Transform p = blend(poseOf(a), poseOf(b), (float) seg.t);
                        applyTransform(p.x, p.y, p.z, p.yaw, p.pitch, p.fov);
                        return;
                    }
                    if (a.mode == MODE_PLAYER) {
                        // 玩家视角连续段：整段保持玩家视角，不做位移。
                        restore();
                        client.player.setInvisible(false);
                        setHandHidden(false);
                        if (client.options != null) {
                            client.options.perspective = a.perspective;
                            client.options.fov = clampFov(a.fov);
                        }
                        return;
                    }
                    net.minecraft.entity.Entity target = findTarget(a.targetEntityId);
                    if (target != null && target != client.player) {
                        applyEntityView(target);
                        if (client.options != null) client.options.fov = clampFov(a.fov);
                        return;
                    }
                    restore();
                    client.player.setInvisible(false);
                    setHandHidden(false);
                    return;
                }
                // 视角切换：脱离相机，平滑地从当前视角位置移动到目标视角位置。
                // 例如 自由→玩家 时相机会缓缓钻进玩家身体，到关键帧时才切成玩家视角；
                // 玩家→自由 时从玩家眼睛位置飞出来。端点按“当前实时位置”取，玩家走动也跟得上。
                CameraPath.Transform from = poseOf(a);
                CameraPath.Transform to = poseOf(b);
                CameraPath.Transform p = blend(from, to, (float) seg.t);
                setHandHidden(true);
                if (client.options != null) client.options.perspective = b.perspective;
                applyTransform(p.x, p.y, p.z, p.yaw, p.pitch, p.fov);
                return;
            }
            CameraPath.Transform transform = path.at(seconds);
            if (transform != null) {
                if (client.options != null) client.options.perspective = transform.perspective;
                if (transform.mode == MODE_ENTITY) {
                    net.minecraft.entity.Entity target = findTarget(transform.targetEntityId);
                    if (target != null && target != client.player) {
                        applyEntityView(target);
                        if (client.options != null) client.options.fov = clampFov(transform.fov);
                        return;
                    }
                }
                if (transform.mode == MODE_FREE) {
                    setHandHidden(true);
                    applyTransform(transform.x, transform.y, transform.z, transform.yaw, transform.pitch, transform.fov);
                    return;
                }
                restore();
                client.player.setInvisible(false);
                setHandHidden(false);
                if (client.options != null) {
                    client.options.perspective = transform.perspective;
                    client.options.fov = clampFov(transform.fov);
                }
                return;
            }
        }
        if (mode == MODE_FREE) {
            setHandHidden(true);
            applyTransform(freeX, freeY, freeZ, freeYaw, freePitch, freeFov);
            return;
        }
        if (mode == MODE_ENTITY) {
            // 回放快照里可能带着 invisible 标记，每帧都要清掉，否则看别人时自己会消失。
            if (client.player != null) client.player.setInvisible(false);
            net.minecraft.entity.Entity target = findTarget();
            if (target != null && target != client.player) {
                applyEntityView(target);
                return;
            }
            // Target disappeared after a seek; never leave the local model
            // hidden in the entity-view fallback.
            restore();
            client.player.setInvisible(false);
            return;
        }
        restore();
        // The local model is visible in every player-view perspective.  Replay
        // snapshots can carry an invisible flag from the recording moment,
        // which made F5 look like the model was flickering in and out.
        if (mode == MODE_PLAYER) {
            client.player.setInvisible(false);
            if (client.options != null && client.options.perspective != 0
                    && client.getCameraEntity() != client.player) {
                // 第三人称（含正面 F5）把相机绑回被回放的自己；否则相机可能还停在
                // 上一次自由视角的机位，模型被近平面裁掉，看起来就是“自己消失了”。
                client.setCameraEntity(client.player);
            }
        }
    }

    /** 把关键帧换算成“当前这一帧该在哪”：玩家/目标视角取实时眼位，自由视角取记录坐标。 */
    private CameraPath.Transform poseOf(CameraPath.Keyframe frame) {
        CameraPath.Transform pose = new CameraPath.Transform();
        pose.mode = MODE_FREE;
        pose.perspective = frame.perspective;
        pose.x = frame.x;
        pose.y = frame.y;
        pose.z = frame.z;
        pose.yaw = frame.yaw;
        pose.pitch = frame.pitch;
        pose.fov = frame.fov;
        if (frame.mode == MODE_PLAYER && client.player != null) {
            pose.x = client.player.x;
            pose.y = client.player.y + client.player.getEyeHeight();
            pose.z = client.player.z;
            pose.yaw = client.player.yaw;
            pose.pitch = client.player.pitch;
        } else if (frame.mode == MODE_ENTITY) {
            net.minecraft.entity.Entity target = findTarget(frame.targetEntityId);
            if (target != null) {
                pose.x = target.x;
                pose.y = target.y + target.getEyeHeight();
                pose.z = target.z;
                pose.yaw = target.yaw;
                pose.pitch = target.pitch;
            }
        }
        return pose;
    }

    private static CameraPath.Transform blend(CameraPath.Transform a, CameraPath.Transform b, float t) {
        CameraPath.Transform out = new CameraPath.Transform();
        out.mode = MODE_FREE;
        out.perspective = b.perspective;
        out.x = a.x + (b.x - a.x) * t;
        out.y = a.y + (b.y - a.y) * t;
        out.z = a.z + (b.z - a.z) * t;
        out.yaw = a.yaw + wrapAngle(b.yaw - a.yaw) * t;
        out.pitch = a.pitch + (b.pitch - a.pitch) * t;
        out.fov = a.fov + (b.fov - a.fov) * t;
        return out;
    }

    private static float wrapAngle(float delta) {
        while (delta < -180.0F) delta += 360.0F;
        while (delta >= 180.0F) delta -= 360.0F;
        return delta;
    }

    private net.minecraft.entity.Entity findTarget() {
        return findTarget(targetEntityId);
    }

    private net.minecraft.entity.Entity findTarget(int entityId) {
        if (client.world == null || entityId < 0) return null;
        List<net.minecraft.entity.Entity> entities = client.world.getLoadedEntities();
        if (entities == null) return null;
        for (net.minecraft.entity.Entity entity : entities) {
            if (entity.getEntityId() == entityId) return entity;
        }
        return null;
    }

    private static float clampFov(float fov) {
        return Math.max(10.0F, Math.min(160.0F, fov));
    }

    private void savePlayerState(net.minecraft.entity.player.PlayerEntity player) {
        saved = true;
        savedX = player.x;
        savedY = player.y;
        savedZ = player.z;
        savedPrevX = player.prevX;
        savedPrevY = player.prevY;
        savedPrevZ = player.prevZ;
        savedYaw = player.yaw;
        savedPitch = player.pitch;
        savedPrevYaw = player.prevYaw;
        savedPrevPitch = player.prevPitch;
        savedInvisible = player.isInvisible();
        savedFov = client.options.fov;
    }

    private void applyTransform(double x, double y, double z, float yaw, float pitch, float fov) {
        firstPersonHandPlayer = null;
        cameraOverridePlayer = null;
        net.minecraft.entity.player.PlayerEntity player = client.player;
        if (player == null) return;
        if (!saved) savePlayerState(player);
        // Keep the recorded player exactly where the replay put it.  Moving
        // client.player to the camera used to drag the visible body along with
        // the free camera; only the invisible camera actor carries yaw/pitch.
        player.setInvisible(false);
        // 渲染时位置是 prevTick 与当前位置按 tickDelta 插值出来的。回放里客户端玩家
        // 不一定每个 tick 都被 tick（自由相机会暂停世界推进），prevTick 一旦停在旧值，
        // 身体就会被插到两处位置中间，看起来像"人不在原地的残影"。这里把插值两端
        // 对齐，模型才严格站在录制坐标上。
        player.prevTickX = player.x;
        player.prevTickY = player.y;
        player.prevTickZ = player.z;
        player.prevX = player.x;
        player.prevY = player.y;
        player.prevZ = player.z;
        player.prevYaw = player.yaw;
        player.prevPitch = player.pitch;
        player.headYaw = player.yaw;
        player.prevHeadYaw = player.yaw;
        player.bodyYaw = player.yaw;
        player.prevBodyYaw = player.yaw;
        client.options.fov = Math.max(10.0F, Math.min(160.0F, fov));
        if (camera == null && client.world != null) camera = new ReplayCameraEntity(client.world);
        if (camera != null) {
            camera.setTransform(x, y, z, yaw, pitch);
            client.setCameraEntity(camera);
        }
        updateCameraActor(x, y, z, yaw, pitch);
    }

    /**
     * 1.8.9 Camera.update always consumes a PlayerEntity even though the real
     * camera position comes from the camera entity.  Feed it a detached,
     * invisible actor so the local player can stay at the recorded position.
     */
    private void updateCameraActor(double x, double y, double z, float yaw, float pitch) {
        if (client.world == null) return;
        if (cameraActor == null) {
            net.minecraft.entity.player.ClientPlayerEntity networkPlayer = client.player;
            if (networkPlayer == null || networkPlayer.networkHandler == null) return;
            cameraActor = new net.minecraft.entity.player.ClientPlayerEntity(client, client.world, networkPlayer.networkHandler, new net.minecraft.stat.StatHandler());
            cameraActor.setInvisible(true);
        }
        cameraActor.world = client.world;
        cameraActor.x = cameraActor.prevX = cameraActor.prevTickX = x;
        cameraActor.y = cameraActor.prevY = cameraActor.prevTickY = y;
        cameraActor.z = cameraActor.prevZ = cameraActor.prevTickZ = z;
        cameraActor.yaw = cameraActor.prevYaw = yaw;
        cameraActor.pitch = cameraActor.prevPitch = pitch;
        cameraActor.dimension = client.player.dimension;
        cameraOverridePlayer = cameraActor;
    }

    /**
     * View from another player's eyes.  The local player must stay at its
     * recorded position and remain visible; moving it to the target (the old
     * implementation) hid the very model the user wants to see.  The target's
     * own model is hidden so its head does not fill the first-person camera.
     */
    private void applyEntityView(net.minecraft.entity.Entity target) {
        net.minecraft.entity.player.PlayerEntity player = client.player;
        if (player == null || target == player) return;
        if (!saved) savePlayerState(player);
        player.setInvisible(false);

        if (hiddenTarget != target) {
            restoreHiddenTarget();
            hiddenTarget = target;
            hiddenTargetWasInvisible = target.isInvisible();
        }

        // Hide the observed player's body only in first person; in third person
        // (F5) both the target and the recorded local player should be visible.
        boolean firstPerson = client.options.perspective == 0;
        if (firstPerson) {
            if (!target.isInvisible()) target.setInvisible(true);
            firstPersonHandPlayer = target instanceof net.minecraft.client.network.AbstractClientPlayerEntity
                    ? (net.minecraft.client.network.AbstractClientPlayerEntity) target : null;
        } else {
            // Third person must show the observed player even if the replay
            // snapshot marked them invisible at that moment.
            target.setInvisible(false);
            firstPersonHandPlayer = null;
        }

        // Camera.update() in 1.8.9 always uses MinecraftClient.player for its
        // position and rotation.  Move the *camera argument* to the target via
        // CameraMixin instead of moving the local player (which would destroy
        // the local model the user wants to see in third person).
        cameraOverridePlayer = target instanceof net.minecraft.entity.player.PlayerEntity ? (net.minecraft.entity.player.PlayerEntity) target : null;
        // transformCamera() and Camera.update() must agree; use the target as
        // the camera entity so the third-person distance is anchored at the
        // target, while the local player stays at its recorded position.
        client.setCameraEntity(target);
        client.options.fov = Math.max(10.0F, Math.min(160.0F, freeFov));
        // Target first person uses a mixin to render the target's own arm;
        // do not suppress the whole hand layer here.
        setHandHidden(false);
    }

    public void restore() {
        firstPersonHandPlayer = null;
        cameraOverridePlayer = null;
        restoreHiddenTarget();
        setHandHidden(false);
        if (client.player == null) {
            client.setCameraEntity(null);
            saved = false;
            return;
        }
        if (!saved) {
            client.setCameraEntity(client.player);
            return;
        }
        net.minecraft.entity.player.PlayerEntity player = client.player;
        // The replay player is positioned by EntitySnapshot every tick.  Do
        // not restore stale x/y/z or yaw/pitch captured when a free/entity
        // view started: that would teleport the recorded player backwards
        // whenever a keyframe switches back to the main player view.
        player.setInvisible(savedInvisible);
        client.options.fov = savedFov;
        client.setCameraEntity(player);
        saved = false;
    }

    private void restoreHiddenTarget() {
        if (hiddenTarget != null) {
            hiddenTarget.setInvisible(hiddenTargetWasInvisible);
            hiddenTarget = null;
        }
        cameraOverridePlayer = null;
    }

    /**
     * 玩家视角下只有第一人称才该出现手臂。1.8.9 的 {@code renderHand} 是 GameRenderer
     * 自己按 perspective 判定的，但回放里这条路径被我们接管过，F5 切到第三人称后
     * 手臂仍然挂在屏幕右下角（放大成一根柱子），所以这里每帧按视角重新同步一次。
     * 相机轨道播放期间由轨道自己的每段逻辑决定，不覆盖。
     */
    public void syncFirstPersonHand() {
        if (client.options == null || isPathActive()) return;
        if (mode != MODE_PLAYER) {
            setHandHidden(true);
            return;
        }
        setHandHidden(client.options.perspective != 0);
    }

    /** Free/target views must not render the local first-person arm. */
    private void setHandHidden(boolean hidden) {
        if (handHidden == hidden) return;
        try {
            ReplayGameRendererAccessor renderer = (ReplayGameRendererAccessor) (Object) client.gameRenderer;
            if (hidden) {
                savedRenderHand = renderer.client$getRenderHand();
                renderer.client$setRenderHand(false);
            } else {
                renderer.client$setRenderHand(savedRenderHand);
            }
            handHidden = hidden;
        } catch (Throwable ignored) {
        }
    }
}
