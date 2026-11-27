package net.client.ui.md3.theme;

/**
 * Material 3 colour roles.  Values follow the Material 3 baseline (seed #6750A4)
 * light / dark schemes.
 */
public final class Md3ColorScheme {
    public int primary, onPrimary, primaryContainer, onPrimaryContainer;
    public int secondary, onSecondary, secondaryContainer, onSecondaryContainer;
    public int tertiary, onTertiary, tertiaryContainer, onTertiaryContainer;
    public int error, onError, errorContainer, onErrorContainer;
    public int background, onBackground;
    public int surface, onSurface, surfaceVariant, onSurfaceVariant;
    public int surfaceDim, surfaceBright;
    public int surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
            surfaceContainerHigh, surfaceContainerHighest;
    public int outline, outlineVariant;
    public int inverseSurface, inverseOnSurface, inversePrimary;
    public int shadow, scrim;

    public Md3ColorScheme() {
    }

    public static Md3ColorScheme light() {
        Md3ColorScheme s = new Md3ColorScheme();
        s.primary = Md3Colors.rgb(0x6750A4);
        s.onPrimary = Md3Colors.rgb(0xFFFFFF);
        s.primaryContainer = Md3Colors.rgb(0xEADDFF);
        s.onPrimaryContainer = Md3Colors.rgb(0x21005D);
        s.secondary = Md3Colors.rgb(0x625B71);
        s.onSecondary = Md3Colors.rgb(0xFFFFFF);
        s.secondaryContainer = Md3Colors.rgb(0xE8DEF8);
        s.onSecondaryContainer = Md3Colors.rgb(0x1D192B);
        s.tertiary = Md3Colors.rgb(0x7D5260);
        s.onTertiary = Md3Colors.rgb(0xFFFFFF);
        s.tertiaryContainer = Md3Colors.rgb(0xFFD8E4);
        s.onTertiaryContainer = Md3Colors.rgb(0x31111D);
        s.error = Md3Colors.rgb(0xB3261E);
        s.onError = Md3Colors.rgb(0xFFFFFF);
        s.errorContainer = Md3Colors.rgb(0xF9DEDC);
        s.onErrorContainer = Md3Colors.rgb(0x410E0B);
        s.background = Md3Colors.rgb(0xFEF7FF);
        s.onBackground = Md3Colors.rgb(0x1D1B20);
        s.surface = Md3Colors.rgb(0xFEF7FF);
        s.onSurface = Md3Colors.rgb(0x1D1B20);
        s.surfaceVariant = Md3Colors.rgb(0xE7E0EC);
        s.onSurfaceVariant = Md3Colors.rgb(0x49454F);
        s.surfaceDim = Md3Colors.rgb(0xDED8E1);
        s.surfaceBright = Md3Colors.rgb(0xFEF7FF);
        s.surfaceContainerLowest = Md3Colors.rgb(0xFFFFFF);
        s.surfaceContainerLow = Md3Colors.rgb(0xF7F2FA);
        s.surfaceContainer = Md3Colors.rgb(0xF3EDF7);
        s.surfaceContainerHigh = Md3Colors.rgb(0xECE6F0);
        s.surfaceContainerHighest = Md3Colors.rgb(0xE6E0E9);
        s.outline = Md3Colors.rgb(0x79747E);
        s.outlineVariant = Md3Colors.rgb(0xCAC4D0);
        s.inverseSurface = Md3Colors.rgb(0x322F35);
        s.inverseOnSurface = Md3Colors.rgb(0xF5EFF7);
        s.inversePrimary = Md3Colors.rgb(0xD0BCFF);
        s.shadow = Md3Colors.rgb(0x000000);
        s.scrim = Md3Colors.rgb(0x000000);
        return s;
    }

    public static Md3ColorScheme dark() {
        Md3ColorScheme s = new Md3ColorScheme();
        s.primary = Md3Colors.rgb(0xD0BCFF);
        s.onPrimary = Md3Colors.rgb(0x381E72);
        s.primaryContainer = Md3Colors.rgb(0x4F378B);
        s.onPrimaryContainer = Md3Colors.rgb(0xEADDFF);
        s.secondary = Md3Colors.rgb(0xCCC2DC);
        s.onSecondary = Md3Colors.rgb(0x332D41);
        s.secondaryContainer = Md3Colors.rgb(0x4A4458);
        s.onSecondaryContainer = Md3Colors.rgb(0xE8DEF8);
        s.tertiary = Md3Colors.rgb(0xEFB8C8);
        s.onTertiary = Md3Colors.rgb(0x492532);
        s.tertiaryContainer = Md3Colors.rgb(0x633B48);
        s.onTertiaryContainer = Md3Colors.rgb(0xFFD8E4);
        s.error = Md3Colors.rgb(0xF2B8B5);
        s.onError = Md3Colors.rgb(0x601410);
        s.errorContainer = Md3Colors.rgb(0x8C1D18);
        s.onErrorContainer = Md3Colors.rgb(0xF9DEDC);
        s.background = Md3Colors.rgb(0x141218);
        s.onBackground = Md3Colors.rgb(0xE6E0E9);
        s.surface = Md3Colors.rgb(0x141218);
        s.onSurface = Md3Colors.rgb(0xE6E0E9);
        s.surfaceVariant = Md3Colors.rgb(0x49454F);
        s.onSurfaceVariant = Md3Colors.rgb(0xCAC4D0);
        s.surfaceDim = Md3Colors.rgb(0x141218);
        s.surfaceBright = Md3Colors.rgb(0x3B383E);
        s.surfaceContainerLowest = Md3Colors.rgb(0x0F0D13);
        s.surfaceContainerLow = Md3Colors.rgb(0x1D1B20);
        s.surfaceContainer = Md3Colors.rgb(0x211F26);
        s.surfaceContainerHigh = Md3Colors.rgb(0x2B2930);
        s.surfaceContainerHighest = Md3Colors.rgb(0x36343B);
        s.outline = Md3Colors.rgb(0x938F99);
        s.outlineVariant = Md3Colors.rgb(0x49454F);
        s.inverseSurface = Md3Colors.rgb(0xE6E0E9);
        s.inverseOnSurface = Md3Colors.rgb(0x313033);
        s.inversePrimary = Md3Colors.rgb(0x6750A4);
        s.shadow = Md3Colors.rgb(0x000000);
        s.scrim = Md3Colors.rgb(0x000000);
        return s;
    }

    public static Md3ColorScheme lerp(Md3ColorScheme a, Md3ColorScheme b, float t) {
        Md3ColorScheme s = new Md3ColorScheme();
        s.primary = Md3Colors.lerp(a.primary, b.primary, t);
        s.onPrimary = Md3Colors.lerp(a.onPrimary, b.onPrimary, t);
        s.primaryContainer = Md3Colors.lerp(a.primaryContainer, b.primaryContainer, t);
        s.onPrimaryContainer = Md3Colors.lerp(a.onPrimaryContainer, b.onPrimaryContainer, t);
        s.secondary = Md3Colors.lerp(a.secondary, b.secondary, t);
        s.onSecondary = Md3Colors.lerp(a.onSecondary, b.onSecondary, t);
        s.secondaryContainer = Md3Colors.lerp(a.secondaryContainer, b.secondaryContainer, t);
        s.onSecondaryContainer = Md3Colors.lerp(a.onSecondaryContainer, b.onSecondaryContainer, t);
        s.tertiary = Md3Colors.lerp(a.tertiary, b.tertiary, t);
        s.onTertiary = Md3Colors.lerp(a.onTertiary, b.onTertiary, t);
        s.tertiaryContainer = Md3Colors.lerp(a.tertiaryContainer, b.tertiaryContainer, t);
        s.onTertiaryContainer = Md3Colors.lerp(a.onTertiaryContainer, b.onTertiaryContainer, t);
        s.error = Md3Colors.lerp(a.error, b.error, t);
        s.onError = Md3Colors.lerp(a.onError, b.onError, t);
        s.errorContainer = Md3Colors.lerp(a.errorContainer, b.errorContainer, t);
        s.onErrorContainer = Md3Colors.lerp(a.onErrorContainer, b.onErrorContainer, t);
        s.background = Md3Colors.lerp(a.background, b.background, t);
        s.onBackground = Md3Colors.lerp(a.onBackground, b.onBackground, t);
        s.surface = Md3Colors.lerp(a.surface, b.surface, t);
        s.onSurface = Md3Colors.lerp(a.onSurface, b.onSurface, t);
        s.surfaceVariant = Md3Colors.lerp(a.surfaceVariant, b.surfaceVariant, t);
        s.onSurfaceVariant = Md3Colors.lerp(a.onSurfaceVariant, b.onSurfaceVariant, t);
        s.surfaceDim = Md3Colors.lerp(a.surfaceDim, b.surfaceDim, t);
        s.surfaceBright = Md3Colors.lerp(a.surfaceBright, b.surfaceBright, t);
        s.surfaceContainerLowest = Md3Colors.lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, t);
        s.surfaceContainerLow = Md3Colors.lerp(a.surfaceContainerLow, b.surfaceContainerLow, t);
        s.surfaceContainer = Md3Colors.lerp(a.surfaceContainer, b.surfaceContainer, t);
        s.surfaceContainerHigh = Md3Colors.lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, t);
        s.surfaceContainerHighest = Md3Colors.lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, t);
        s.outline = Md3Colors.lerp(a.outline, b.outline, t);
        s.outlineVariant = Md3Colors.lerp(a.outlineVariant, b.outlineVariant, t);
        s.inverseSurface = Md3Colors.lerp(a.inverseSurface, b.inverseSurface, t);
        s.inverseOnSurface = Md3Colors.lerp(a.inverseOnSurface, b.inverseOnSurface, t);
        s.inversePrimary = Md3Colors.lerp(a.inversePrimary, b.inversePrimary, t);
        s.shadow = Md3Colors.lerp(a.shadow, b.shadow, t);
        s.scrim = Md3Colors.lerp(a.scrim, b.scrim, t);
        return s;
    }

    /** Named palette used by the tonal-palette strip in the showcase. */
    public static int[] tonalPalette(String name) {
        if ("secondary".equals(name)) {
            return new int[]{
                    0xFF000000, 0xFF1D192B, 0xFF332D41, 0xFF4A4458, 0xFF625B71,
                    0xFF7A7289, 0xFF958DA5, 0xFFB0A7C0, 0xFFCCC2DC, 0xFFE8DEF8,
                    0xFFF6EDFF, 0xFFFFFBFF, 0xFFFFFFFF};
        }
        if ("tertiary".equals(name)) {
            return new int[]{
                    0xFF000000, 0xFF31111D, 0xFF492532, 0xFF633B48, 0xFF7D5260,
                    0xFF986977, 0xFFB38392, 0xFFD29DAC, 0xFFEFB8C8, 0xFFFFD8E4,
                    0xFFFFECF1, 0xFFFFFBFF, 0xFFFFFFFF};
        }
        if ("neutral".equals(name)) {
            return new int[]{
                    0xFF000000, 0xFF1D1B20, 0xFF322F35, 0xFF48464C, 0xFF605D64,
                    0xFF79767D, 0xFF938F96, 0xFFAEA9B1, 0xFFCAC5CD, 0xFFE6E0E9,
                    0xFFF5EFF7, 0xFFFEF7FF, 0xFFFFFFFF};
        }
        if ("neutral-variant".equals(name)) {
            return new int[]{
                    0xFF000000, 0xFF1D1A22, 0xFF322F37, 0xFF49454F, 0xFF605D66,
                    0xFF79747E, 0xFF938F99, 0xFFAEA9B4, 0xFFCAC4D0, 0xFFE7E0EC,
                    0xFFF5EEFA, 0xFFFEF7FF, 0xFFFFFFFF};
        }
        return new int[]{
                0xFF000000, 0xFF21005D, 0xFF381E72, 0xFF4F378B, 0xFF6750A4,
                0xFF7F67BE, 0xFF9A82DB, 0xFFB69DF8, 0xFFD0BCFF, 0xFFEADDFF,
                0xFFF6EDFF, 0xFFFFFBFE, 0xFFFFFFFF};
    }
}
