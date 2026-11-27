package com.amethyst.blur.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePack;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("resourcePacks")
    List<ResourcePack> blur$getResourcePacks();
}
