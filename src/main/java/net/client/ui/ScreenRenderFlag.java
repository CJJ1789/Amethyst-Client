package net.client.ui;

/**
 * 标记「当前是否正在渲染某个 Screen（菜单壳）」。
 *
 * 浅色主题会把原版白色/浅灰字改成 onSurface 深色，这个映射只应该作用于菜单里的原版文字。
 * HUD、聊天栏、世界里的名字牌/告示牌同样走 TextRenderer，但它们底色是游戏画面，
 * 改成深色就会看不见，所以那类绘制不能进这个区间。
 */
public final class ScreenRenderFlag {

   private static int depth;

   private ScreenRenderFlag() {
   }

   public static void begin() {
      depth++;
   }

   public static void end() {
      if (depth > 0) {
         depth--;
      }
   }

   public static boolean active() {
      return depth > 0;
   }
}
