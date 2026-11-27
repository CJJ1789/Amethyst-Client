package net.client.ui;

import net.client.ui.md3.theme.Md3ColorScheme;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ClientConfig;

/** Single source of truth for the Material 3 theme used by every Client screen. */
public final class ClientTheme {

   private static Md3Theme cached;
   private static int cachedColor = Integer.MIN_VALUE;
   private static boolean cachedLight;

   /* 1.0.2_2：明暗切换 / 换主题色时做一段颜色过渡，不再一帧之内硬切。 */
   private static final float TRANSITION_MS = 220f;
   private static Md3ColorScheme fromScheme;
   private static Md3ColorScheme toScheme;
   private static long animStart;
   private static boolean animating;

   private ClientTheme() {
   }

   public static Md3Theme get() {
      boolean light = ClientConfig.lightTheme;
      int color = ClientConfig.themeColor;
      long now = System.nanoTime();

      if (animating) {
         float p = (now - animStart) / 1_000_000f / TRANSITION_MS;
         if (p >= 1f) {
            animating = false;
            cached = new Md3Theme(toScheme, !light);
         } else {
            cached = new Md3Theme(lerp(fromScheme, toScheme, ease(p)), !light);
         }
         return cached;
      }

      if (cached != null && cachedColor == color && cachedLight == light) {
         return cached;
      }

      Md3ColorScheme next = buildScheme(light, color);
      if (cached == null) {
         cached = new Md3Theme(next, !light);
      } else {
         // 从当前真正显示的颜色开始，而不是从上一套的终点开始，连续切换也不会跳。
         fromScheme = cached.colors;
         toScheme = next;
         animStart = now;
         animating = true;
         cached = new Md3Theme(fromScheme, !light);
      }
      cachedColor = color;
      cachedLight = light;
      return cached;
   }

   /** 过渡曲线：起步快、收尾慢，观感比线性柔和。 */
   private static float ease(float t) {
      float inv = 1f - t;
      return 1f - inv * inv * inv;
   }

   private static Md3ColorScheme buildScheme(boolean light, int color) {
      return build(light, color).colors;
   }

   public static boolean dark() {
      return !ClientConfig.lightTheme;
   }

   private static Md3Theme build(boolean light, int color) {
      Md3ColorScheme scheme = light ? Md3ColorScheme.light() : Md3ColorScheme.dark();
      if ((color & 0x00FFFFFF) != 0x006750A4) {
         scheme.primary = color;
         scheme.onPrimary = contrast(color);
         scheme.primaryContainer = Md3Colors.composite(
                 Md3Colors.alpha(color, light ? 0.18f : 0.30f), scheme.surfaceContainerHigh);
         scheme.onPrimaryContainer = contrast(scheme.primaryContainer);
         scheme.secondary = Md3Colors.composite(
                 Md3Colors.alpha(color, light ? 0.38f : 0.32f), scheme.surface);
         scheme.onSecondary = contrast(scheme.secondary);
         scheme.secondaryContainer = Md3Colors.composite(
                 Md3Colors.alpha(color, light ? 0.14f : 0.20f), scheme.surfaceContainerHighest);
         scheme.onSecondaryContainer = contrast(scheme.secondaryContainer);
         scheme.inversePrimary = Md3Colors.composite(
                 Md3Colors.alpha(color, 0.45f), scheme.inverseSurface);
      }
      return new Md3Theme(scheme, !light);
   }

   /** WCAG luminance pick: black ink on light fills, white ink on dark fills. */
   private static int contrast(int background) {
      int c = background | 0xFF000000;
      double r = ((c >> 16) & 0xFF) / 255.0;
      double g = ((c >> 8) & 0xFF) / 255.0;
      double b = (c & 0xFF) / 255.0;
      double lum = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
      return lum > 0.55 ? 0xFF111111 : 0xFFFFFFFF;
   }

   private static double linear(double v) {
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
   }

   /** 逐通道线性插值（含 alpha）。 */
   private static Md3ColorScheme lerp(Md3ColorScheme a, Md3ColorScheme b, float t) {
      Md3ColorScheme s = new Md3ColorScheme();
      s.primary = mix(a.primary, b.primary, t);
      s.onPrimary = mix(a.onPrimary, b.onPrimary, t);
      s.primaryContainer = mix(a.primaryContainer, b.primaryContainer, t);
      s.onPrimaryContainer = mix(a.onPrimaryContainer, b.onPrimaryContainer, t);
      s.secondary = mix(a.secondary, b.secondary, t);
      s.onSecondary = mix(a.onSecondary, b.onSecondary, t);
      s.secondaryContainer = mix(a.secondaryContainer, b.secondaryContainer, t);
      s.onSecondaryContainer = mix(a.onSecondaryContainer, b.onSecondaryContainer, t);
      s.tertiary = mix(a.tertiary, b.tertiary, t);
      s.onTertiary = mix(a.onTertiary, b.onTertiary, t);
      s.tertiaryContainer = mix(a.tertiaryContainer, b.tertiaryContainer, t);
      s.onTertiaryContainer = mix(a.onTertiaryContainer, b.onTertiaryContainer, t);
      s.error = mix(a.error, b.error, t);
      s.onError = mix(a.onError, b.onError, t);
      s.errorContainer = mix(a.errorContainer, b.errorContainer, t);
      s.onErrorContainer = mix(a.onErrorContainer, b.onErrorContainer, t);
      s.background = mix(a.background, b.background, t);
      s.onBackground = mix(a.onBackground, b.onBackground, t);
      s.surface = mix(a.surface, b.surface, t);
      s.onSurface = mix(a.onSurface, b.onSurface, t);
      s.surfaceVariant = mix(a.surfaceVariant, b.surfaceVariant, t);
      s.onSurfaceVariant = mix(a.onSurfaceVariant, b.onSurfaceVariant, t);
      s.surfaceDim = mix(a.surfaceDim, b.surfaceDim, t);
      s.surfaceBright = mix(a.surfaceBright, b.surfaceBright, t);
      s.surfaceContainerLowest = mix(a.surfaceContainerLowest, b.surfaceContainerLowest, t);
      s.surfaceContainerLow = mix(a.surfaceContainerLow, b.surfaceContainerLow, t);
      s.surfaceContainer = mix(a.surfaceContainer, b.surfaceContainer, t);
      s.surfaceContainerHigh = mix(a.surfaceContainerHigh, b.surfaceContainerHigh, t);
      s.surfaceContainerHighest = mix(a.surfaceContainerHighest, b.surfaceContainerHighest, t);
      s.outline = mix(a.outline, b.outline, t);
      s.outlineVariant = mix(a.outlineVariant, b.outlineVariant, t);
      s.inverseSurface = mix(a.inverseSurface, b.inverseSurface, t);
      s.inverseOnSurface = mix(a.inverseOnSurface, b.inverseOnSurface, t);
      s.inversePrimary = mix(a.inversePrimary, b.inversePrimary, t);
      s.shadow = mix(a.shadow, b.shadow, t);
      s.scrim = mix(a.scrim, b.scrim, t);
      return s;
   }

   private static int mix(int x, int y, float t) {
      int xa = (x >>> 24) & 0xFF;
      int xr = (x >> 16) & 0xFF;
      int xg = (x >> 8) & 0xFF;
      int xb = x & 0xFF;
      int ya = (y >>> 24) & 0xFF;
      int yr = (y >> 16) & 0xFF;
      int yg = (y >> 8) & 0xFF;
      int yb = y & 0xFF;
      int ca = Math.round(xa + (ya - xa) * t);
      int cr = Math.round(xr + (yr - xr) * t);
      int cg = Math.round(xg + (yg - xg) * t);
      int cb = Math.round(xb + (yb - xb) * t);
      return (ca << 24) | (cr << 16) | (cg << 8) | cb;
   }
}
