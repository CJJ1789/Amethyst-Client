package net.client.mixin;

import net.client.replay.session.CameraController;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.8.9's Camera.update(PlayerEntity, boolean) ignores MinecraftClient
 * cameraEntity and always reads the passed player.  For the observed-player
 * view we substitute the target player for that argument, leaving the local
 * player model in the recorded position.
 */
@Mixin(net.minecraft.client.render.Camera.class)
public class ReplayCameraMixin {
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static net.minecraft.entity.player.PlayerEntity client$overrideCameraPlayer(net.minecraft.entity.player.PlayerEntity original) {
        net.minecraft.entity.player.PlayerEntity target = CameraController.getCameraOverridePlayer();
        return target != null ? target : original;
    }
}
