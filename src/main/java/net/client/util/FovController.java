package net.client.util;

import net.client.ClientConfig;
import net.minecraft.block.material.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Items;

/**
 * 1.0.1_13：客户端 FOV 控制器（Badlion 式）。
 *
 * 模型与 FOVEffectsPlus（MIT）的思路一致：不是一个倍率，而是「每个条件一个目标视野」，
 * 命中条件时把视野过渡到那个值；总开关关闭时整个类不参与，原版与 OptiFine 的 FOV 逻辑逐位照旧。
 *
 * 目标值选择：以「默认视野」为基准，所有同时成立的条件里取偏离基准最大的那个目标值
 * （例如疾跑 80° 与迟缓 65° 同时成立且基准 70°，取 80°；水下 60° 同时成立时仍取 80°），
 * 避免多个条件叠乘造成视野被连环压缩/放大。
 *
 * 平滑：按真实时间为权重的指数收敛（约 0.12 秒半衰），不依赖渲染帧率；
 * 关闭「平滑过渡」时直接取目标值。
 */
public final class FovController {

   private static final float SMOOTH_HALF_LIFE = 0.12F;
   private static float current = Float.NaN;
   private static long lastNanos;

   private FovController() {
   }

   /** 总开关关闭时调用，清掉平滑状态，下次打开从目标值直接起步。 */
   public static void reset() {
      current = Float.NaN;
      lastNanos = 0L;
   }

   /**
    * 算出当前应当使用的 FOV（含平滑与上下限夹紧）。
    */
   public static float apply(MinecraftClient client, float tickDelta) {
      float target = target(client);
      long now = System.nanoTime();
      float dt = lastNanos == 0L ? 0.0F : (float)(now - lastNanos) / 1.0e9F;
      lastNanos = now;
      if (dt < 0.0F) {
         dt = 0.0F;
      }

      if (Float.isNaN(current) || !ClientConfig.fovSmooth || dt > 0.5F) {
         current = target;
      } else {
         float alpha = 1.0F - (float)Math.pow(0.5, dt / SMOOTH_HALF_LIFE);
         current += (target - current) * alpha;
         if (Math.abs(target - current) < 0.01F) {
            current = target;
         }
      }

      float fov = current;
      if (fov < ClientConfig.fovMin) {
         fov = ClientConfig.fovMin;
      } else if (fov > ClientConfig.fovMax) {
         fov = ClientConfig.fovMax;
      }

      return fov;
   }

   private static float target(MinecraftClient client) {
      float base = ClientConfig.fovBase * ClientConfig.fovGlobal;
      float best = base;
      float bestDelta = 0.0F;

      Entity camera = client.getCameraEntity();
      AbstractClientPlayerEntity player = camera instanceof AbstractClientPlayerEntity
         ? (AbstractClientPlayerEntity)camera
         : client.player;

      if (player != null) {
         if (ClientConfig.fovSprintOn && player.isSprinting()) {
            best = pick(base, best, bestDelta, ClientConfig.fovSprint);
            bestDelta = Math.abs(best - base);
         }

         if (ClientConfig.fovSpeedOn) {
            StatusEffectInstance speed = player.getEffectInstance(StatusEffect.SPEED);
            if (speed != null) {
               best = pick(base, best, bestDelta, ClientConfig.fovSpeed);
               bestDelta = Math.abs(best - base);
            }
         }

         if (ClientConfig.fovSlowOn) {
            StatusEffectInstance slow = player.getEffectInstance(StatusEffect.SLOWNESS);
            if (slow != null) {
               best = pick(base, best, bestDelta, ClientConfig.fovSlow);
               bestDelta = Math.abs(best - base);
            }
         }

         if (ClientConfig.fovFlyOn && player.abilities.flying) {
            best = pick(base, best, bestDelta, ClientConfig.fovFly);
            bestDelta = Math.abs(best - base);
         }

         if (ClientConfig.fovAimOn && player.isUsingItem() && player.getUsedItem().getItem() == Items.BOW) {
            best = pick(base, best, bestDelta, ClientConfig.fovAim);
            bestDelta = Math.abs(best - base);
         }
      }

      if (ClientConfig.fovWaterOn && client.world != null && camera != null) {
         if (Camera.getSubmergedBlock(client.world, camera, 0.0F).getMaterial() == Material.WATER) {
            best = pick(base, best, bestDelta, ClientConfig.fovWater);
         }
      }

      return best;
   }

   private static float pick(float base, float best, float bestDelta, float candidate) {
      return Math.abs(candidate - base) > bestDelta ? candidate : best;
   }
}
