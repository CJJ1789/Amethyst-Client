package net.client.ime;

import net.client.ClientConfig;
import net.client.ui.ClientTheme;
import net.client.util.HudFill;
import net.client.util.ClientLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.opengl.Display;

/**
 * 中文输入法桥（1.0.1_20 重写版）。
 *
 * <p>结构照 IMEInputBackport（MIT）那套：原生后端只负责「组字串 + 候选窗 + 取消」，
 * 语义（按键该不该放行）与绘制（内联组字）都在这一层做。参考项目的原生实现是
 * JNI + Forge/CatFrame 强绑定，1.8.9 Legacy Fabric 与手机端都加载不了，所以代码没复用，
 * 只采用了它的产品形态：内联组字、候选窗跟手、组字期间按键不串、Esc 只取消组字。</p>
 *
 * <p>平台：只有 Windows 有可用后端（IMM32 via JNA），其它平台直接视为不可用，
 * 设置项也标了「仅限电脑端」。</p>
 */
public final class ImeBridge {
   private static final ImeBackend BACKEND = isWindows() ? new Imm32Backend() : new UnsupportedBackend();
   private static final int KEY_ESCAPE = 1;
   private static final int KEY_BACKSPACE = 14;
   private static final int KEY_ENTER = 28;
   private static final int KEY_KP_ENTER = 156;

   private static volatile String composition = "";
   private static boolean warnedUnsupported;

   private ImeBridge() {
   }

   public static boolean isWindows() {
      String os = System.getProperty("os.name", "");
      return os != null && os.toLowerCase().contains("win");
   }

   public static boolean supported() {
      return !(BACKEND instanceof UnsupportedBackend);
   }

   /** 设置里切换开关时调用：下次渲染重新建上下文。 */
   public static void onConfigChanged() {
      composition = "";
      BACKEND.reset();
   }

   public static String composition() {
      return composition;
   }

   public static boolean composing() {
      return !composition.isEmpty();
   }

   /**
    * 文本框每帧渲染后调用：同步组字串、把系统候选窗钉到光标处、把待选字符画在光标位置。
    */
   public static void onFieldRender(TextFieldWidget field) {
      if (field == null) {
         return;
      }
      onFieldRender(field.x, field.y, field.getInnerWidth(), field.getText(),
              field.getCursor(), field.isFocused());
   }

   /**
    * Framework-neutral entry point: MD3 text fields are not vanilla
    * {@link TextFieldWidget}s, so the screen forwards its own caret geometry.
    */
   public static void onFieldRender(int fieldX, int fieldY, int fieldWidth,
                                    String text, int cursor, boolean focused) {
      if (!ClientConfig.imeFix) {
         composition = "";
         return;
      }
      if (!BACKEND.available()) {
         if (!warnedUnsupported) {
            warnedUnsupported = true;
            ClientLog.warn("输入法桥当前平台不可用（仅 Windows 桌面端支持）");
         }
         composition = "";
         return;
      }
      if (!focused) {
         composition = "";
         return;
      }
      String comp = BACKEND.composition();
      composition = comp == null ? "" : comp;
      if (composition.isEmpty()) {
         return;
      }
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null) {
         return;
      }
      int caret = caretX(fieldX, fieldWidth, text, cursor, mc);
      int lineH = mc.textRenderer == null ? 9 : mc.textRenderer.fontHeight;
      BACKEND.setCaret(toPixels(caret), toPixels(fieldY + lineH + 2));
      drawComposition(mc, comp, caret, fieldY);
   }

   /**
    * 组字期间的按键语义：字母不再二次落入输入框，Esc 只取消组字，其余按键原样放行。
    *
    * @return true 表示这次按键已被输入法消费，调用方不要再处理
    */
   public static boolean handleKey(char typedChar, int keyCode) {
      if (!ClientConfig.imeFix || !composing() || !BACKEND.available()) {
         return false;
      }
      if (keyCode == KEY_ESCAPE) {
         BACKEND.cancel();
         composition = "";
         return true;
      }
      if (keyCode == KEY_BACKSPACE || keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER) {
         return true;
      }
      // 可打印字符（含空格/标点）：IME 正在组字，不能让它再进一次输入框
      return typedChar >= ' ' || typedChar > 0;
   }

   private static void drawComposition(MinecraftClient mc, String comp, int caret, int fieldY) {
      TextRenderer tr = mc.textRenderer;
      if (tr == null || comp.isEmpty()) {
         return;
      }
      int y = fieldY + 6;
      int accent = ClientTheme.get().colors.primary;
      tr.draw(comp, caret, y, accent);
      int w = tr.getStringWidth(comp);
      HudFill.fill(caret, y + tr.fontHeight, caret + w, y + tr.fontHeight + 1, accent);
   }

   private static int caretX(int fieldX, int fieldWidth, String text, int cursor, MinecraftClient mc) {
      if (text == null) {
         return fieldX + 4;
      }
      int end = cursor < 0 ? 0 : (cursor > text.length() ? text.length() : cursor);
      TextRenderer tr = mc.textRenderer;
      int width = tr == null ? 0 : tr.getStringWidth(text.substring(0, end));
      int x = fieldX + 4 + width;
      int maxX = fieldX + fieldWidth - 4;
      return x > maxX ? maxX : x;
   }

   /** GUI 逻辑像素 → 窗口客户区像素（系统候选窗用的是后者）。 */
   private static int toPixels(int gui) {
      int guiW = MinecraftClient.getInstance() == null ? 0 : MinecraftClient.getInstance().width;
      int pxW = Display.getWidth();
      if (guiW <= 0 || pxW <= 0) {
         return gui;
      }
      double scale = (double)pxW / (double)guiW;
      return (int)Math.round((double)gui * scale);
   }

   static final class UnsupportedBackend implements ImeBackend {
      public boolean available() {
         return false;
      }

      public String composition() {
         return "";
      }

      public void setCaret(int pixelX, int pixelY) {
      }

      public void cancel() {
      }

      public void reset() {
      }

      public boolean swallowVirtualKey(int vk) {
         return false;
      }
   }
}
