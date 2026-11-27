package net.client.mixin;

import java.util.Random;
import net.client.ClientConfig;
import net.client.util.CustomParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleType;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ParticleManager.class})
public class ParticleManagerMixin {
   @Unique
   private static boolean client$spawningExtra = false;
   @Unique
   private static float client$critCarry = 0.0F;
   @Unique
   private static float client$sharpCarry = 0.0F;
   @Unique
   private static final Random CLIENT_RANDOM = new Random();

   /** 1.0.1_14「隐藏挖掘粒子」：方块被破坏时的碎屑，以及挖掘进度中的裂纹粒子。 */
   @Inject(
      method = {"addBlockBreakParticles"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$skipBlockBreak(BlockPos pos, BlockState state, CallbackInfo ci) {
      if (ClientConfig.hideMiningParticles) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"addBlockBreakingParticles"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$skipBlockBreaking(BlockPos pos, Direction direction, CallbackInfo ci) {
      if (ClientConfig.hideMiningParticles) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"addEmitter"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$skipOwnEmitter(Entity entity, ParticleType type, CallbackInfo ci) {
      if (ClientConfig.hideOwnParticles) {
         if (type == ParticleType.CRIT || type == ParticleType.CRIT_MAGIC) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null && entity == client.player) {
               ci.cancel();
            }
         }
      }
   }

   @Inject(
      method = {"addParticle(IDDDDDD[I)Lnet/minecraft/client/particle/Particle;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$skipOwnPotion(
      int id, double x, double y, double z, double vx, double vy, double vz, int[] args, CallbackInfoReturnable<Particle> cir
   ) {
      if (ClientConfig.hideOwnParticles) {
         if (id == ParticleType.MOB_SPELL.getId() || id == ParticleType.AMBIENT_MOB_SPELL.getId()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
               double dx = x - client.player.x;
               double dy = y - client.player.y;
               double dz = z - client.player.z;
               double reach = (double)client.player.width / 2.0 + 0.6;
               if (dx * dx + dz * dz <= reach * reach && dy >= -0.6 && dy <= (double)client.player.height + 0.6) {
                  cir.setReturnValue(null);
               }
            }
         }
      }
   }

   @Inject(
      method = {"addParticle(IDDDDDD[I)Lnet/minecraft/client/particle/Particle;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$multiplyParticles(
      int id, double x, double y, double z, double vx, double vy, double vz, int[] args, CallbackInfoReturnable<Particle> cir
   ) {
      if (!client$spawningExtra && !CustomParticles.bypass && ClientConfig.customParticles) {
         boolean crit = id == ParticleType.CRIT.getId();
         boolean sharp = id == ParticleType.CRIT_MAGIC.getId();
         if (crit || sharp) {
            float mult = crit ? ClientConfig.critParticleMultiplier : ClientConfig.sharpParticleMultiplier;
            if (mult != 1.0F) {
               if (mult < 1.0F) {
                  if (CLIENT_RANDOM.nextFloat() > mult) {
                     cir.setReturnValue(null);
                  }
               } else {
                  float carry = crit ? client$critCarry : client$sharpCarry;
                  carry += mult - 1.0F;
                  int extra = (int)carry;
                  carry -= (float)extra;
                  if (crit) {
                     client$critCarry = carry;
                  } else {
                     client$sharpCarry = carry;
                  }

                  if (extra > 6) {
                     extra = 6;
                  }

                  if (extra > 0) {
                     client$spawningExtra = true;

                     try {
                        ParticleManager self = (ParticleManager)(Object)this;

                        for (int i = 0; i < extra; i++) {
                           double jx = x + (CLIENT_RANDOM.nextDouble() - 0.5) * 0.6;
                           double jy = y + (CLIENT_RANDOM.nextDouble() - 0.5) * 0.4;
                           double jz = z + (CLIENT_RANDOM.nextDouble() - 0.5) * 0.6;
                           self.addParticle(id, jx, jy, jz, vx, vy, vz, args);
                        }
                     } finally {
                        client$spawningExtra = false;
                     }
                  }
               }
            }
         }
      }
   }
}
