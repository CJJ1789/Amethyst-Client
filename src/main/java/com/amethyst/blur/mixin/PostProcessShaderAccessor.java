package com.amethyst.blur.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gl.JsonGlProgram;
import net.minecraft.client.gl.PostProcessShader;

@Mixin(PostProcessShader.class)
public interface PostProcessShaderAccessor {

    @Accessor("program")
    JsonGlProgram blur$getProgram();
}
