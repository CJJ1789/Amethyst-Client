package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;

/**
 * M3 navigation rail: a vertical list of icon + label destinations with a
 * pill-shaped active indicator.  The rail owns its scroll offset but leaves
 * the surrounding surface to the caller, so it can sit inside any pane.
 */
public class Md3NavigationRail extends Md3Widget {
    public String[] labels = new String[0];
    public String[] icons = new String[0];
    public int selected;
    /** Last row activated by a click; the screen consumes and resets this. */
    public int activatedIndex = -1;
    public float itemHeight = 48f;
    public float scroll;
    public float topPadding;
    public float bottomPadding = 8f;
    public int surface;

    public int visibleCount() {
        float inner = h - topPadding - bottomPadding;
        return Math.max(1, (int) Math.floor(inner / itemHeight));
    }

    public float maxScroll() {
        float content = labels.length * itemHeight + topPadding + bottomPadding;
        return Math.max(0f, content - h);
    }

    public void clampScroll() {
        if (scroll < 0f) scroll = 0f;
        float max = maxScroll();
        if (scroll > max) scroll = max;
    }

    public int indexAt(float mx, float my) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return -1;
        float local = my - y + scroll - topPadding;
        if (local < 0f) return -1;
        int index = (int) Math.floor(local / itemHeight);
        if (index < 0 || index >= labels.length) return -1;
        return index;
    }

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        clampScroll();

        if (surface != 0) {
            c.roundRect(x, y, w, h, 20f, surface);
        }

        c.pushClip(x, y + 4f, w, h - 8f);
        int hoverIndex = indexAt(mx, my);
        for (int i = 0; i < labels.length; i++) {
            float iy = y + topPadding + i * itemHeight - scroll;
            if (iy + itemHeight < y - 4f || iy > y + h + 4f) continue;

            boolean active = i == selected;
            boolean hot = hoverIndex == i && enabled;
            float pillH = itemHeight - 8f;
            if (active) {
                c.roundRect(x + 8f, iy + 4f, w - 16f, pillH, pillH / 2f,
                        Md3Colors.alpha(t.colors.secondaryContainer, 0.92f));
            } else if (hot) {
                c.roundRect(x + 8f, iy + 4f, w - 16f, pillH, pillH / 2f,
                        Md3Colors.alpha(t.colors.onSurface, 0.06f));
            }

            int color = active ? t.colors.onSecondaryContainer
                    : (hot ? t.colors.onSurface : t.colors.onSurfaceVariant);
            float iconSize = 19f;
            String icon = i < icons.length ? icons[i] : null;
            float textX;
            if (icon != null) {
                c.icon(icon, x + 20f, iy + itemHeight / 2f - iconSize / 2f, iconSize, color);
                textX = x + 44f;
            } else {
                textX = x + 20f;
            }

            String label = labels[i];
            float avail = x + w - 12f - textX;
            label = fit(c, label, Md3Type.LABEL_LARGE, avail);
            Md3Text.leftMiddle(c, label, textX, iy + itemHeight / 2f, Md3Type.LABEL_LARGE, color);
        }
        c.popClip();

        if (consumeClick() && enabled) {
            int index = indexAt(mx, my);
            if (index >= 0) {
                selected = index;
                activatedIndex = index;
            }
        }
    }

    private static String fit(Md3Canvas c, String text, Md3Type type, float maxWidth) {
        if (maxWidth <= 0f) return "";
        if (c.textWidth(text, type) <= maxWidth) return text;
        String ellipsis = "...";
        float ew = c.textWidth(ellipsis, type);
        int end = text.length();
        while (end > 0 && c.textWidth(text.substring(0, end), type) + ew > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }
}
