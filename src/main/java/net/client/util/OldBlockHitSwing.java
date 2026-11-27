package net.client.util;

import net.client.ClientConfig;
import net.client.mixin.LivingEntitySwingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;

public final class OldBlockHitSwing {
   private OldBlockHitSwing() {
   }

   public static void keepAlive(MinecraftClient mc) {
      if (mc != null && ClientConfig.blockHitAnim) {
         PlayerEntity player = mc.player;
         if (player != null && mc.options != null) {
            if (player.isUsingItem() && mc.options.useKey.isPressed()
                  && mc.result != null && mc.result.type == BlockHitResult.Type.BLOCK) {
               int fullTicks = ((LivingEntitySwingAccessor)player).client$swingDuration();
               int halfTicks = Math.max(1, fullTicks / 2);
               boolean notStarted = !player.handSwinging || player.handSwingTicks < 0;
               boolean pastHalf = player.handSwingTicks >= halfTicks;
               if (notStarted || pastHalf) {
                  player.handSwingTicks = -1;
                  player.handSwinging = true;
               }
            }
         }
      }
   }
}
