package net.amethystesp.mixin;

import net.amethystesp.EspRenderer;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void amethystesp$renderEntities(Entity cameraEntity, CameraView cameraView, float tickDelta, CallbackInfo ci) {
        EspRenderer.render(tickDelta);
    }
}
