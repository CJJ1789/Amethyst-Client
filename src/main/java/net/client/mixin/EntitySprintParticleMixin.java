package net.client.mixin;

import net.client.ClientConfig;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.0.1_9「脚下粒子」：原版 Entity.attemptSprintingParticles 在疾跑、且脚踩在非空气方块上时，
 * 每 tick 在脚下撒一层 BLOCK_CRACK 碎屑——就是跑起来时脚边那一串小颗粒。
 * 这里在入口直接取消，玩家和兔子之类的疾跑粒子一起关掉。
 */
@Mixin({Entity.class})
public class EntitySprintParticleMixin {
   @Inject(
      method = {"attemptSprintingParticles"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$skipSprintParticles(CallbackInfo ci) {
      if (ClientConfig.hideSprintParticles) {
         ci.cancel();
      }
   }
}
