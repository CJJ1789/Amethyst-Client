package com.amethyst.blur.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.amethyst.blur.Blur;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.JsonGlProgram;
import net.minecraft.client.gl.PostProcessShader;
import net.minecraft.client.gl.ShaderEffect;

@Mixin(ShaderEffect.class)
public class ShaderEffectMixin {

    /**
     * Feeds the current radius and fade-in progress to every pass of the blur shader before
     * the effect is rendered. This replaces the original mod's RenderTickEvent handler and
     * also makes radius changes take effect immediately (no resource reload required).
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void blur$updateUniforms(float tickDelta, CallbackInfo ci) {
        Blur blur = Blur.getInstance();
        if (blur == null) {
            return;
        }

        for (PostProcessShader pass : ((ShaderEffectAccessor) (Object) this).blur$getPasses()) {
            JsonGlProgram program = ((PostProcessShaderAccessor) (Object) pass).blur$getProgram();

            GlUniform radius = program.getUniformByName("Radius");
            if (radius != null) {
                radius.set(blur.getRadius());
            }

            GlUniform progress = program.getUniformByName("Progress");
            if (progress != null) {
                progress.set(Blur.getProgress());
            }
        }
    }
}
