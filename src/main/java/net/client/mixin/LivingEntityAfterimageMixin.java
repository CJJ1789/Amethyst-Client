package net.client.mixin;

import net.client.ClientConfig;
import net.client.util.AfterimageHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public class LivingEntityAfterimageMixin {
   @Inject(
      method = {"handleStatus"},
      at = {@At("HEAD")}
   )
   private void client$onStatus(byte status, CallbackInfo ci) {
      if (status == 2 && ClientConfig.afterimage) {
         LivingEntity self = (LivingEntity)(Object)this;
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc != null) {
            Entity camera = mc.getCameraEntity();
            if (self != camera || ClientConfig.afterimageSelf) {
               AfterimageHandler.addAfterimage(self);
            }
         }
      }
   }
}
