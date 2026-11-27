package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;

/** M3 floating action button: small / regular / large / extended. */
public class Md3Fab extends Md3Widget {
    public enum Size {
        SMALL, REGULAR, LARGE, EXTENDED
    }

    public Size size = Size.REGULAR;
    public String icon = "add";
    public String label = "Compose";
    public int surface;

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        int base = t.colors.primaryContainer;
        int content = t.colors.onPrimaryContainer;
        if (!enabled) {
            base = Md3Paint.disabledContainer(surface != 0 ? surface : t.colors.surface, t.colors.onSurface);
            content = Md3Paint.disabledContent(t.colors.onSurface);
        }
        base = Md3Paint.container(base, content, hoverAnim.get(), pressAnim.get());
        c.shadow(x, y, w, h, Math.min(w, h) / 2f, size == Size.SMALL ? 2 : 4);
        c.roundRect(x, y, w, h, Math.min(w, h) / 2f, base);

        float iconSize = size == Size.LARGE ? 36f : (size == Size.SMALL ? 20f : 24f);
        if (size == Size.EXTENDED) {
            float tw = c.textWidth(label, Md3Type.LABEL_LARGE);
            float gap = 12f;
            float group = iconSize + gap + tw;
            float left = x + (w - group) / 2f;
            c.icon(icon, left, y + (h - iconSize) / 2f, iconSize, content);
            Md3Text.leftMiddle(c, label, left + iconSize + gap, y + h / 2f, Md3Type.LABEL_LARGE, content);
        } else {
            c.icon(icon, x + (w - iconSize) / 2f, y + (h - iconSize) / 2f, iconSize, content);
        }
    }
}
