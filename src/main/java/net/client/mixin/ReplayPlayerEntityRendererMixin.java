package net.client.mixin;

import net.client.replay.ReplayClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.8.9's PlayerEntityRenderer only draws the main player when the render
 * dispatcher's camera entity is that same player.  Replay free camera and
 * "view another player" use a separate camera entity, so the recorded player
 * model was skipped even after isInvisible was cleared.
 *
 * Keep vanilla's first-person hide (camera inside the local player's head),
 * but draw the local body whenever the camera is detached or in third person.
 */
@Mixin(net.minecraft.client.render.entity.PlayerEntityRenderer.class)
public class ReplayPlayerEntityRendererMixin {
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isMainPlayer()Z")
    )
    private boolean client$keepReplayPlayerVisible(net.minecraft.client.network.AbstractClientPlayerEntity player) {
        boolean mainPlayer = player.isMainPlayer();
        if (!mainPlayer) return false;
        if (!ReplayClient.isReplayActive() && !ReplayClient.isReplayCaptureActive()) {
            return true;
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player != player || client.options == null) {
            return true;
        }
        net.minecraft.entity.Entity camera = client.getCameraEntity();
        // Only the classic first-person view looks out of the player's own
        // eyes and must not draw the body.  Third person and detached replay
        // cameras should always show the recorded player.
        return net.client.replay.session.ReplayView.shouldHideLocalPlayer(player);
    }

    /**
     * 第二层皮肤（帽子/外套/袖子/裤子）在 1.8.9 由实体 DataTracker 的部件位控制，
     * 合成出来的回放玩家这些位不可靠。这里在 setModelPose 跑完后直接把模型部件的
     * 可见性打开，绕开数据同步，保证一定是双层。
     */
    @Inject(method = "setModelPose", at = @At("TAIL"))
    private void client$forceSkinLayers(net.minecraft.client.network.AbstractClientPlayerEntity player,
            CallbackInfo ci) {
        if (!ReplayClient.isReplayActive() && !ReplayClient.isReplayCaptureActive()) return;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player != player) return;
        net.minecraft.client.render.entity.model.PlayerEntityModel model =
                ((net.minecraft.client.render.entity.PlayerEntityRenderer) (Object) this).getModel();
        model.hat.visible = true;
        model.jacket.visible = true;
        model.leftSleeve.visible = true;
        model.rightSleeve.visible = true;
        model.leftPants.visible = true;
        model.rightPants.visible = true;
    }
}
