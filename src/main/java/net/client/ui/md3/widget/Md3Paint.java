package net.client.ui.md3.widget;

import net.client.ui.md3.theme.Md3Colors;

/** State-layer / disabled colour helpers shared by widgets. */
public final class Md3Paint {
    private Md3Paint() {
    }

    /** MD3 state layer: 8% hover, 12% press, composited over the container. */
    public static int container(int base, int layer, float hover, float press) {
        float opacity = Math.max(hover * 0.08f, press * 0.12f);
        if (opacity <= 0f) return base;
        return Md3Colors.composite(Md3Colors.alpha(layer, opacity), base);
    }

    public static int disabledContainer(int surface, int onSurface) {
        return Md3Colors.composite(Md3Colors.alpha(onSurface, 0.12f), surface);
    }

    public static int disabledContent(int onSurface) {
        return Md3Colors.alpha(onSurface, 0.38f);
    }
}
