package net.client.ui.md3.widget;

import net.client.ui.md3.anim.Md3Anim;
import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;

/** M3 radio button. */
public class Md3Radio extends Md3Widget {
    public boolean selected;
    public int surface;
    public int group;
    private final Md3Anim dot = new Md3Anim(0f);

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        float d = 20f;
        float bx = x + (w - d) / 2f;
        float by = y + (h - d) / 2f;

        if (hoverAnim.get() > 0.01f && enabled) {
            c.roundRect(x + w / 2f - 20f, y + h / 2f - 20f, 40f, 40f, 20f,
                    Md3Colors.alpha(t.colors.onSurface, 0.08f * hoverAnim.get()));
        }
        int ring = enabled ? (selected ? t.colors.primary : t.colors.onSurfaceVariant)
                : Md3Colors.alpha(t.colors.onSurface, 0.38f);
        c.strokeRoundRect(bx, by, d, d, d / 2f, 2f, ring);
        float a = dot.get();
        if (a > 0.01f) {
            float inner = 10f * a;
            c.roundRect(x + w / 2f - inner / 2f, y + h / 2f - inner / 2f, inner, inner, inner / 2f,
                    enabled ? t.colors.primary : ring);
        }
        dot.target(selected ? 1f : 0f);
        dot.update(dt, 20f);
        if (consumeClick() && enabled) {
            selected = true;
        }
    }
}
