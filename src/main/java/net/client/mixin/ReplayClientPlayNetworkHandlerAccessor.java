package net.client.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replay sessions install their ClientWorld without going through the vanilla join handler. */
@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public interface ReplayClientPlayNetworkHandlerAccessor {
    @Accessor("world")
    net.minecraft.client.world.ClientWorld client$getWorld();

    @Accessor("world")
    void client$setWorld(net.minecraft.client.world.ClientWorld world);

    @Accessor("playerListEntries")
    java.util.Map<java.util.UUID, net.minecraft.client.network.PlayerListEntry> client$getPlayerListEntries();

    @Accessor("positionLookSetup")
    void client$setPositionLookSetup(boolean setup);
}
