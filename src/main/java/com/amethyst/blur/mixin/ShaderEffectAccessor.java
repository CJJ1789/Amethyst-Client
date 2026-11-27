package com.amethyst.blur.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gl.PostProcessShader;
import net.minecraft.client.gl.ShaderEffect;

@Mixin(ShaderEffect.class)
public interface ShaderEffectAccessor {

    @Accessor("passes")
    List<PostProcessShader> blur$getPasses();
}
