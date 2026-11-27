package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;

/** M3 chip: assist / filter / input / suggestion. */
public class Md3Chip extends Md3Widget {
    public enum Variant {
        ASSIST, FILTER, INPUT, SUGGESTION
    }

    public Variant variant = Variant.ASSIST;
    public String label = "Chip";
    public String icon;
    public boolean selected;
    public int surface;

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        int base;
        int content;
        boolean outline;
        if (selected) {
            base = t.colors.secondaryContainer;
            content = t.colors.onSecondaryContainer;
            outline = false;
        } else if (variant == Variant.SUGGESTION) {
            base = t.colors.surfaceContainerLow;
            content = t.colors.onSurfaceVariant;
            outline = false;
        } else {
            base = surface != 0 ? surface : t.colors.surface;
            content = t.colors.onSurfaceVariant;
            outline = true;
        }
        if (!enabled) {
            base = Md3Paint.disabledContainer(surface != 0 ? surface : t.colors.surface, t.colors.onSurface);
            content = Md3Paint.disabledContent(t.colors.onSurface);
            outline = false;
        }
        int painted = Md3Paint.container(base, content, hoverAnim.get(), pressAnim.get());
        c.roundRect(x, y, w, h, h / 2f, painted);
        if (outline) c.strokeRoundRect(x, y, w, h, h / 2f, 1f, t.colors.outlineVariant);

        float cy = y + h / 2f;
        float iconSize = 18f;
        float gap = 6f;
        String lead = icon;
        if (selected && variant == Variant.FILTER) lead = "check";
        float tw = c.textWidth(label, Md3Type.LABEL_MEDIUM);
        float group = (lead != null ? iconSize + gap : 0f) + tw;
        float left = x + (w - group) / 2f;
        if (lead != null) {
            c.icon(lead, left, cy - iconSize / 2f, iconSize, content);
            left += iconSize + gap;
        }
        Md3Text.leftMiddle(c, label, left, cy, Md3Type.LABEL_MEDIUM, content);
    }
}
