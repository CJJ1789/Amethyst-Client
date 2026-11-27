package net.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ClientChunkProvider;

@Mixin(net.minecraft.world.chunk.ClientChunkProvider.class)
public interface ReplayClientChunkProviderAccessor {
    @Accessor("chunks")
    List<net.minecraft.world.chunk.Chunk> client$getChunks();
}
