package net.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {EntityRenderer.class},
   priority = 1100
)
public abstract class EntityRendererMixin<T extends Entity> {
   @Shadow
   protected EntityRenderDispatcher dispatcher;

   @Inject(
      method = {"renderLabelIfPresent"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$nameTag(T entity, String text, double x, double y, double z, int maxDistance, CallbackInfo ci) {
      if (text != null && !text.isEmpty()) {
         double dist = entity.squaredDistanceTo(this.dispatcher.field_11098);
         if (!(dist > (double)(maxDistance * maxDistance))) {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            if (tr != null) {
               int bg = ClientConfig.nameBgColor;
               int fg = ClientConfig.nameTextColor;
               float scale = 0.026666673F;
               GlStateManager.pushMatrix();
               GlStateManager.translate((float)x, (float)(y + (double)entity.height + 0.5), (float)z);
               GL11.glNormal3f(0.0F, 1.0F, 0.0F);
               GlStateManager.rotate(-this.dispatcher.yaw, 0.0F, 1.0F, 0.0F);
               GlStateManager.rotate(this.dispatcher.pitch, 1.0F, 0.0F, 0.0F);
               GlStateManager.scale(-scale, -scale, scale);
               GlStateManager.disableLighting();
               GlStateManager.depthMask(false);
               GlStateManager.disableDepthTest();
               GlStateManager.enableBlend();
               GlStateManager.blendFuncSeparate(770, 771, 1, 0);
               GlStateManager.disableTexture();
               int half = tr.getStringWidth(text) / 2;
               Tessellator tess = Tessellator.getInstance();
               BufferBuilder buf = tess.getBuffer();
               buf.begin(7, VertexFormats.POSITION_COLOR);
               float a = (float)(bg >>> 24 & 0xFF) / 255.0F;
               float r = (float)(bg >> 16 & 0xFF) / 255.0F;
               float g = (float)(bg >> 8 & 0xFF) / 255.0F;
               float b = (float)(bg & 0xFF) / 255.0F;
               buf.vertex((double)(-half) - 2.0, 9.0, 0.0).color(r, g, b, a).next();
               buf.vertex((double)half + 2.0, 9.0, 0.0).color(r, g, b, a).next();
               buf.vertex((double)half + 2.0, -2.0, 0.0).color(r, g, b, a).next();
               buf.vertex((double)(-half) - 2.0, -2.0, 0.0).color(r, g, b, a).next();
               tess.draw();
               GlStateManager.enableTexture();
               GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
               tr.draw(text, -half, 0, fg);
               GlStateManager.enableDepthTest();
               GlStateManager.depthMask(true);
               GlStateManager.enableLighting();
               GlStateManager.disableBlend();
               GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
               GlStateManager.popMatrix();
               ci.cancel();
            }
         }
      }
   }
}
