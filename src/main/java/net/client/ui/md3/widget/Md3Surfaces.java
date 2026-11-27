package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;

/** Static M3 surfaces (cards, dialogs, snackbars, menus, tooltips). */
public final class Md3Surfaces {
    public enum CardVariant {
        ELEVATED, FILLED, OUTLINED
    }

    private Md3Surfaces() {
    }

    public static void card(Md3Canvas c, Md3Theme t, float x, float y, float w, float h,
                           CardVariant variant, float radius) {
        if (variant == CardVariant.ELEVATED) {
            c.shadow(x, y, w, h, radius, 1);
            c.roundRect(x, y, w, h, radius, t.colors.surfaceContainerLow);
        } else if (variant == CardVariant.FILLED) {
            c.roundRect(x, y, w, h, radius, t.colors.surfaceContainerHighest);
        } else {
            c.roundRect(x, y, w, h, radius, t.colors.surfaceContainerLowest);
            c.strokeRoundRect(x, y, w, h, radius, 1f, t.colors.outlineVariant);
        }
    }

    public static void dialog(Md3Canvas c, Md3Theme t, float x, float y, float w, float h) {
        c.shadow(x, y, w, h, 28f, 5);
        c.roundRect(x, y, w, h, 28f, t.colors.surfaceContainerHigh);
    }

    public static void snackbar(Md3Canvas c, Md3Theme t, float x, float y, float w, float h) {
        c.shadow(x, y, w, h, 4f, 3);
        c.roundRect(x, y, w, h, 4f, t.colors.inverseSurface);
    }

    public static void menu(Md3Canvas c, Md3Theme t, float x, float y, float w, float h) {
        c.shadow(x, y, w, h, 12f, 3);
        c.roundRect(x, y, w, h, 12f, t.colors.surfaceContainer);
    }

    public static void menuItem(Md3Canvas c, Md3Theme t, float x, float y, float w, float h,
                                String icon, String label, boolean selected) {
        if (selected) {
            c.roundRect(x + 4f, y, w - 8f, h, 8f, t.colors.secondaryContainer);
        }
        int color = selected ? t.colors.onSecondaryContainer : t.colors.onSurfaceVariant;
        c.icon(icon, x + 12f, y + h / 2f - 10f, 20f, color);
        Md3Text.leftMiddle(c, label, x + 44f, y + h / 2f,
                net.client.ui.md3.theme.Md3Type.BODY_MEDIUM, color);
    }

    public static void tooltip(Md3Canvas c, Md3Theme t, float x, float y, float w, float h, String label) {
        c.shadow(x, y, w, h, 4f, 2);
        c.roundRect(x, y, w, h, 4f, t.colors.surfaceContainerHighest);
        Md3Text.centered(c, label, x + w / 2f, y + h / 2f,
                net.client.ui.md3.theme.Md3Type.LABEL_MEDIUM, t.colors.onSurface);
    }

    public static void badge(Md3Canvas c, float x, float y, float d, int color, String label) {
        c.roundRect(x, y, d, d, d / 2f, color);
        if (label != null) {
            Md3Text.centered(c, label, x + d / 2f, y + d / 2f,
                    net.client.ui.md3.theme.Md3Type.LABEL_SMALL,
                    Md3Colors.rgb(0xFFFFFF));
        }
    }
}
