package net.client.mixin;

import net.client.util.CustomParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自定义粒子的主触发点：客户端每次对实体发起攻击都会经过这里（1.8.9 里 attackEntity 内部
 * 再调 player.attack）。和 PlayerAttackParticleMixin 互为兜底，CustomParticles 内部有去重。
 */
@Mixin({ClientPlayerInteractionManager.class})
public class AttackEntityParticleMixin {
   @Inject(
      method = {"attackEntity"},
      at = {@At("RETURN")}
   )
   private void client$attackEntityParticles(PlayerEntity player, Entity target, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && player != null && player == mc.player) {
         CustomParticles.onLocalAttack(target);
      }
   }
}
