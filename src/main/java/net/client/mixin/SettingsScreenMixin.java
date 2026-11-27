package net.client.mixin;

import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.screen.SettingsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.GameOptions.Option;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SettingsScreen.class})
public class SettingsScreenMixin {
   private static final int[] KEEP_ORDER = new int[]{110, 8675309, 106, 101, 100, 102, 103, 105};

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void client$trim(CallbackInfo ci) {
      SettingsScreen self = (SettingsScreen)(Object)this;
      List<ButtonWidget> buttons = ((ScreenAccessor)self).client$buttons();
      if (buttons != null) {
         int realmsId = Option.REALMS_NOTIFICATIONS.getOrdinal();
         Iterator<ButtonWidget> it = buttons.iterator();

         while (it.hasNext()) {
            ButtonWidget button = it.next();
            String label = button.message == null ? "" : button.message;
            if (button.id == 107 || button.id == 104 || button.id == realmsId
                  || label.contains("Realms") || label.contains("领域")) {
               it.remove();
            }
         }

         int startX = self.width / 2 - 155;
         int startY = self.height / 6 + 48 - 6;

         for (int i = 0; i < KEEP_ORDER.length; i++) {
            ButtonWidget found = null;

            for (ButtonWidget button : buttons) {
               if (button.id == KEEP_ORDER[i]) {
                  found = button;
                  break;
               }
            }

            if (found != null) {
               found.x = startX + i % 2 * 160;
               found.y = startY + 24 * (i / 2);
            }
         }

         for (ButtonWidget buttonx : buttons) {
            if (buttonx.id == 200) {
               buttonx.y = startY + 24 * ((KEEP_ORDER.length + 1) / 2) + 6;
            }
         }
      }
   }
}
