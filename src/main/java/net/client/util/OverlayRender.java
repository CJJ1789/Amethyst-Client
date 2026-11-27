package net.client.util;

import com.mojang.blaze3d.platform.GlStateManager;
import net.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.lwjgl.opengl.GL11;

public final class OverlayRender {
   private OverlayRender() {
   }

   public static void render(float tickDelta) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         if (ClientConfig.selfNameTag && mc.options.perspective != 0) {
         }
      }
   }

   private static void renderSelfName(MinecraftClient mc, float tickDelta) {
      ClientPlayerEntity player = mc.player;
      TextRenderer textRenderer = mc.textRenderer;
      String text = player.getName().asFormattedString();
      if (text != null && !text.isEmpty()) {
         double x = player.prevX + (player.x - player.prevX) * (double)tickDelta;
         double y = player.prevY + (player.y - player.prevY) * (double)tickDelta + (double)player.height + 0.5;
         double z = player.prevZ + (player.z - player.prevZ) * (double)tickDelta;
         int bg = ClientConfig.nameBgColor;
         int fg = ClientConfig.nameTextColor;
         GlStateManager.pushMatrix();
         GlStateManager.translate((float)x, (float)y, (float)z);
         GL11.glNormal3f(0.0F, 1.0F, 0.0F);
         GlStateManager.rotate(-player.yaw, 0.0F, 1.0F, 0.0F);
         GlStateManager.rotate(player.pitch, 1.0F, 0.0F, 0.0F);
         GlStateManager.scale(-0.02666667F, -0.02666667F, 0.02666667F);
         GlStateManager.disableLighting();
         GlStateManager.depthMask(false);
         GlStateManager.enableBlend();
         GlStateManager.blendFuncSeparate(770, 771, 1, 0);
         int halfWidth = textRenderer.getStringWidth(text) / 2;
         GlStateManager.disableTexture();
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         GL11.glColor4f(red(bg), green(bg), blue(bg), alpha(bg));
         GL11.glBegin(7);
         GL11.glVertex3d((double)(-halfWidth) - 1.0, -1.0, 0.0);
         GL11.glVertex3d((double)(-halfWidth) - 1.0, 8.0, 0.0);
         GL11.glVertex3d((double)halfWidth + 1.0, 8.0, 0.0);
         GL11.glVertex3d((double)halfWidth + 1.0, -1.0, 0.0);
         GL11.glEnd();
         GlStateManager.enableTexture();
         textRenderer.draw(text, -halfWidth, 0, fg);
         GlStateManager.disableBlend();
         GlStateManager.enableLighting();
         GlStateManager.depthMask(true);
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         GlStateManager.popMatrix();
      }
   }

   private static void boxEdges(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      line(minX, minY, minZ, maxX, minY, minZ);
      line(maxX, minY, minZ, maxX, minY, maxZ);
      line(maxX, minY, maxZ, minX, minY, maxZ);
      line(minX, minY, maxZ, minX, minY, minZ);
      line(minX, maxY, minZ, maxX, maxY, minZ);
      line(maxX, maxY, minZ, maxX, maxY, maxZ);
      line(maxX, maxY, maxZ, minX, maxY, maxZ);
      line(minX, maxY, maxZ, minX, maxY, minZ);
      line(minX, minY, minZ, minX, maxY, minZ);
      line(maxX, minY, minZ, maxX, maxY, minZ);
      line(maxX, minY, maxZ, maxX, maxY, maxZ);
      line(minX, minY, maxZ, minX, maxY, maxZ);
   }

   private static void line(double x1, double y1, double z1, double x2, double y2, double z2) {
      GL11.glVertex3d(x1, y1, z1);
      GL11.glVertex3d(x2, y2, z2);
   }

   private static float red(int argb) {
      return (float)(argb >> 16 & 0xFF) / 255.0F;
   }

   private static float green(int argb) {
      return (float)(argb >> 8 & 0xFF) / 255.0F;
   }

   private static float blue(int argb) {
      return (float)(argb & 0xFF) / 255.0F;
   }

   private static float alpha(int argb) {
      return (float)(argb >> 24 & 0xFF) / 255.0F;
   }
}
