package net.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;

@Mixin(
   value = {HeldItemRenderer.class},
   priority = 1200
)
public abstract class HeldItemRendererMixin {
   @Shadow
   @Final
   private MinecraftClient client;
   @Shadow
   private ItemStack mainHand;
   @Shadow
   private float equipProgress;
   @Shadow
   private float lastEquipProgress;
   @Unique
   private boolean client$bobPushed;
   @Unique
   private boolean client$fireScaled;
   @Unique
   private boolean client$handOffsetPushed;
   @Unique
   private static float client$frameDelta = 0.0F;

   @Shadow
   private void applyEquipAndSwingOffset(float equipProgress, float swingProgress) {
   }

   @Shadow
   private void translateSwingProgress(float swingProgress) {
   }

   @Shadow
   private void rotate(float pitch, float yaw) {
   }

   @Shadow
   private void applyPlayerLighting(AbstractClientPlayerEntity player) {
   }

   @Shadow
   private void applyPlayerRotation(ClientPlayerEntity player, float tickDelta) {
   }

   @Shadow
   private void renderMap(AbstractClientPlayerEntity player, float pitch, float equipProgress, float swingProgress) {
   }

   @Shadow
   private void applyEatOrDrinkTransformation(AbstractClientPlayerEntity player, float tickDelta) {
   }

   @Shadow
   private void applySwordBlockTransformation() {
   }

   @Shadow
   private void applyBowTransformation(float tickDelta, AbstractClientPlayerEntity player) {
   }

   @Shadow
   public abstract void renderItem(LivingEntity var1, ItemStack var2, Mode var3);

   @Shadow
   private void renderArm(AbstractClientPlayerEntity player, float equipProgress, float swingProgress) {
   }

   @Inject(
      method = {"renderFireOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$fireHeightIn(float tickDelta, CallbackInfo ci) {
      int pct = ClientConfig.fireHeight;
      if (pct < 100) {
         if (pct <= 0) {
            ci.cancel();
         } else {
            float s = Math.max(0.05F, (float)pct / 100.0F);
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, -0.5F * (1.0F - s), 0.0F);
            GlStateManager.scale(1.0F, s, 1.0F);
            this.client$fireScaled = true;
         }
      }
   }

   @Inject(
      method = {"renderFireOverlay"},
      at = {@At("RETURN")}
   )
   private void client$fireHeightOut(float tickDelta, CallbackInfo ci) {
      if (this.client$fireScaled) {
         this.client$fireScaled = false;
         GlStateManager.popMatrix();
      }
   }

   @Inject(
      method = {"renderArmHoldingItem"},
      at = {@At("HEAD")}
   )
   private void client$handOffsetPush(float tickDelta, CallbackInfo ci) {
      GlStateManager.pushMatrix();
      float forward = ClientConfig.handForward;
      float right = ClientConfig.handRight;
      float up = ClientConfig.handUp;
      if (forward != 0.0F || right != 0.0F || up != 0.0F) {
         GlStateManager.translate(right, up, -forward);
      }

      this.client$handOffsetPushed = true;
   }

   @Inject(
      method = {"renderArmHoldingItem"},
      at = {@At("RETURN")}
   )
   private void client$handOffsetPop(float tickDelta, CallbackInfo ci) {
      if (this.client$handOffsetPushed) {
         GlStateManager.popMatrix();
         this.client$handOffsetPushed = false;
      }
   }

   @Inject(
      method = {"renderArmHoldingItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/platform/GlStateManager;pushMatrix()V",
         shift = At.Shift.AFTER
      )}
   )
   private void client$leftHandMirror(float tickDelta, CallbackInfo ci) {
      if (ClientConfig.leftHanded) {
         // 原版方法内部已经 push 了一层矩阵；在这里镜像，物品/手臂的位置变换
         // 仍处于该层内，不会像 HEAD 镜像那样把整只手推到镜头外。
         GlStateManager.scale(-1.0F, 1.0F, 1.0F);
         GlStateManager.translate(-0.1F, 0.0F, 0.0F);
         // 负缩放会翻转三角形绕序；不切到 CW，空手手臂会被背面剔除掉。
         GL11.glFrontFace(GL11.GL_CW);
      }
   }

   @Inject(
      method = {"renderArmHoldingItem"},
      at = {@At("RETURN")}
   )
   private void client$leftHandRestoreCull(float tickDelta, CallbackInfo ci) {
      if (ClientConfig.leftHanded) {
         GL11.glFrontFace(GL11.GL_CCW);
      }
   }

   @Inject(
      method = {"renderArmHoldingItem"},
      at = {@At("HEAD")}
   )
   private void client$bobPush(float tickDelta, CallbackInfo ci) {
      if (ClientConfig.minimalBobbing && this.client.options.bobView) {
         GlStateManager.pushMatrix();
         this.client$applyHandBob(tickDelta);
         this.client$bobPushed = true;
      }
   }

   @Inject(
      method = {"renderArmHoldingItem"},
      at = {@At("RETURN")}
   )
   private void client$bobPop(float tickDelta, CallbackInfo ci) {
      if (this.client$bobPushed) {
         GlStateManager.popMatrix();
         this.client$bobPushed = false;
      }
   }

   @Inject(
      method = {"renderArmHoldingItem"},
      at = {@At("HEAD")}
   )
   private void client$captureFrameDelta(float tickDelta, CallbackInfo ci) {
      client$frameDelta = tickDelta;
   }

   @ModifyArg(
      method = {"renderArmHoldingItem"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipAndSwingOffset(FF)V"
      ),
      slice = @Slice(
         from = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEatOrDrinkTransformation(Lnet/minecraft/client/network/AbstractClientPlayerEntity;F)V"
         ),
         to = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyBowTransformation(FLnet/minecraft/client/network/AbstractClientPlayerEntity;)V"
         )
      ),
      index = 1
   )
   private float client$oldBlockHitSwing(float swingProgress) {
      return ClientConfig.blockHitAnim && this.client != null && this.client.player != null
         ? this.client.player.getHandSwingProgress(client$frameDelta)
         : swingProgress;
   }

   private void client$applyHandBob(float tickDelta) {
      PlayerEntity player = this.client.player;
      float delta = player.horizontalSpeed - player.prevHorizontalSpeed;
      float walked = -(player.horizontalSpeed + delta * tickDelta);
      float stride = player.prevStrideDistance + (player.strideDistance - player.prevStrideDistance) * tickDelta;
      float tilt = player.field_6752 + (player.field_6753 - player.field_6752) * tickDelta;
      GlStateManager.translate(MathHelper.sin(walked * (float) Math.PI) * stride * 0.5F, -Math.abs(MathHelper.cos(walked * (float) Math.PI) * stride), 0.0F);
      GlStateManager.rotate(MathHelper.sin(walked * (float) Math.PI) * stride * 3.0F, 0.0F, 0.0F, 1.0F);
      GlStateManager.rotate(Math.abs(MathHelper.cos(walked * (float) Math.PI - 0.2F) * stride) * 5.0F, 1.0F, 0.0F, 0.0F);
      GlStateManager.rotate(tilt, 1.0F, 0.0F, 0.0F);
   }
}
