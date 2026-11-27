package net.client.util;

import java.util.Random;
import net.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleType;
import net.minecraft.entity.Entity;

/**
 * 自定义粒子：只要勾上「自定义粒子」，每次攻击都会在目标身上直接生成暴击（火花）和
 * 附魔（魔法星）粒子，不再依赖「真的打出暴击」或「武器带锋利/附魔」这两个前提。
 *
 * <p>1.0.1_2 修了两点：</p>
 * <ul>
 *    <li>原来被 {@code hideOwnParticles} 一起挡掉了——「隐藏第一人称粒子」只该隐藏原版自己身上的
 *        粒子，不应该连自定义粒子也吃掉；</li>
 *    <li>触发点从只有 {@code PlayerEntity.attack} 扩到 {@code ClientPlayerInteractionManager.attackEntity}，
 *        客户端两条路都会到，加了 2ms 去重，避免同一次攻击出两份。</li>
 * </ul>
 */
public final class CustomParticles {
   public static boolean bypass = false;
   private static final Random RANDOM = new Random();
   private static Entity lastTarget;
   private static long lastAttackNanos;

   private CustomParticles() {
   }

   public static void onLocalAttack(Entity target) {
      if (target == null || !ClientConfig.customParticles) {
         return;
      }
      // 两条注入点（attack / attackEntity）会连着触发，2ms 内的同一目标只算一次
      long now = System.nanoTime();
      if (target == lastTarget && now - lastAttackNanos < 2000000L) {
         return;
      }
      lastTarget = target;
      lastAttackNanos = now;

      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.particleManager == null) {
         return;
      }
      int crit = Math.round(10.0F * Math.max(0.0F, ClientConfig.critParticleMultiplier));
      int sharp = Math.round(8.0F * Math.max(0.0F, ClientConfig.sharpParticleMultiplier));
      if (crit <= 0 && sharp <= 0) {
         return;
      }
      bypass = true;

      try {
         emit(mc, ParticleType.CRIT, target, crit);
         emit(mc, ParticleType.CRIT_MAGIC, target, sharp);
      } finally {
         bypass = false;
      }
   }

   private static void emit(MinecraftClient mc, ParticleType type, Entity target, int count) {
      if (count > 0) {
         if (count > 60) {
            count = 60;
         }

         for (int i = 0; i < count; i++) {
            double px = target.x + (RANDOM.nextDouble() - 0.5) * ((double)target.width + 0.4);
            double py = target.y + RANDOM.nextDouble() * (double)target.height;
            double pz = target.z + (RANDOM.nextDouble() - 0.5) * ((double)target.width + 0.4);
            double vx = (RANDOM.nextDouble() - 0.5) * 0.8;
            double vy = RANDOM.nextDouble() * 0.6 + 0.2;
            double vz = (RANDOM.nextDouble() - 0.5) * 0.8;
            mc.particleManager.addParticle(type.getId(), px, py, pz, vx, vy, vz, new int[0]);
         }
      }
   }
}
