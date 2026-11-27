package com.amethyst.blur.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.amethyst.blur.Blur;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void blur$onScreenChange(Screen screen, CallbackInfo ci) {
        Blur.onScreenChange(screen);
    }

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void blur$ensureResourcePack(CallbackInfo ci) {
        Blur.ensureResourcePack((MinecraftClient) (Object) this);
    }
}
