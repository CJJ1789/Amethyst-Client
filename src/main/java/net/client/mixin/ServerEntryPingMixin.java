package net.client.mixin;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import net.client.util.ServerListPingFix;
import net.minecraft.client.gui.widget.ServerEntry;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replace ServerEntry's fixed five-thread ping submission with the bounded scheduler. */
@Mixin(ServerEntry.class)
public class ServerEntryPingMixin {
   @Shadow
   private ServerInfo serverInfo;

   @Redirect(
           method = {"render"},
           at = @At(
                   value = "INVOKE",
                   target = "Ljava/util/concurrent/ThreadPoolExecutor;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"
           )
   )
   private Future<?> client$submitPing(ThreadPoolExecutor vanillaPool, Runnable task) {
      return ServerListPingFix.submit(vanillaPool, task, this.serverInfo);
   }
}
