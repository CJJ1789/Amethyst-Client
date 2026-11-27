package net.client.mixin;

import net.client.util.ServerListDrag;
import net.client.util.ServerListPingFix;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin {
   @Shadow
   private MultiplayerServerListWidget serverListWidget;
   @Shadow
   private ServerList serverList;

   @Inject(method = {"init"}, at = {@At("TAIL")})
   private void client$initServerFixes(CallbackInfo ci) {
      ServerListDrag.reset();
      if (this.serverList != null) {
         ServerListPingFix.configure(this.serverList.size());
      }
   }

   @Inject(method = {"refresh"}, at = {@At("TAIL")})
   private void client$refreshServerFixes(CallbackInfo ci) {
      if (this.serverList != null) {
         ServerListPingFix.configure(this.serverList.size());
      }
   }

   @Inject(method = {"render"}, at = {@At("TAIL")})
   private void client$dragGhost(int mouseX, int mouseY, float delta, CallbackInfo ci) {
      ServerListDrag.renderGhost(mouseX, mouseY);
   }

   @Inject(method = {"mouseClicked"}, at = {@At("HEAD")})
   private void client$serverDragPress(int mouseX, int mouseY, int button, CallbackInfo ci) {
      ServerListDrag.onPress((MultiplayerScreen)(Object)this, this.serverListWidget,
              this.serverList, mouseX, mouseY, button);
   }

   @Inject(method = {"mouseReleased"}, at = {@At("HEAD")}, cancellable = true)
   private void client$serverDragRelease(int mouseX, int mouseY, int button,
                                                CallbackInfo ci) {
      if (ServerListDrag.onRelease((MultiplayerScreen)(Object)this, mouseX, mouseY, button)) {
         ci.cancel();
      }
   }
}
