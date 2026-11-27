package net.client.util;

import net.minecraft.client.gui.DrawableHelper;

public final class HudFill {
   private HudFill() {
   }

   public static void fill(int x1, int y1, int x2, int y2, int argb) {
      DrawableHelper.fill(x1, y1, x2, y2, argb);
   }
}
