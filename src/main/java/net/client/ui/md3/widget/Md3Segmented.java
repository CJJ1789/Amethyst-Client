package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;

/** M3 segmented button row. */
public class Md3Segmented extends Md3Widget {
    public String[] labels = {"Day", "Week", "Month"};
    public int selected;
    public boolean showCheck = true;
    public int surface;

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        float seg = w / labels.length;
        // Selected container fills the whole segment; the group outline is painted last.
        if (selected >= 0 && selected < labels.length) {
            float sx = x + seg * selected;
            int color = t.colors.secondaryContainer;
            if (labels.length == 1) {
                c.roundRect(sx, y, seg, h, h / 2f, color);
            } else if (selected == 0) {
                // Rounded on the group start, square where it meets the next segment.
                c.roundRect(sx, y, seg, h, h / 2f, color);
                c.fill(sx + seg - h / 2f, y, h / 2f, h, color);
            } else if (selected == labels.length - 1) {
                // Square on the left edge, rounded on the group end.
                c.roundRect(sx, y, seg, h, h / 2f, color);
                c.fill(sx, y, h / 2f, h, color);
            } else {
                // Middle segments are rectangular and fill the frame completely.
                c.fill(sx, y, seg, h, color);
            }
        }
        for (int i = 1; i < labels.length; i++) {
            c.fill(x + seg * i, y + 1f, 1f, h - 2f, t.colors.outlineVariant);
        }
        c.strokeRoundRect(x, y, w, h, h / 2f, 1f, t.colors.outline);
        for (int i = 0; i < labels.length; i++) {
            float sx = x + seg * i;
            String label = labels[i];
            float tw = c.textWidth(label, Md3Type.LABEL_LARGE);
            String icon = (i == selected && showCheck) ? "check" : null;
            float iconSize = 16f, gap = 6f;
            float group = (icon != null ? iconSize + gap : 0f) + tw;
            float left = sx + (seg - group) / 2f;
            int color = i == selected ? t.colors.onSecondaryContainer : t.colors.onSurfaceVariant;
            if (icon != null) {
                c.icon(icon, left, y + h / 2f - iconSize / 2f, iconSize, color);
                left += iconSize + gap;
            }
            Md3Text.leftMiddle(c, label, left, y + h / 2f, Md3Type.LABEL_LARGE, color);
        }
        if (consumeClick() && enabled) {
            int i = (int) ((mx - x) / (w / labels.length));
            if (i >= 0 && i < labels.length) selected = i;
        }
    }
}
