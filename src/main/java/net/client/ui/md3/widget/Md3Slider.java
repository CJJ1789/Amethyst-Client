package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;

/** Continuous M3 slider. */
public class Md3Slider extends Md3Widget {
    public float value;
    public boolean enabled = true;
    public int surface;
    private boolean dragging;
    private float halo;

    public Md3Slider(float value) {
        this.value = value;
    }

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        float trackY = y + h / 2f - 2f;
        float thumbR = 10f;
        float travel = w - thumbR * 2f;
        boolean over = enabled && mx >= x && mx <= x + w && my >= y && my <= y + h;

        if (down && !dragging && over) {
            dragging = true;
        }
        if (dragging) {
            value = Math.max(0f, Math.min(1f, (mx - x - thumbR) / travel));
        }
        if (!down) dragging = false;
        halo += ((dragging || over ? 1f : 0f) - halo) * Math.min(1f, dt * 14f);

        int active = enabled ? t.colors.primary : Md3Colors.alpha(t.colors.onSurface, 0.38f);
        int inactive = enabled ? t.colors.surfaceContainerHighest : Md3Colors.alpha(t.colors.onSurface, 0.12f);

        c.roundRect(x, trackY, w, 4f, 2f, inactive);
        c.roundRect(x, trackY, value * w, 4f, 2f, active);

        float thumbX = x + thumbR + value * travel;
        if (halo > 0.02f) {
            float r = 20f;
            c.roundRect(thumbX - r, y + h / 2f - r, r * 2f, r * 2f, r,
                    Md3Colors.alpha(t.colors.primary, 0.10f * halo));
        }
        c.roundRect(thumbX - thumbR, y + h / 2f - thumbR, thumbR * 2f, thumbR * 2f, thumbR, active);
    }
}
