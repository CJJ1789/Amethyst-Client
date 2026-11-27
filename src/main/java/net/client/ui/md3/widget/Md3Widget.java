package net.client.ui.md3.widget;

import net.client.ui.md3.anim.Md3Anim;

/** Base class for all interactive Material 3 widgets (immediate-mode style). */
public abstract class Md3Widget {
    public float x, y, w, h;
    public boolean enabled = true;
    public boolean visible = true;

    protected boolean hovered;
    protected boolean pressed;
    protected boolean pressedInside;
    private boolean wasDown;

    public boolean clicked;

    protected final Md3Anim hoverAnim = new Md3Anim(0f);
    protected final Md3Anim pressAnim = new Md3Anim(0f);

    public void setBounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    protected boolean contains(float mx, float my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected void pointer(float mx, float my, boolean down, float dt) {
        hovered = enabled && contains(mx, my);
        if (down && !wasDown) {
            pressedInside = hovered;
        }
        pressed = down && pressedInside;
        if (!down) {
            clicked = wasDown && pressedInside && hovered;
            pressedInside = false;
            pressed = false;
        } else {
            clicked = false;
        }
        wasDown = down;
        hoverAnim.target(hovered ? 1f : 0f);
        pressAnim.target(pressed ? 1f : 0f);
        hoverAnim.update(dt, 12f);
        pressAnim.update(dt, 18f);
    }

    public abstract void render(net.client.ui.md3.render.Md3Canvas canvas,
                                net.client.ui.md3.theme.Md3Theme theme,
                                float mx, float my, boolean down, float dt);

    public boolean consumeClick() {
        boolean c = clicked;
        clicked = false;
        return c;
    }
}
