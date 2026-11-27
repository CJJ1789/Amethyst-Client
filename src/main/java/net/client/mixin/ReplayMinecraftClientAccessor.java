package net.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.ClientTickTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.MinecraftClient.class)
public interface ReplayMinecraftClientAccessor {
    @Accessor("ticker")
    net.minecraft.client.render.ClientTickTracker client$getTicker();
}
