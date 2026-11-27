package net.client.mixin;

import net.client.mixin.ReplayMinecraftClientAccessor;
import net.client.replay.ReplayClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.MinecraftClient.class)
public class ReplayMinecraftClientMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void client$tick(CallbackInfo ci) {
        ReplayClient.onClientTick();
    }

    @Inject(method = "runGameLoop", at = @At("TAIL"))
    private void client$frame(CallbackInfo ci) {
        net.minecraft.client.MinecraftClient client = (net.minecraft.client.MinecraftClient) (Object) this;
        float tickDelta = ((ReplayMinecraftClientAccessor) client).client$getTicker().tickDelta;
        ReplayClient.onFrameEnd(tickDelta);
    }
}
