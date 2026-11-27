package net.client.mixin;

import net.client.replay.ReplayClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public class ReplayClientPlayNetworkHandlerMixin {
    @Inject(method = "onDisconnected", at = @At("TAIL"))
    private void client$onDisconnected(net.minecraft.text.Text reason, CallbackInfo ci) {
        if (ReplayClient.getRecordingManager() != null && ReplayClient.getRecordingManager().isActive()) {
            ReplayClient.getRecordingManager().stop();
        }
    }
}
