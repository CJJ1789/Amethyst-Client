package net.client.mixin;

import net.client.replay.ReplayClient;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.network.ClientConnection.class)
public class ReplayClientConnectionMixin {
    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V",
            at = @At("HEAD")
    )
    private void client$onPacket(ChannelHandlerContext context, net.minecraft.network.Packet<?> packet, CallbackInfo ci) {
        if (ReplayClient.getRecordingManager() != null) {
            ReplayClient.getRecordingManager().onPacket((net.minecraft.network.ClientConnection) (Object) this, packet);
        }
    }
}
