package net.client.util;

import net.client.ClientConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.lwjgl.opengl.GL11;

public final class BlockHighlightRenderer {
   private BlockHighlightRenderer() {
   }

   public static void render(BlockHitResult hit, float tickDelta) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         if (hit != null && hit.getBlockPos() != null) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();
            String name = block.getTranslationKey();
            if (name == null || !name.endsWith("air") && !name.endsWith("air.void")) {
               Entity cam = mc.getCameraEntity();
               if (cam == null) {
                  cam = mc.player;
               }

               double cx = cam.prevX + (cam.x - cam.prevX) * (double)tickDelta;
               double cy = cam.prevY + (cam.y - cam.prevY) * (double)tickDelta;
               double cz = cam.prevZ + (cam.z - cam.prevZ) * (double)tickDelta;

               Box sel;
               try {
                  sel = block.getSelectionBox(mc.world, pos);
               } catch (Exception var38) {
                  sel = new Box(
                     (double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)(pos.getX() + 1), (double)(pos.getY() + 1), (double)(pos.getZ() + 1)
                  );
               }

               if (sel != null) {
                  double minX = sel.minX - cx - 1.0E-4;
                  double minY = sel.minY - cy - 1.0E-4;
                  double minZ = sel.minZ - cz - 1.0E-4;
                  double maxX = sel.maxX - cx + 1.0E-4;
                  double maxY = sel.maxY - cy + 1.0E-4;
                  double maxZ = sel.maxZ - cz + 1.0E-4;
                  int fill = ClientConfig.highlightFill;
                  int line = ClientConfig.highlightOutline;
                  float fr = red(fill);
                  float fg = green(fill);
                  float fb = blue(fill);
                  float fa = alpha(fill);
                  float lr = red(line);
                  float lg = green(line);
                  float lb = blue(line);
                  float la = alpha(line);
                  Direction side = hit.direction;
                  GL11.glPushAttrib(1048575);
                  GL11.glEnable(3042);
                  GL11.glBlendFunc(770, 771);
                  GL11.glDisable(3553);
                  GL11.glDisable(2884);
                  GL11.glEnable(2929);
                  GL11.glDepthMask(false);
                  GL11.glDisable(2896);
                  if (fa > 0.0F) {
                     renderVolumeFill(minX, minY, minZ, maxX, maxY, maxZ, fr, fg, fb, fa);
                  }

                  if (la > 0.0F) {
                     renderOutline(minX, minY, minZ, maxX, maxY, maxZ, lr, lg, lb, la);
                  }

                  GL11.glDepthMask(true);
                  GL11.glPopAttrib();
                  if (side == null) {
                     side = Direction.DOWN;
                  }
               }
            }
         }
      }
   }

   private static void renderVolumeFill(double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
      Tessellator tess = Tessellator.getInstance();
      BufferBuilder buf = tess.getBuffer();
      int ri = (int)(r * 255.0F);
      int gi = (int)(g * 255.0F);
      int bi = (int)(b * 255.0F);
      int ai = (int)(a * 255.0F);
      buf.begin(7, VertexFormats.POSITION_COLOR);
      quad(buf, ri, gi, bi, ai, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
      quad(buf, ri, gi, bi, ai, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
      quad(buf, ri, gi, bi, ai, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
      quad(buf, ri, gi, bi, ai, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
      quad(buf, ri, gi, bi, ai, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
      quad(buf, ri, gi, bi, ai, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
      tess.draw();
   }

   private static void quad(
      BufferBuilder buf,
      int r,
      int g,
      int b,
      int a,
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      double x3,
      double y3,
      double z3,
      double x4,
      double y4,
      double z4
   ) {
      buf.vertex(x1, y1, z1).color(r, g, b, a).next();
      buf.vertex(x2, y2, z2).color(r, g, b, a).next();
      buf.vertex(x3, y3, z3).color(r, g, b, a).next();
      buf.vertex(x4, y4, z4).color(r, g, b, a).next();
   }

   private static void renderOutline(double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
      GL11.glEnable(2848);
      GL11.glHint(3154, 4354);
      GL11.glLineWidth(2.0F);
      Tessellator tess = Tessellator.getInstance();
      BufferBuilder buf = tess.getBuffer();
      int ri = (int)(r * 255.0F);
      int gi = (int)(g * 255.0F);
      int bi = (int)(b * 255.0F);
      int ai = (int)(a * 255.0F);
      buf.begin(1, VertexFormats.POSITION_COLOR);
      edge(buf, ri, gi, bi, ai, x1, y1, z1, x2, y1, z1);
      edge(buf, ri, gi, bi, ai, x2, y1, z1, x2, y1, z2);
      edge(buf, ri, gi, bi, ai, x2, y1, z2, x1, y1, z2);
      edge(buf, ri, gi, bi, ai, x1, y1, z2, x1, y1, z1);
      edge(buf, ri, gi, bi, ai, x1, y2, z1, x2, y2, z1);
      edge(buf, ri, gi, bi, ai, x2, y2, z1, x2, y2, z2);
      edge(buf, ri, gi, bi, ai, x2, y2, z2, x1, y2, z2);
      edge(buf, ri, gi, bi, ai, x1, y2, z2, x1, y2, z1);
      edge(buf, ri, gi, bi, ai, x1, y1, z1, x1, y2, z1);
      edge(buf, ri, gi, bi, ai, x2, y1, z1, x2, y2, z1);
      edge(buf, ri, gi, bi, ai, x2, y1, z2, x2, y2, z2);
      edge(buf, ri, gi, bi, ai, x1, y1, z2, x1, y2, z2);
      tess.draw();
      GL11.glLineWidth(1.0F);
      GL11.glDisable(2848);
   }

   private static void edge(BufferBuilder buf, int r, int g, int b, int a, double x1, double y1, double z1, double x2, double y2, double z2) {
      buf.vertex(x1, y1, z1).color(r, g, b, a).next();
      buf.vertex(x2, y2, z2).color(r, g, b, a).next();
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
