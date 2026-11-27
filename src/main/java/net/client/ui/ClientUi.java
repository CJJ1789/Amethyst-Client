package net.client.ui;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.Identifier;

/** Small drawing / input helpers shared by the rewritten MD3 screens. */
public final class ClientUi {
   private static boolean soundBroken;

   private ClientUi() {
   }

   /**
    * 1.0.2_4：接管原版页面（原版 Options / 资源包 / 多人游戏列表 …）时的毛玻璃底。
    * 这些页面自己没有 MD3 面板，背景是刚画好的全景；这里把整屏再抓一遍做 9 点平均，
    * 铺一层很淡的磨砂玻璃，观感才跟客户端设置页一致。
    */
   public static void glassBackdrop(int w, int h) {
      if (w <= 0 || h <= 0 || !net.client.ClientConfig.uiOverride) {
         return;
      }
      Md3Canvas c = new Md3Canvas();
      c.begin(w, h);
      try {
         ClientGlass.capture(c);
         ClientGlass.drawFull(c, 0.75f,
                 Md3Colors.alpha(0xFFFFFF, ClientTheme.dark() ? 6 : 12), 0xFFFFFF);
      } finally {
         c.end();
      }
   }

   /** Dim or tint the screen behind a client dialog. */
   /** 本帧是否已经铺过全景底（由 panoramaLayer 置位，backdrop 消费）。 */
   private static boolean panoramaPending;

   /**
    * 1.0.2_2：铺一层和标题界面同款的全景动画背景。
    * 必须在该 Screen 的 {@code canvas.begin()} 之前调用：canvas 会把 GL 的投影矩阵换成
    * GUI 正交矩阵并缓存一大堆状态，全景那套 glPerspective + 旋转插在中间会让后续绘制全部丢失。
    */
   /** 主菜单类的页面（模组列表等）共用原版全景；客户端设置页不再使用。 */
   public static void panoramaLayer(int w, int h) {
      if (w <= 0 || h <= 0) {
         return;
      }
      net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
      if (mc == null || mc.world != null) {
         return;
      }
      ClientPanorama.render(mc, w, h);
      panoramaPending = true;
   }

   /**
    * 静态底色：面板同色打底，再压两块很淡的主色 / 次色光晕。
    * 1.0.2_24 恢复：1.0.2_22 曾把它退化成一块纯 surface，用户希望那种「掺了点别的颜色」的
    * 旧底色回来（仍然静态，不是那套旋转全景）。
    */
   public static void backdrop(Md3Canvas c, Md3Theme t, int w, int h, boolean inWorld) {
      if (w <= 0 || h <= 0) {
         return;
      }
      if (panoramaPending) {
         // 1.0.2_2：菜单背景和标题界面共用同一层——都是那套全景动画。
         // 全景本身由 panoramaLayer() 在 canvas.begin() 之前就画好了（那会儿 GL 状态归 MC 管，
          // 混进 canvas 的绘制流程里会把投影矩阵弄乱、整块面板都画不出来），
         // 这里只补一层按主题的压色，保证面板上的文字仍然清楚。
         panoramaPending = false;
         // 标题页不经过这里（TitleScreen 自己画背景），所以这层压暗只作用于「设置页」等
         // 面板类菜单；1.0.2_6 去掉的是主菜单背景上的滤镜，这里保持原样以保证可读性。
         c.fill(0f, 0f, w, h, Md3Colors.alpha(t.colors.surface,
                 ClientTheme.dark() ? 0.42f : 0.24f));
      } else if (inWorld) {
         // 世界内、但不是客户端设置页：背景交给 Blur 的 shader 管道，MD3 层不再额外压暗。
      } else {
         c.fill(0f, 0f, w, h, t.colors.surface);
         c.glow(w * 0.15f - 160f, -150f, 480f, 360f,
                 Md3Colors.alpha(t.colors.primary, ClientTheme.dark() ? 0.16f : 0.09f));
         c.glow(w * 0.82f - 160f, h - 220f, 420f, 340f,
                 Md3Colors.alpha(t.colors.secondary, ClientTheme.dark() ? 0.12f : 0.07f));
      }
   }

   public static void click() {
      if (soundBroken) {
         return;
      }
      try {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null || mc.getSoundManager() == null) {
            return;
         }
         mc.getSoundManager().play(PositionedSoundInstance.master(new Identifier("gui.button.press"), 1.0f));
      } catch (Throwable ignored) {
         soundBroken = true;
      }
   }

   public static boolean hit(float mx, float my, float x, float y, float w, float h) {
      return mx >= x && mx <= x + w && my >= y && my <= y + h;
   }

   /** Ellipsize to a width measured with the MD3 atlas. */
   public static String fit(Md3Canvas c, String text, Md3Type type, float maxWidth) {
      if (text == null || text.isEmpty() || maxWidth <= 0f) {
         return "";
      }
      if (c.textWidth(text, type) <= maxWidth) {
         return text;
      }
      String tail = "...";
      float tailW = c.textWidth(tail, type);
      int end = text.length();
      while (end > 0 && c.textWidth(text.substring(0, end), type) + tailW > maxWidth) {
         end--;
      }
      return text.substring(0, Math.max(0, end)) + tail;
   }

   public static void textFit(Md3Canvas c, String text, float x, float cy, Md3Type type,
                              float maxWidth, int color) {
      Md3Text.leftMiddle(c, fit(c, text, type, maxWidth), x, cy, type, color);
   }

   /** Filled pill used for leading/trailing metadata (not an interactive chip). */
   public static void tag(Md3Canvas c, Md3Theme t, String label, float x, float cy) {
      float tw = c.textWidth(label, Md3Type.LABEL_MEDIUM);
      float w = tw + 16f;
      float h = 22f;
      c.roundRect(x, cy - h / 2f, w, h, 11f, t.colors.surfaceContainerHighest);
      Md3Text.centered(c, label, x + w / 2f, cy, Md3Type.LABEL_MEDIUM, t.colors.onSurfaceVariant);
   }

   public static float tagWidth(Md3Canvas c, String label) {
      return c.textWidth(label, Md3Type.LABEL_MEDIUM) + 16f;
   }

   public static void divider(Md3Canvas c, Md3Theme t, float x, float y, float w) {
      c.fill(x, y, w, 1f, t.colors.outlineVariant);
   }

   /** Section header used inside cards: label + optional trailing count. */
   public static void sectionHeader(Md3Canvas c, Md3Theme t, String title, String trailing,
                                    float x, float y, float w) {
      c.text(title, x, y, Md3Type.TITLE_SMALL, t.colors.onSurfaceVariant);
      if (trailing != null && !trailing.isEmpty()) {
         float tw = c.textWidth(trailing, Md3Type.LABEL_MEDIUM);
         c.text(trailing, x + w - tw, y + 1f, Md3Type.LABEL_MEDIUM, t.colors.primary);
      }
   }
}
