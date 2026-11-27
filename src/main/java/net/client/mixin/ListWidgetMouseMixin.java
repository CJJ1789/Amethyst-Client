package net.client.mixin;

import net.client.util.ServerListDrag;
import net.minecraft.client.gui.widget.ListWidget;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ListWidget.handleMouse() is called every frame and implements its own
 * click-drag scrolling from the raw LWJGL mouse state.  That bypasses
 * Screen.mouseDragged, so cancelling the screen event alone was not enough.
 * While a server row is being reordered, block that path completely unless
 * the event is a real wheel event, in which case the wheel is allowed to scroll.
 */
@Mixin(ListWidget.class)
public class ListWidgetMouseMixin {
   @Inject(method = {"handleMouse"}, at = {@At("HEAD")}, cancellable = true)
   private void client$serverDragWheelOnly(CallbackInfo ci) {
      if (!ServerListDrag.isActive()) {
         return;
      }
      if (Mouse.getEventDWheel() == 0) {
         ci.cancel();
         return;
      }
      // Wheel is allowed, but the same frame's click-drag path must not also
      // move the list under the dragged row.
      ((ListWidget)(Object)this).setDragging(false);
   }
}
