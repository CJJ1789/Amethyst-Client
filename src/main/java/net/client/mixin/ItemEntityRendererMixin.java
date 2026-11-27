package net.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.client.ClientConfig;
import net.client.util.ClientLog;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemEntityRenderer.class})
public abstract class ItemEntityRendererMixin {
   private static ItemEntity client$current;
   private static int client$renders;
   private static int client$spinCalls;
   private static int client$posCalls;
   private static boolean client$loggedHead;
   private static boolean client$loggedSpin;
   private static boolean client$loggedMiss;

   @Inject(
      method = {"render(Lnet/minecraft/entity/ItemEntity;DDDFF)V"},
      at = {@At("HEAD")}
   )
   private void client$remember(ItemEntity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
      client$current = entity;
      client$renders++;
      if (!client$loggedHead) {
         client$loggedHead = true;
         ClientLog.info("[物理掉落物] render 钩子已挂上，realDrops=" + ClientConfig.realDrops + " dropNoSpin=" + ClientConfig.dropRest);
      }

      if (!client$loggedMiss && client$renders > 40 && client$spinCalls == 0) {
         client$loggedMiss = true;
         ClientLog.warn("[物理掉落物] 自转钩子未命中（渲染 " + client$renders + " 次仍为 0），原版自转没有被拦截");
      }
   }

   @Redirect(
      method = {"method_10221"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/platform/GlStateManager;rotate(FFFF)V"
      ),
      require = 1
   )
   private static void client$cancelVanillaSpin(float angle, float x, float y, float z) {
      ItemEntity entity = client$current;
      client$spinCalls++;
      if (!client$loggedSpin) {
         client$loggedSpin = true;
         ClientLog.info("[物理掉落物] 自转钩子命中，onGround=" + (entity != null && entity.onGround));
      }

      if (client$spinCalls % 200 == 0) {
         ClientLog.info("[物理掉落物] 自转钩子累计命中 " + client$spinCalls + " 次，当前 onGround=" + (entity != null && entity.onGround));
      }

      if (!ClientConfig.realDrops || !ClientConfig.dropRest || entity == null || !entity.onGround) {
         GlStateManager.rotate(angle, x, y, z);
      }
   }

   @Redirect(
      method = {"method_10221"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/platform/GlStateManager;translate(FFF)V",
         ordinal = 0
      ),
      require = 1
   )
   private static void client$cancelHover(float x, float y, float z) {
      ItemEntity entity = client$current;
      if (ClientConfig.realDrops && ClientConfig.dropRest && entity != null && entity.onGround) {
         float hover = MathHelper.sin((float)entity.getAge() / 10.0F + entity.hoverHeight) * 0.1F + 0.1F;
         GlStateManager.translate(x, y - hover, z);
      } else {
         GlStateManager.translate(x, y, z);
      }
   }

   @Inject(
      method = {"render(Lnet/minecraft/entity/ItemEntity;DDDFF)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/ItemRenderer;renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/BakedModel;)V",
         ordinal = 0
      )}
   )
   private void client$poseBlockModel(ItemEntity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
      this.client$applyPose(entity, tickDelta);
   }

   @Inject(
      method = {"render(Lnet/minecraft/entity/ItemEntity;DDDFF)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/ItemRenderer;renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/BakedModel;)V",
         ordinal = 1
      )}
   )
   private void client$poseItemModel(ItemEntity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
      this.client$applyPose(entity, tickDelta);
   }

   private void client$applyPose(ItemEntity entity, float tickDelta) {
      client$posCalls++;
      if (ClientConfig.realDrops) {
         double vx = entity.velocityX;
         double vz = entity.velocityZ;
         float seconds = ((float)entity.getAge() + tickDelta) / 20.0F;
         if (entity.onGround) {
            int id = entity.getEntityId();
            float restYaw = (float)(id * 47 % 24) * 15.0F;
            GlStateManager.translate(0.0F, -0.12F, 0.0F);
            GlStateManager.rotate(restYaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
         } else {
            float travelYaw;
            if (vx * vx + vz * vz > 1.0E-4) {
               travelYaw = (float)Math.toDegrees(Math.atan2(-vx, vz));
            } else {
               travelYaw = (float)(entity.getEntityId() * 37 % 360);
            }

            float speed = (float)Math.sqrt(vx * vx + vz * vz);
            float tumbleSpeed = 230.0F * (0.6F + Math.min(speed, 0.9F));
            GlStateManager.rotate(travelYaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-tumbleSpeed * seconds, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(40.0F * seconds, 0.0F, 0.0F, 1.0F);
         }
      }
   }
}
