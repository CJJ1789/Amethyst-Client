package net.client.mixin;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Type;
import net.client.ClientConfig;
import java.nio.FloatBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * “现代字体”开关：接管原版 TextRenderer 的所有文字，包括菜单、HUD、聊天、
 * 物品数量、实体名牌和 F3；使用 9px 常规字重，视觉尺寸与原版 9px 字一致。
 * § 颜色码按段解析，不再把带颜色码的聊天/名牌整串退回原版。
 */
@Mixin(TextRenderer.class)
public class TextRendererMixin {
   @Inject(method = "draw(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), cancellable = true)
   private void client$modernFont(String text, float x, float y, int color,
                                        boolean shadow, CallbackInfoReturnable<Integer> cir) {
      client$render(text, x, y, color, cir);
   }

   @Inject(method = "draw(Ljava/lang/String;III)I", at = @At("HEAD"), cancellable = true)
   private void client$modernFontInt(String text, int x, int y, int color,
                                           CallbackInfoReturnable<Integer> cir) {
      client$render(text, (float) x, (float) y, color, cir);
   }

   private void client$render(String text, float x, float y, int color,
                                     CallbackInfoReturnable<Integer> cir) {
      if (!ClientConfig.modernFont || text == null || text.isEmpty()) {
         return;
      }
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return;
      }

      int width = client.currentScreen != null ? client.currentScreen.width : client.width;
      int height = client.currentScreen != null ? client.currentScreen.height : client.height;

      // 原版计分板文字用 0x20FFFFFF（alpha 仅 0x20）。现代字体图集的抗锯齿
      // 像素本身还有 alpha，两者相乘会把白字再稀释成半透明灰。只针对这种
      // 低 alpha 的纯白文字抬到不透明；彩色/正常 HUD 文字不受影响。
      if (ClientConfig.modernFont && (color & 0x00FFFFFF) == 0x00FFFFFF
            && ((color >>> 24) & 0xFF) > 0 && ((color >>> 24) & 0xFF) < 0x40) {
         color |= 0xFF000000;
      }
      int base = (color & 0xFF000000) == 0 ? (0xFF000000 | color) : color;
      Md3Canvas canvas = new Md3Canvas();
      // Preserve the caller's matrices for HUD text, chat, world text and the
      // 3D sign model in SignEditScreen.  Plain screen text still resets to a
      // clean ortho pass so depth-enabled GUI states cannot hide it.  Resetting
      // HUD/world matrices was what made chat disappear and sign text vanish.
      boolean preserve = !client$isGuiProjection()
              || client.currentScreen == null
              || client.currentScreen instanceof ChatScreen
              || client$hasTransformedModelView();
      if (preserve) {
         canvas.beginPreserve(width, height);
      } else {
         canvas.begin(width, height);
      }
      // 世界空间的名牌/告示牌文字带有缩放和旋转，禁用 GUI 像素吸附；
      // 聊天栏、F3 和普通界面仍保持像素对齐。
      canvas.pixelSnap = !(preserve && client$hasTransformedModelView());
      // 原版 draw 的 y 是从字形顶部起算；9px 图集自带约 1.5px 的顶部留白，
      // 不补偿会让按钮/聊天/F3 整体看起来向下沉，这里统一上移 1.5px。
      float glyphTop = y - 1.5f;
      boolean worldText = preserve && client$hasTransformedModelView();
      client$drawFormatted(canvas, text, x, glyphTop, base, worldText);
      if (preserve) {
         canvas.endPreserve();
      } else {
         canvas.end();
      }
      cir.setReturnValue(0);
   }

   private void client$drawFormatted(Md3Canvas canvas, String text, float x, float y,
                                            int base, boolean worldText) {
      Md3Type type = worldText ? Md3Type.WORLD_TEXT : Md3Type.VANILLA_TEXT;
      int current = base;
      float pen = x;
      float top = y;
      for (int i = 0; i < text.length(); i++) {
         char ch = text.charAt(i);
         if (ch == '\n') {
            pen = x;
            top += type.lineHeight;
            continue;
         }
         if (ch == '\r') {
            continue;
         }
         if (ch == '\u00a7' && i + 1 < text.length()) {
            char code = Character.toLowerCase(text.charAt(++i));
            if (code == 'r') {
               current = base;
            } else {
               int rgb = client$legacyColor(code);
               if (rgb != -1) {
                  current = (base & 0xFF000000) | rgb;
               }
            }
            continue;
         }
         pen += canvas.text(String.valueOf(ch), pen, top, type, current);
      }
   }

   private static int client$legacyColor(char code) {
      switch (code) {
         case '0': return 0x000000;
         case '1': return 0x0000AA;
         case '2': return 0x00AA00;
         case '3': return 0x00AAAA;
         case '4': return 0xAA0000;
         case '5': return 0xAA00AA;
         case '6': return 0xFFAA00;
         case '7': return 0xAAAAAA;
         case '8': return 0x555555;
         case '9': return 0x5555FF;
         case 'a': return 0x55FF55;
         case 'b': return 0x55FFFF;
         case 'c': return 0xFF5555;
         case 'd': return 0xFF55FF;
         case 'e': return 0xFFFF55;
         case 'f': return 0xFFFFFF;
         default: return -1;
      }
   }



   private static boolean client$isGuiProjection() {
      FloatBuffer projection = BufferUtils.createFloatBuffer(16);
      GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
      return Math.abs(projection.get(0)) < 0.02f && Math.abs(projection.get(5)) < 0.02f;
   }

   private static boolean client$hasTransformedModelView() {
      FloatBuffer model = BufferUtils.createFloatBuffer(16);
      GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model);
      // SignEditScreen draws the sign model with a GUI scale/rotation and then
      // the block renderer applies its own 0.015625 * 0.666 scale, so the net
      // scale is close to 1; detect rotation/translation instead.  Ordinary
      // screen text has an identity model-view here.
      return Math.abs(model.get(0) - 1f) > 0.04f
              || Math.abs(model.get(5) - 1f) > 0.04f
              || Math.abs(model.get(1)) > 0.04f
              || Math.abs(model.get(4)) > 0.04f
              || Math.abs(model.get(12)) > 0.05f
              || Math.abs(model.get(13)) > 0.05f
              || Math.abs(model.get(14)) > 0.05f;
   }
}
