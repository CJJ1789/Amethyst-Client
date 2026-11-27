package com.amethyst.blur.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.amethyst.blur.Blur;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

@Mixin(Screen.class)
public class ScreenMixin {

    /**
     * Replaces the two hardcoded dark gradient colors of the vanilla world background with
     * Blur's configurable (and fade-in animated) colors.
     */
    @Inject(method = "renderBackground(I)V", at = @At("HEAD"), cancellable = true)
    private void blur$renderBackground(int alpha, CallbackInfo ci) {
        Blur.bootstrap();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }

        Screen self = (Screen) (Object) this;
        ((DrawableHelperInvoker) (Object) this).blur$fillGradient(0, 0, self.width, self.height,
                Blur.getBackgroundColor(true), Blur.getBackgroundColor(false));
        ci.cancel();
    }
}
