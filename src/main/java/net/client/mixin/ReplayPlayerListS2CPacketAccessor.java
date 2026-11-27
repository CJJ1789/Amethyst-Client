package net.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;

@Mixin(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.class)
public interface ReplayPlayerListS2CPacketAccessor {
    @Accessor("action")
    void client$setAction(net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action action);

    @Accessor("entries")
    List<PlayerListS2CPacket.Entry> client$getEntries();
}
