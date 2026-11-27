package net.client.mixin;

import java.net.InetAddress;
import java.net.UnknownHostException;
import net.client.util.FastIpPing;
import net.minecraft.client.network.MultiplayerServerListPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Server list ping path: do not reverse-DNS a literal IP address. */
@Mixin(MultiplayerServerListPinger.class)
public class FastIpPingPingerMixin {
   @Redirect(
           method = {"add"},
           at = @At(
                   value = "INVOKE",
                   target = "Ljava/net/InetAddress;getByName(Ljava/lang/String;)Ljava/net/InetAddress;"
           )
   )
   private static InetAddress client$fastPing(String address) throws UnknownHostException {
      return FastIpPing.getByName(address);
   }
}
