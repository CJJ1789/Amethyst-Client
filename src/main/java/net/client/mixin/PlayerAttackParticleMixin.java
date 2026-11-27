package net.client.mixin;

import net.client.util.CustomParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerEntity.class})
public class PlayerAttackParticleMixin {
   @Inject(
      method = {"attack"},
      at = {@At("RETURN")}
   )
   private void client$attackParticles(Entity target, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.player != null) {
         if ((Object)this == mc.player) {
            CustomParticles.onLocalAttack(target);
         }
      }
   }
}
