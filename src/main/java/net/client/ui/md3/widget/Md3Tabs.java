package net.client.ui.md3.widget;

import net.client.ui.md3.anim.Md3Anim;
import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;

/** M3 primary / secondary tabs with an animated indicator. */
public class Md3Tabs extends Md3Widget {
    public String[] labels = {"Overview", "Details", "Reviews"};
    public int selected;
    public boolean primary = true;
    public Md3Anim indicator = new Md3Anim(0f);

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        float seg = w / labels.length;
        indicator.target(selected);
        indicator.update(dt, 14f);
        float ix = x + seg * indicator.get();
        float indicatorW = seg - 32f;
        c.roundRect(ix + 16f, y + h - 3f, indicatorW, 3f, 1.5f,
                primary ? t.colors.primary : t.colors.primary);
        if (!primary) {
            c.fill(x, y + h - 1f, w, 1f, t.colors.outlineVariant);
        }
        for (int i = 0; i < labels.length; i++) {
            float sx = x + seg * i;
            if (hoverAnim.get() > 0.01f && hovered && mx >= sx && mx < sx + seg) {
                c.roundRect(sx + 4f, y + 4f, seg - 8f, h - 8f, 8f,
                        Md3Colors.alpha(t.colors.onSurface, 0.05f));
            }
            boolean active = i == selected;
            int color = active ? (primary ? t.colors.primary : t.colors.onSurface) : t.colors.onSurfaceVariant;
            Md3Text.centered(c, labels[i], sx + seg / 2f, y + h / 2f, Md3Type.LABEL_LARGE, color);
        }
        if (consumeClick() && enabled) {
            int i = (int) ((mx - x) / seg);
            if (i >= 0 && i < labels.length) selected = i;
        }
    }
}
