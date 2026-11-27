package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.anim.Md3Anim;

/**
 * M3 dropdown menu: an anchored surface with one row per option and a check
 * mark on the current one.  Rendering is immediate-mode; the caller only has
 * to remember to draw it last so it stays above the underlying screen.
 */
public class Md3Menu {
    public String[] items = new String[0];
    public int selected;
    public float x, y, w;
    public float itemHeight = 40f;
    public float padding = 4f;
    public int surface;
    /** 1.0.2_1：调用方可以覆盖菜单底色（设置界面用来跟随「背景不透明度」）。 */
    public int surfaceColor;
    public boolean hasSurface;

    private final Md3Anim openAnim = new Md3Anim(0f);

    public float height() {
        return padding * 2f + items.length * itemHeight;
    }

    public void open() {
        openAnim.snap(0f);
        openAnim.target(1f);
    }

    public void update(float dt) {
        openAnim.update(dt, 16f);
    }

    public float anim() {
        return openAnim.get();
    }

    public int indexAt(float mx, float my) {
        if (mx < x || mx >= x + w || my < y || my >= y + height()) return -1;
        float local = my - y - padding;
        if (local < 0f) return -1;
        int index = (int) Math.floor(local / itemHeight);
        return index >= 0 && index < items.length ? index : -1;
    }

    public void render(Md3Canvas c, Md3Theme t, float mx, float my) {
        float h = height();
        float a = anim();
        if (a <= 0.01f) return;
        float grow = 4f * (1f - a);
        if (this.hasSurface) {
            c.shadow(x, y + grow, w, h - grow * 2f, 12f, 3);
            c.roundRect(x, y + grow, w, h - grow * 2f, 12f, this.surfaceColor);
        } else {
            Md3Surfaces.menu(c, t, x, y + grow, w, h - grow * 2f);
        }
        c.pushClip(x + 2f, y + 2f, w - 4f, h - 4f);
        int hover = indexAt(mx, my);
        for (int i = 0; i < items.length; i++) {
            float iy = y + padding + i * itemHeight;
            boolean current = i == selected;
            if (i == hover) {
                c.roundRect(x + 4f, iy, w - 8f, itemHeight, 8f,
                        net.client.ui.md3.theme.Md3Colors.alpha(t.colors.onSurface, 0.06f));
            }
            if (current) {
                c.roundRect(x + 4f, iy, w - 8f, itemHeight, 8f, t.colors.secondaryContainer);
            }
            int color = current ? t.colors.onSecondaryContainer : t.colors.onSurface;
            String label = items[i];
            float max = w - 56f;
            if (c.textWidth(label, Md3Type.BODY_MEDIUM) > max) {
                int end = label.length();
                while (end > 0 && c.textWidth(label.substring(0, end) + "...", Md3Type.BODY_MEDIUM) > max) end--;
                label = label.substring(0, end) + "...";
            }
            Md3Text.leftMiddle(c, label, x + 16f, iy + itemHeight / 2f,
                    Md3Type.BODY_MEDIUM, color);
            if (current) {
                c.icon("check", x + w - 32f, iy + itemHeight / 2f - 9f, 18f, color);
            }
        }
        c.popClip();
    }
}
