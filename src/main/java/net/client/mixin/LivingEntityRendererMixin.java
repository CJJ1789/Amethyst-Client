package net.client.mixin;

import java.nio.FloatBuffer;
import net.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {LivingEntityRenderer.class},
   priority = 1200
)
public class LivingEntityRendererMixin {
   private static final String LC_HURT = "method_10252(Lnet/minecraft/entity/LivingEntity;FZ)Z";

   @Inject(
      method = {"hasLabel"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void client$selfLabel(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
      if (ClientConfig.selfNameTag && entity == MinecraftClient.getInstance().player) {
         cir.setReturnValue(true);
      }
   }

   @Redirect(
      method = {"method_10252(Lnet/minecraft/entity/LivingEntity;FZ)Z"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;",
         ordinal = 0
      )
   )
   private FloatBuffer client$hurtR(FloatBuffer buffer, float value) {
      return buffer.put(client$hurtChannel(0, value));
   }

   @Redirect(
      method = {"method_10252(Lnet/minecraft/entity/LivingEntity;FZ)Z"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;",
         ordinal = 1
      )
   )
   private FloatBuffer client$hurtG(FloatBuffer buffer, float value) {
      return buffer.put(client$hurtChannel(1, value));
   }

   @Redirect(
      method = {"method_10252(Lnet/minecraft/entity/LivingEntity;FZ)Z"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;",
         ordinal = 2
      )
   )
   private FloatBuffer client$hurtB(FloatBuffer buffer, float value) {
      return buffer.put(client$hurtChannel(2, value));
   }

   @Redirect(
      method = {"method_10252(Lnet/minecraft/entity/LivingEntity;FZ)Z"},
      at = @At(
         value = "INVOKE",
         target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;",
         ordinal = 3
      )
   )
   private FloatBuffer client$hurtA(FloatBuffer buffer, float value) {
      return buffer.put(client$hurtChannel(3, value));
   }

   private static float client$hurtChannel(int channel, float vanilla) {
      if (!ClientConfig.hurtColorOn) {
         return vanilla;
      } else {
         int argb = ClientConfig.hurtColor;
         int shift = channel == 0 ? 16 : (channel == 1 ? 8 : (channel == 2 ? 0 : 24));
         int v = argb >> shift & 0xFF;
         return channel == 3 ? (float)v / 255.0F : (float)v / 255.0F;
      }
   }
}
