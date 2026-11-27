package net.client.mixin;

import net.client.replay.ReplayClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replay rendering must show the recorded local player like any other player.
 * <p>
 * {@code LivingEntityRenderer.renderModel} drops the model whenever
 * {@code entity.isInvisible()} is true, or when the entity is invisible but the
 * local player is still allowed to see it.  Replay snapshots carry the
 * invisible flag from the moment of recording and the replay tick re-applies
 * it after the editor cleared it, so the body vanished again a frame later.
 * <p>
 * Here the local player is treated exactly like a remote player: always drawn,
 * unless the camera sits inside its own head (classic first person).  That
 * keeps F5 / 第三人称 and the detached replay camera intact.
 */
@Mixin(net.minecraft.client.render.entity.LivingEntityRenderer.class)
public class ReplayLocalPlayerVisibilityMixin {

    private static boolean announced;

    /** {@code !entity.isInvisible()} — force "visible" for the replayed local player. */
    @Redirect(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;isInvisible()Z"))
    private boolean client$forceVisible(LivingEntity entity) {
        if (client$hideLocalPlayer(entity)) {
            return true;
        }
        if (client$showLocalPlayer(entity)) {
            client$trace(entity);
            return false;
        }
        return entity.isInvisible();
    }

    /** {@code entity.isInvisibleTo(player)} — same override for the translucent pass. */
    @Redirect(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;isInvisibleTo(Lnet/minecraft/entity/player/PlayerEntity;)Z"))
    private boolean client$forceInvisibleTo(LivingEntity entity, PlayerEntity viewer) {
        if (client$hideLocalPlayer(entity)) {
            return true;
        }
        if (client$showLocalPlayer(entity)) {
            return false;
        }
        return entity.isInvisibleTo(viewer);
    }

    private static long lastTraceMs;

    /** 每 2 秒打一次：本地玩家实际坐标 / 相机实体 / 两者距离，用来判断是"没画"还是"画在别处"。 */
    private static void client$trace(LivingEntity entity) {
        // 1.0.2：这段诊断原来在回放期间每 2 秒无条件打到 stdout / 日志，
        // 长时间预览会持续刷屏。改到 config 里的 debug 开关下。
        if (!ReplayClient.debugEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastTraceMs < 2000L) return;
        lastTraceMs = now;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        net.minecraft.entity.Entity camera = client.getCameraEntity();
        double dx = entity.x - (camera == null ? 0.0 : camera.x);
        double dy = entity.y - (camera == null ? 0.0 : camera.y);
        double dz = entity.z - (camera == null ? 0.0 : camera.z);
        ReplayClient.LOGGER.info(String.format(java.util.Locale.ROOT,
                "[replay] local player pos=(%.1f, %.1f, %.1f) invisible=%s cam=%s camPos=(%.1f, %.1f, %.1f) dist=%.1f persp=%d",
                entity.x, entity.y, entity.z, entity.isInvisible(),
                camera == null ? "null" : camera.getClass().getSimpleName(),
                camera == null ? 0.0 : camera.x, camera == null ? 0.0 : camera.y, camera == null ? 0.0 : camera.z,
                Math.sqrt(dx * dx + dy * dy + dz * dz),
                client.options == null ? -1 : client.options.perspective));
    }

    private static boolean client$isLocalPlayer(LivingEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && entity == client.player;
    }

    /** The camera sits inside the local player's head: vanilla must hide the body. */
    private static boolean client$hideLocalPlayer(LivingEntity entity) {
        if (!client$isLocalPlayer(entity)) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ReplayClient.isReplayActive() && !ReplayClient.isReplayCaptureActive()) return false;
        return net.client.replay.session.ReplayView.shouldHideLocalPlayer(entity);
    }

    /** Replay with a detached camera: the body belongs on screen like any other player. */
    private static boolean client$showLocalPlayer(LivingEntity entity) {
        if (!client$isLocalPlayer(entity)) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ReplayClient.isReplayActive() && !ReplayClient.isReplayCaptureActive()) return false;
        return !net.client.replay.session.ReplayView.shouldHideLocalPlayer(entity);
    }
}
