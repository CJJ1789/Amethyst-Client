// Upstream: FastPacks / McPacks 1.0 (Forge 1.8.9), MIT
//   https://modrinth.com/mod/fastpacks (author: McPacks Team; Modrinth owner: Dulandor)
// Ported and modified for Legacy Fabric by this project; upstream MIT notice retained
// (see licenses/FASTPACKS-MIT.txt). The port was never released standalone.

package com.mcpacks.mixin;

import com.mcpacks.gui.GuiFastResourcePacks;
import java.lang.reflect.Field;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ResourcePackScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MinecraftClient.class})
public class MinecraftClientMixin {
   @Unique
   private static Field mcpacks$parentScreenField;
   @Unique
   private static boolean mcpacks$parentFieldSearched;

   @Inject(
      method = {"setScreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void mcpacks$replaceResourcePackScreen(Screen screen, CallbackInfo ci) {
      if (screen != null && screen instanceof ResourcePackScreen && !(screen instanceof GuiFastResourcePacks)) {
         Screen parent = mcpacks$findParentScreen((ResourcePackScreen)screen);
         if (parent == null) {
            parent = MinecraftClient.getInstance().currentScreen;
         }

         ci.cancel();
         MinecraftClient.getInstance().setScreen(new GuiFastResourcePacks(parent));
      }
   }

   @Unique
   private static Screen mcpacks$findParentScreen(ResourcePackScreen vanillaScreen) {
      if (!mcpacks$parentFieldSearched) {
         mcpacks$parentFieldSearched = true;

         try {
            for (Field field : ResourcePackScreen.class.getDeclaredFields()) {
               if (Screen.class.isAssignableFrom(field.getType())) {
                  field.setAccessible(true);
                  mcpacks$parentScreenField = field;
                  break;
               }
            }
         } catch (Exception var6) {
            mcpacks$parentScreenField = null;
         }
      }

      if (mcpacks$parentScreenField == null) {
         return null;
      } else {
         try {
            return (Screen)mcpacks$parentScreenField.get(vanillaScreen);
         } catch (Exception var5) {
            return null;
         }
      }
   }
}
