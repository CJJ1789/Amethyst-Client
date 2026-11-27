package net.client.ui.md3.anim;

/** Tiny exponential interpolator used for hover / press / selection motion. */
public final class Md3Anim {
    private float value;
    private float target;

    public Md3Anim(float initial) {
        this.value = initial;
        this.target = initial;
    }

    public void target(float target) {
        this.target = target;
    }

    public void snap(float value) {
        this.value = value;
        this.target = value;
    }

    public float get() {
        return value;
    }

    public float update(float dt, float speed) {
        if (dt <= 0f) return value;
        float factor = 1f - (float) Math.exp(-speed * dt);
        value += (target - value) * factor;
        if (Math.abs(target - value) < 0.001f) value = target;
        return value;
    }

    public float update(float dt) {
        return update(dt, 14f);
    }

    public boolean settled() {
        return Math.abs(target - value) < 0.01f;
    }

    public static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t);
    }

    public static float emphasized(float t) {
        t = Math.max(0f, Math.min(1f, t));
        // Approximation of the M3 emphasized easing curve.
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3f) / 2f;
    }

    public static float standard(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
