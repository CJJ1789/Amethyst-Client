package net.client.ui.md3.theme;

/** Small colour toolkit operating on packed 0xAARRGGBB ints. */
public final class Md3Colors {
    private Md3Colors() {
    }

    public static int rgb(int hex) {
        return 0xFF000000 | (hex & 0xFFFFFF);
    }

    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int alpha(int color, float alpha) {
        return alpha(color, (int) (alpha * 255f + 0.5f));
    }

    public static int a(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int r(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int g(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int b(int color) {
        return color & 0xFF;
    }

    public static int lerp(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int aa = (int) (a(from) + (a(to) - a(from)) * t);
        int rr = (int) (r(from) + (r(to) - r(from)) * t);
        int gg = (int) (g(from) + (g(to) - g(from)) * t);
        int bb = (int) (b(from) + (b(to) - b(from)) * t);
        return argb(aa, rr, gg, bb);
    }

    /** Source-over composite of straight-alpha colours. */
    public static int composite(int fg, int bg) {
        float fa = a(fg) / 255f;
        float ba = a(bg) / 255f;
        float outA = fa + ba * (1f - fa);
        if (outA <= 0.0001f) return 0;
        float wf = fa / outA;
        float wb = ba * (1f - fa) / outA;
        int rr = (int) (r(fg) * wf + r(bg) * wb);
        int gg = (int) (g(fg) * wf + g(bg) * wb);
        int bb = (int) (b(fg) * wf + b(bg) * wb);
        return argb((int) (outA * 255f), rr, gg, bb);
    }

    /** State layer: role colour at the given opacity laid over a surface. */
    public static int stateLayer(int surface, int role, float opacity) {
        return composite(alpha(role, opacity), surface);
    }

    public static int lighten(int color, float amount) {
        int rr = (int) (r(color) + (255 - r(color)) * amount);
        int gg = (int) (g(color) + (255 - g(color)) * amount);
        int bb = (int) (b(color) + (255 - b(color)) * amount);
        return argb(a(color), rr, gg, bb);
    }

    public static int darken(int color, float amount) {
        int rr = (int) (r(color) * (1f - amount));
        int gg = (int) (g(color) * (1f - amount));
        int bb = (int) (b(color) * (1f - amount));
        return argb(a(color), rr, gg, bb);
    }
}
