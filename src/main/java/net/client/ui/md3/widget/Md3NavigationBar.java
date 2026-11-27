package net.client.ui.md3.widget;

import net.client.ui.md3.anim.Md3Anim;
import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;

/** M3 navigation bar. */
public class Md3NavigationBar extends Md3Widget {
    public String[] labels = {"Home", "Search", "Saved", "Profile"};
    public String[] icons = {"home", "search", "favorite", "person"};
    public int selected;
    public float barHeight = 64f;
    public Md3Anim indicator = new Md3Anim(0f);

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        c.fill(x, y, w, barHeight, t.colors.surfaceContainer);
        c.fill(x, y, w, 1f, t.colors.outlineVariant);
        float seg = w / labels.length;
        indicator.target(selected);
        indicator.update(dt, 14f);
        float pillW = 64f;
        float pillX = x + seg * indicator.get() + (seg - pillW) / 2f;
        c.roundRect(pillX, y + 8f, pillW, 32f, 16f, t.colors.primaryContainer);
        for (int i = 0; i < labels.length; i++) {
            float sx = x + seg * i;
            boolean active = i == selected;
            int color = active ? t.colors.onPrimaryContainer : t.colors.onSurfaceVariant;
            c.icon(icons[i], sx + (seg - 20f) / 2f, y + 14f, 20f, color);
            Md3Text.centered(c, labels[i], sx + seg / 2f, y + 48f, Md3Type.LABEL_SMALL, color);
        }
        if (consumeClick() && enabled) {
            int i = (int) ((mx - x) / seg);
            if (i >= 0 && i < labels.length) selected = i;
        }
    }
}
