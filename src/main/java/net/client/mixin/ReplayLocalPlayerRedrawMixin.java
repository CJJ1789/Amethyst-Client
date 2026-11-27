package net.client.mixin;

import net.client.replay.ReplayClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hard guarantee that the replayed local player is on screen.
 * <p>
 * Everything else (camera-entity skip in {@code renderEntities}, the
 * {@code isMainPlayer} gate, the invisible flags in {@code renderModel}, the
 * per-entity distance/frustum culling) can each drop the model on its own, and
 * which one fires depends on the view mode and how close the camera is.  Rather
 * than chase each gate, this draws the local body once more after the normal
 * entity pass, exactly the same way a remote player is drawn, whenever the
 * replay camera is detached from it.
 */
@Mixin(WorldRenderer.class)
public class ReplayLocalPlayerRedrawMixin {

    private static boolean announced;

    @Inject(method = "renderEntities", at = @At("RETURN"))
    private void client$redrawLocalPlayer(Entity cameraEntity, CameraView cameraView, float tickDelta,
                                          CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getEntityRenderManager() == null) {
            return;
        }
        if (!ReplayClient.isReplayActive() && !ReplayClient.isReplayCaptureActive()) {
            return;
        }
        Entity camera = client.getCameraEntity();
        if (net.client.replay.session.ReplayView.shouldHideLocalPlayer(client.player)) {
            // First person looking out of the player's own eyes: vanila hides the body.
            return;
        }
        try {
            client.getEntityRenderManager().renderEntity(client.player, tickDelta);
            if (!announced) {
                announced = true;
                if (ReplayClient.debugEnabled()) {
                    ReplayClient.LOGGER.info("[replay] local player redrawn by replay pass");
                }
            }
        } catch (Throwable ignored) {
            // A failed extra draw must never break the world render.
        }
    }
}
