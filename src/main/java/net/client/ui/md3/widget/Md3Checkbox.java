package net.client.ui.md3.widget;

import net.client.ui.md3.anim.Md3Anim;
import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;

/** M3 checkbox with checked / indeterminate states. */
public class Md3Checkbox extends Md3Widget {
    public boolean checked;
    public boolean indeterminate;
    public int surface;
    private final Md3Anim check = new Md3Anim(0f);

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        float box = 18f;
        float bx = x + (w - box) / 2f;
        float by = y + (h - box) / 2f;
        float a = check.get();

        if (hoverAnim.get() > 0.01f && enabled) {
            c.roundRect(x + w / 2f - 20f, y + h / 2f - 20f, 40f, 40f, 20f,
                    Md3Colors.alpha(t.colors.onSurface, 0.08f * hoverAnim.get()));
        }

        if (!enabled) {
            c.strokeRoundRect(bx, by, box, box, 4f, 2f, Md3Colors.alpha(t.colors.onSurface, 0.38f));
        } else if (a < 0.5f) {
            c.strokeRoundRect(bx, by, box, box, 4f, 2f, t.colors.onSurfaceVariant);
        } else {
            c.roundRect(bx, by, box, box, 4f, t.colors.primary);
        }
        if (indeterminate) {
            c.roundRect(bx + 4f, by + box / 2f - 1.5f, box - 8f, 3f, 1.5f, t.colors.onPrimary);
        } else if (a > 0.5f) {
            float iconSize = 14f;
            c.icon("check", bx + (box - iconSize) / 2f, by + (box - iconSize) / 2f, iconSize, t.colors.onPrimary);
        }

        float target = (checked || indeterminate) ? 1f : 0f;
        check.target(target);
        check.update(dt, 18f);
        if (consumeClick() && enabled) {
            checked = !checked;
            indeterminate = false;
        }
    }
}
