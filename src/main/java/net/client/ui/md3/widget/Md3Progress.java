package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.render.Md3Shapes;

/** M3 linear + circular progress indicators. */
public final class Md3Progress {
    private Md3Progress() {
    }

    public static void linear(Md3Canvas c, float x, float y, float w,
                              int color, int trackColor, float value,
                              boolean indeterminate, float time) {
        float h = 4f;
        c.roundRect(x, y, w, h, 2f, trackColor);
        if (indeterminate) {
            float phase = (time * 0.8f) % 1.6f;
            float p = Math.min(1f, Math.max(0f, phase / 1.2f));
            float eased = net.client.ui.md3.anim.Md3Anim.emphasized(p);
            float barW = w * (0.30f + 0.18f * (float) Math.sin(p * Math.PI));
            float bx = x + (w - barW) * eased;
            c.roundRect(bx, y, barW, h, 2f, color);
        } else {
            c.roundRect(x, y, w * Math.max(0f, Math.min(1f, value)), h, 2f, color);
        }
    }

    public static void circular(Md3Canvas c, float cx, float cy, float diameter,
                                float thickness, int color, int trackColor,
                                float value, boolean indeterminate, float time) {
        float r = diameter / 2f;
        c.strokeRoundRect(cx - r, cy - r, diameter, diameter, r, thickness, trackColor);
        if (!indeterminate && value <= 0.001f) return;
        int rpx = Math.max(2, Math.round(r * c.scale));
        int tpx = Math.max(1, Math.round(thickness * c.scale));
        float angle;
        int sweep;
        if (indeterminate) {
            sweep = 260;
            angle = (time * 300f) % 360f;
        } else {
            sweep = Math.min(359, Math.round(value * 360f));
            if (value >= 0.999f) {
                c.strokeRoundRect(cx - r, cy - r, diameter, diameter, r, thickness, color);
                return;
            }
            angle = -90f;
        }
        int tex = Md3Shapes.arc(rpx, tpx, sweep);
        float drawSize = (rpx * 2 + 2) / (float) c.scale;
        float left = cx - drawSize / 2f;
        float top = cy - drawSize / 2f;
        c.pushMatrix();
        c.translate(cx, cy);
        c.rotate(angle);
        c.translate(-cx, -cy);
        c.image(tex, left, top, drawSize, drawSize, 0f, 0f, 1f, 1f, color);
        c.popMatrix();
    }

    public static void linearGradientText(Md3Canvas c, String value, float x, float right,
                                          float cy, int color) {
        net.client.ui.md3.theme.Md3Type type = net.client.ui.md3.theme.Md3Type.LABEL_MEDIUM;
        Md3Text.rightMiddle(c, value, right, cy, type, color);
    }
}
