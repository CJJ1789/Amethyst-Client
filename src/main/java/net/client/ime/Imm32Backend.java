package net.client.ime;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.util.Arrays;
import java.util.List;
import net.client.util.ClientLog;

/**
 * Windows 后端的原生桥（IMM32，经 JNA 调用）。
 *
 * <p>1.8.9 是 LWJGL2，窗口没有绑定输入法上下文（HIMC），系统输入法要么不出字、要么候选窗
 * 飘到屏幕左上角。这里把 IMM32 上下文重新关联到游戏窗口，读回组字串，并把候选窗钉到光标处。</p>
 *
 * <p>设计参考 IMEInputBackport（MIT）的「一个后端只负责原生组字/候选，语义与绘制交给上层」，
 * 但那份实现是 JNI + Forge/CatFrame 的，平台与加载器都对不上，代码没有复用，只借了结构。</p>
 */
final class Imm32Backend implements ImeBackend {
   private static final int GCS_COMPSTR = 0x0008;
   private static final int CFS_POINT = 0x0002;
   private static final int NI_COMPOSITIONSTR = 0x0015;
   private static final int CPS_CANCEL = 0x0004;

   private static final int VK_ESCAPE = 0x1B;
   private static final int VK_RETURN = 0x0D;
   private static final int VK_BACK = 0x08;

   private static boolean loaded;
   private static boolean loadFailed;
   private static Pointer hwnd;
   private static Pointer himc;
   private static String lastWarn = "";
   /**
    * 1.0.2_18：组字缓冲复用。原来每帧 new 一个 JNA Memory，而 JNA 的 Memory 只能靠
    * finalize 释放 —— 长句子打字时会持续累积 native 内存。这里只在不够大时才重建。
    */
   private static Memory compositionBuffer;

   private static void warnOnce(String msg) {
      if (!lastWarn.equals(msg)) {
         lastWarn = msg;
         ClientLog.warn(msg);
      }
   }

   public boolean available() {
      return init();
   }

   private boolean init() {
      if (loadFailed) {
         return false;
      }
      if (!loaded) {
         loaded = true;
         try {
            Class.forName("com.sun.jna.Native");
         } catch (Throwable t) {
            warnOnce("未找到 JNA，输入法桥不可用: " + t);
            loadFailed = true;
            return false;
         }
      }
      if (hwnd == null) {
         hwnd = findWindow();
         if (hwnd == null) {
            return false;
         }
      }
      if (himc == null) {
         try {
            Pointer ctx = Imm.INSTANCE.ImmGetContext(hwnd);
            if (ctx == null) {
               ctx = Imm.INSTANCE.ImmCreateContext();
               if (ctx == null) {
                  warnOnce("ImmCreateContext 返回空，跳过输入法桥");
                  return false;
               }
               Imm.INSTANCE.ImmAssociateContext(hwnd, ctx);
               ClientLog.info("输入法：已重新关联 IMM32 上下文");
            }
            himc = ctx;
         } catch (Throwable t) {
            warnOnce("获取输入法上下文失败: " + t);
            loadFailed = true;
            return false;
         }
      }
      return himc != null;
   }

   private static Pointer findWindow() {
      try {
         Pointer h = User32.INSTANCE.FindWindow("LWJGL", null);
         if (h != null) {
            return h;
         }
         return User32.INSTANCE.GetForegroundWindow();
      } catch (Throwable t) {
         warnOnce("查找游戏窗口失败: " + t);
         return null;
      }
   }

   public String composition() {
      if (!init()) {
         return "";
      }
      try {
         int bytes = Imm.INSTANCE.ImmGetCompositionStringW(himc, GCS_COMPSTR, null, 0);
         if (bytes <= 0) {
            return "";
         }
         if (compositionBuffer == null || compositionBuffer.size() < bytes) {
            compositionBuffer = new Memory(bytes);
         }
         Memory mem = compositionBuffer;
         int got = Imm.INSTANCE.ImmGetCompositionStringW(himc, GCS_COMPSTR, mem, bytes);
         if (got <= 0) {
            return "";
         }
         int chars = got / 2;
         StringBuilder sb = new StringBuilder(chars);
         for (int i = 0; i < chars; i++) {
            sb.append(mem.getChar((long)i * 2L));
         }
         return sb.toString();
      } catch (Throwable t) {
         warnOnce("读取组字串失败: " + t);
         return "";
      }
   }

   public void setCaret(int pixelX, int pixelY) {
      if (!init()) {
         return;
      }
      try {
         COMPOSITIONFORM form = new COMPOSITIONFORM();
         form.dwStyle = CFS_POINT;
         form.ptCurrentPos.x = pixelX;
         form.ptCurrentPos.y = pixelY;
         form.write();
         Imm.INSTANCE.ImmSetCompositionWindow(himc, form);
      } catch (Throwable t) {
         warnOnce("候选窗定位失败: " + t);
      }
   }

   /** 组字中按键交给输入法后，输入法要跟着吃到这次击键；这里只负责取消/确认两类动作。 */
   public void cancel() {
      if (!init()) {
         return;
      }
      try {
         Imm.INSTANCE.ImmNotifyIME(himc, NI_COMPOSITIONSTR, CPS_CANCEL, 0);
      } catch (Throwable t) {
         warnOnce("取消组字失败: " + t);
      }
   }

   public void reset() {
      himc = null;
   }

   /** 组字期间需要吞掉的物理键（其余按键原样放行）。 */
   public boolean swallowVirtualKey(int vk) {
      if (vk == VK_ESCAPE || vk == VK_RETURN || vk == VK_BACK) {
         return true;
      }
      // 字母 / 数字 / 标点 / 空格：IME 已经在处理，不能让输入框再吃一遍
      return vk >= 0x20 && vk <= 0x5A || vk >= 0x60 && vk <= 0x6F || vk >= 0xBA && vk <= 0xDF;
   }

   // ---- JNA / IMM32 ---- //

   interface Imm extends StdCallLibrary {
      Imm INSTANCE = (Imm)Native.load("imm32", Imm.class, W32APIOptions.DEFAULT_OPTIONS);

      Pointer ImmGetContext(Pointer hwnd);

      boolean ImmReleaseContext(Pointer hwnd, Pointer himc);

      Pointer ImmCreateContext();

      boolean ImmDestroyContext(Pointer himc);

      Pointer ImmAssociateContext(Pointer hwnd, Pointer himc);

      int ImmGetCompositionStringW(Pointer himc, int index, Pointer buf, int len);

      boolean ImmSetCompositionWindow(Pointer himc, COMPOSITIONFORM form);

      boolean ImmNotifyIME(Pointer himc, int action, int index, int value);
   }

   interface User32 extends StdCallLibrary {
      User32 INSTANCE = (User32)Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

      Pointer FindWindow(String className, String windowName);

      Pointer GetForegroundWindow();
   }

   public static class POINT extends Structure {
      public int x;
      public int y;

      @Override
      protected List<String> getFieldOrder() {
         return Arrays.asList("x", "y");
      }
   }

   public static class RECT extends Structure {
      public int left;
      public int top;
      public int right;
      public int bottom;

      @Override
      protected List<String> getFieldOrder() {
         return Arrays.asList("left", "top", "right", "bottom");
      }
   }

   public static class COMPOSITIONFORM extends Structure {
      public int dwStyle;
      public POINT ptCurrentPos = new POINT();
      public RECT rcArea = new RECT();

      @Override
      protected List<String> getFieldOrder() {
         return Arrays.asList("dwStyle", "ptCurrentPos", "rcArea");
      }
   }
}
