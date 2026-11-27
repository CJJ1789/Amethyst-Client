package net.client.util;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import java.util.List;
import net.client.ClientConfig;
import net.client.mixin.LivingEntityRendererScaleAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BiPedModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

public final class AfterimageRenderer {

   private AfterimageRenderer() {
   }

   public static void renderAll(List<Afterimage> list, float partialTicks) {
      if (list != null && !list.isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null && mc.getCameraEntity() != null) {
            EntityRenderDispatcher dispatcher = mc.getEntityRenderManager();
            if (dispatcher != null) {
               double[] cam = cameraPos(mc, partialTicks);
               float strength = MathHelper.clamp(ClientConfig.afterimageStrength, 0.2F, 2.0F);
               int shells = Math.min(Math.max(ClientConfig.afterimageLayers, 1), 12);
               int rgb = ClientConfig.afterimageColor;
               float red = (float)(rgb >> 16 & 0xFF) / 255.0F;
               float green = (float)(rgb >> 8 & 0xFF) / 255.0F;
               float blue = (float)(rgb & 0xFF) / 255.0F;
               float core = coreOpacity(strength);

               for (Afterimage ghost : list) {
                  float alpha = ghost.getAlpha() * strength;
                  if (!(alpha <= 0.01F)) {
                     LivingEntity entity = ghost.entity;
                     if (entity != null && !entity.removed) {
                        if (!ghost.captured) {
                           ghost.captureFrom(entity);
                        }
                        EntityRenderer renderer = dispatcher.getRenderer(entity);
                        if (renderer instanceof LivingEntityRenderer) {
                           LivingEntityRenderer livingRenderer = (LivingEntityRenderer)renderer;
                           EntityModel model = livingRenderer.getModel();
                           if (model != null) {
                              try {
                                 drawGhost(ghost, entity, model, livingRenderer, cam, partialTicks, alpha, core, shells, red, green, blue);
                              } catch (Exception var20) {
                                 ClientLog.error("单张残影渲染异常", var20);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static float coreOpacity(float strength) {
      return MathHelper.clamp(0.14F + 0.09F * strength, 0.1F, 0.4F);
   }

   private static double[] cameraPos(MinecraftClient mc, float partialTicks) {
      Entity camera = mc.getCameraEntity();
      return camera == null
         ? new double[]{0.0, 0.0, 0.0}
         : new double[]{
            camera.prevX + (camera.x - camera.prevX) * (double)partialTicks,
            camera.prevY + (camera.y - camera.prevY) * (double)partialTicks,
            camera.prevZ + (camera.z - camera.prevZ) * (double)partialTicks
         };
   }

   private static void placeModel(Afterimage ghost, double[] cam, float scale, LivingEntityRenderer renderer, LivingEntity entity, float partialTicks) {
      GlStateManager.translate((float)(-cam[0]), (float)(-cam[1]), (float)(-cam[2]));
      GlStateManager.translate(ghost.x, ghost.y, ghost.z);
      GlStateManager.rotate(180.0F - ghost.bodyYaw, 0.0F, 1.0F, 0.0F);
      GlStateManager.scale(-1.0F, -1.0F, 1.0F);
      ((LivingEntityRendererScaleAccessor)renderer).client$scale(entity, partialTicks);
      GlStateManager.translate(0.0F, -1.5078125F, 0.0F);
      if (scale != 1.0F) {
         GlStateManager.scale(scale, scale, scale);
      }
   }

   private static void drawGhost(
      Afterimage ghost,
      LivingEntity entity,
      EntityModel model,
      LivingEntityRenderer renderer,
      double[] cam,
      float partialTicks,
      float alpha,
      float core,
      int shells,
      float red,
      float green,
      float blue
   ) {
      float headYaw = ghost.headYaw - ghost.bodyYaw;
      float pitch = ghost.pitch;
      float savedSwing = model.handSwingProgress;
      boolean savedRiding = model.riding;
      boolean savedChild = model.child;
      boolean isBiped = model instanceof BiPedModel;
      boolean savedSneaking = false;
      boolean savedAiming = false;
      model.handSwingProgress = ghost.swingProgress;
      model.riding = ghost.riding;
      model.child = ghost.child;
      if (isBiped) {
         BiPedModel biped = (BiPedModel)model;
         savedSneaking = biped.sneaking;
         savedAiming = biped.aiming;
         biped.sneaking = ghost.sneaking;
         biped.aiming = false;
      }

      AfterimageRenderer.GlStateSnapshot state = AfterimageRenderer.GlStateSnapshot.capture();
      GlStateManager.pushMatrix();

      try {
         setupGhostState();
         GlStateManager.pushMatrix();
         placeModel(ghost, cam, 1.0F, renderer, entity, partialTicks);
         GlStateManager.depthMask(true);
         GlStateManager.enableCull();
         GlStateManager.cullFace(1029);
         GlStateManager.color(red, green, blue, alpha * core);
         drawModel(model, entity, ghost, headYaw, pitch);
         GlStateManager.popMatrix();

         for (int i = 0; i < shells; i++) {
            GlStateManager.pushMatrix();
            placeModel(ghost, cam, 1.0F + 0.055F * (float)(i + 1), renderer, entity, partialTicks);
            GlStateManager.depthMask(false);
            GlStateManager.enableCull();
            GlStateManager.cullFace(1028);
            float layerAlpha = alpha * core * (float)Math.pow(0.72F, (double)(i + 1));
            GlStateManager.color(red, green, blue, layerAlpha);
            drawModel(model, entity, ghost, headYaw, pitch);
            GlStateManager.popMatrix();
         }
      } finally {
         GlStateManager.popMatrix();
         state.restore();
         model.handSwingProgress = savedSwing;
         model.riding = savedRiding;
         model.child = savedChild;
         if (isBiped) {
            BiPedModel biped = (BiPedModel)model;
            biped.sneaking = savedSneaking;
            biped.aiming = savedAiming;
         }
      }
   }

   private static void drawModel(EntityModel model, LivingEntity entity, Afterimage ghost, float headYaw, float pitch) {
      model.render(entity, ghost.limbSwing, ghost.limbSwingAmount, ghost.ticksAlive, headYaw, pitch, 0.0625F);
   }

   private static void setupGhostState() {
      GlStateManager.disableTexture();
      GlStateManager.disableLighting();
      GlStateManager.disableAlphaTest();
      GlStateManager.disableColorMaterial();
      GlStateManager.disableColorLogic();
      GlStateManager.enableBlend();
      GlStateManager.blendFunc(770, 771);
      GlStateManager.enableDepthTest();
      GlStateManager.depthFunc(515);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
   }

   private static final class GlStateSnapshot {
      private final boolean texture;
      private final boolean lighting;
      private final boolean alphaTest;
      private final boolean colorMaterial;
      private final boolean blend;
      private final boolean cull;
      private final boolean depthTest;
      private final int depthFunc;
      private final int blendSrc;
      private final int blendDst;
      private final int cullFace;
      private final boolean depthMask;
      private final boolean[] colorMask;

      private GlStateSnapshot(
         boolean texture,
         boolean lighting,
         boolean alphaTest,
         boolean colorMaterial,
         boolean blend,
         boolean cull,
         boolean depthTest,
         int depthFunc,
         int blendSrc,
         int blendDst,
         int cullFace,
         boolean depthMask,
         boolean[] colorMask
      ) {
         this.texture = texture;
         this.lighting = lighting;
         this.alphaTest = alphaTest;
         this.colorMaterial = colorMaterial;
         this.blend = blend;
         this.cull = cull;
         this.depthTest = depthTest;
         this.depthFunc = depthFunc;
         this.blendSrc = blendSrc;
         this.blendDst = blendDst;
         this.cullFace = cullFace;
         this.depthMask = depthMask;
         this.colorMask = colorMask;
      }

      static AfterimageRenderer.GlStateSnapshot capture() {
         ByteBuffer maskBuffer = ByteBuffer.allocateDirect(16);
         GL11.glGetBoolean(3107, maskBuffer);
         boolean[] mask = new boolean[4];

         for (int i = 0; i < 4; i++) {
            mask[i] = maskBuffer.get(i) != 0;
         }

         return new AfterimageRenderer.GlStateSnapshot(
            GL11.glIsEnabled(3553),
            GL11.glIsEnabled(2896),
            GL11.glIsEnabled(3008),
            GL11.glIsEnabled(2903),
            GL11.glIsEnabled(3042),
            GL11.glIsEnabled(2884),
            GL11.glIsEnabled(2929),
            GL11.glGetInteger(2932),
            GL11.glGetInteger(3041),
            GL11.glGetInteger(3040),
            GL11.glGetInteger(2885),
            GL11.glGetBoolean(2930),
            mask
         );
      }

      void restore() {
         GlStateManager.colorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);
         GlStateManager.depthMask(this.depthMask);
         GlStateManager.depthFunc(this.depthFunc);
         GlStateManager.cullFace(this.cullFace);
         GlStateManager.blendFunc(this.blendSrc, this.blendDst);
         if (this.texture) {
            GlStateManager.enableTexture();
         } else {
            GlStateManager.disableTexture();
         }

         if (this.lighting) {
            GlStateManager.enableLighting();
         } else {
            GlStateManager.disableLighting();
         }

         if (this.alphaTest) {
            GlStateManager.enableAlphaTest();
         } else {
            GlStateManager.disableAlphaTest();
         }

         if (this.colorMaterial) {
            GlStateManager.enableColorMaterial();
         } else {
            GlStateManager.disableColorMaterial();
         }

         if (this.blend) {
            GlStateManager.enableBlend();
         } else {
            GlStateManager.disableBlend();
         }

         if (this.cull) {
            GlStateManager.enableCull();
         } else {
            GlStateManager.disableCull();
         }

         if (this.depthTest) {
            GlStateManager.enableDepthTest();
         } else {
            GlStateManager.disableDepthTest();
         }
      }
   }
}
