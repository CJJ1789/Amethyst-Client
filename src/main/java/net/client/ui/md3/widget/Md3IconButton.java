package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Theme;

/** M3 icon button: standard / filled / tonal / outlined. */
public class Md3IconButton extends Md3Widget {
    public enum Variant {
        STANDARD, FILLED, TONAL, OUTLINED
    }

    public Variant variant = Variant.STANDARD;
    public String icon = "settings";
    public boolean selected;
    public int surface;

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        int base;
        int content;
        switch (variant) {
            case FILLED:
                base = t.colors.primary;
                content = t.colors.onPrimary;
                break;
            case TONAL:
                base = t.colors.secondaryContainer;
                content = t.colors.onSecondaryContainer;
                break;
            case OUTLINED:
                base = surface != 0 ? surface : t.colors.surface;
                content = t.colors.onSurfaceVariant;
                break;
            case STANDARD:
            default:
                base = surface != 0 ? surface : t.colors.surface;
                content = t.colors.onSurfaceVariant;
                break;
        }
        if (selected) {
            base = t.colors.primaryContainer;
            content = t.colors.onPrimaryContainer;
        }
        if (!enabled) {
            int disabledBase = Md3Paint.disabledContainer(surface != 0 ? surface : t.colors.surface, t.colors.onSurface);
            c.roundRect(x, y, w, h, w / 2f, disabledBase);
            content = Md3Paint.disabledContent(t.colors.onSurface);
        } else {
            int painted = Md3Paint.container(base, content, hoverAnim.get(), pressAnim.get());
            c.roundRect(x, y, w, h, w / 2f, painted);
            if (variant == Variant.OUTLINED && !selected) {
                c.strokeRoundRect(x, y, w, h, w / 2f, 1f, t.colors.outline);
            }
        }
        float iconSize = Math.min(24f, w * 0.5f);
        c.icon(icon, x + (w - iconSize) / 2f, y + (h - iconSize) / 2f, iconSize, content);
    }
}
