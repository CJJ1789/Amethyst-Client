package net.client.mixin;

import net.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.0.1_17：线性鼠标灵敏度（设置项原名「原始鼠标输入」，1.0.1_20 起更名）。
 *
 * 1.8.9 的 LWJGL2 抓鼠标本来就是直读 OS 增量（Mouse.getDX/getDY），没有独立可关的 native raw 通道，
 * 所以这里把它做成「关掉原版灵敏度三次曲线」：开启时鼠标增量走线性灵敏度（g = f * 2），
 * 不再被 f^3 * 8 这条曲线放大/压缩，手感更接近 1:1 跟手；关闭时与原版逐位一致。
 */
@Mixin({MouseInput.class})
public class MouseInputMixin {
   @Shadow
   public int x;
   @Shadow
   public int y;

   @Inject(method = {"updateMouse"}, at = {@At("RETURN")})
   private void client$rawMouse(CallbackInfo ci) {
      if (!ClientConfig.rawInput) {
         return;
      }
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.options == null) {
         return;
      }
      float f = mc.options.sensitivity * 0.6F + 0.2F;
      if (f <= 0.0F) {
         return;
      }
      // 原版曲线 g = f^3 * 8；原始模式目标 g = f * 2；比值 = 1 / (4 * f^2)
      float ratio = 1.0F / (4.0F * f * f);
      this.x = Math.round(this.x * ratio);
      this.y = Math.round(this.y * ratio);
   }
}
