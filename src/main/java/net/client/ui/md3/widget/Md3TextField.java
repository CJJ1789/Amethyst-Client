package net.client.ui.md3.widget;

import net.client.ui.md3.anim.Md3Anim;
import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;

/** M3 text field: filled and outlined. */
public class Md3TextField extends Md3Widget {
    public enum Variant {
        FILLED, OUTLINED
    }

    public Variant variant = Variant.FILLED;
    public String label = "Label";
    public String text = "";
    public String supporting = "Supporting text";
    public String leadingIcon;
    public String trailingIcon;
    public boolean error;
    public boolean focused;
    public int surface;
    public boolean focusRequested;
    /** Render bullets instead of the real characters (passwords / tokens). */
    public boolean masked;
    public int maxLength = 256;

    private final Md3Anim floatAnim = new Md3Anim(0f);

    private String display() {
        if (!masked) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) sb.append('*');
        return sb.toString();
    }

    public void typeChar(char ch) {
        if (ch >= 32 && ch != 127 && text.length() < maxLength) text += ch;
    }

    public void backspace() {
        if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
    }

    public void clear() {
        text = "";
    }

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        if (consumeClick() && enabled) focusRequested = true;

        boolean floating = focused || !text.isEmpty();
        floatAnim.target(floating ? 1f : 0f);
        floatAnim.update(dt, 14f);
        float a = floatAnim.get();

        int accent = error ? t.colors.error : (focused ? t.colors.primary : t.colors.outline);
        int labelColor = error ? t.colors.error : (focused ? t.colors.primary : t.colors.onSurfaceVariant);
        int textColor = enabled ? t.colors.onSurface : Md3Paint.disabledContent(t.colors.onSurface);

        if (variant == Variant.FILLED) {
            c.roundRectTop(x, y, w, h, 4f, t.colors.surfaceContainerHighest);
            c.fill(x, y + h - (focused ? 2f : 1f), w, focused ? 2f : 1f, accent);
        } else {
            c.roundRect(x, y, w, h, 4f, surface != 0 ? surface : t.colors.surface);
            c.strokeRoundRect(x, y, w, h, 4f, focused ? 2f : 1f, accent);
        }

        float textX = x + 16f;
        if (leadingIcon != null) {
            c.icon(leadingIcon, x + 12f, y + h / 2f - 10f, 20f, labelColor);
            textX = x + 44f;
        }
        if (trailingIcon != null) {
            c.icon(trailingIcon, x + w - 32f, y + h / 2f - 10f, 20f, labelColor);
        }

        // Floating label
        if (a > 0.02f) {
            float ly = variant == Variant.OUTLINED ? y - 6f : y + 8f;
            if (variant == Variant.OUTLINED && !label.isEmpty()) {
                float tw = c.textWidth(label, Md3Type.BODY_SMALL);
                c.fill(textX - 3f, ly - 1f, tw + 6f, 14f, surface != 0 ? surface : t.colors.surface);
            }
            c.text(label, textX, ly, Md3Type.BODY_SMALL, Md3Colors.alpha(labelColor, a));
        }
        // Resting label
        if (a < 0.98f) {
            float ly = Md3Text.topForCenter(y + h / 2f, Md3Type.BODY_LARGE);
            c.text(label, textX, ly, Md3Type.BODY_LARGE,
                    Md3Colors.alpha(enabled ? t.colors.onSurfaceVariant : Md3Paint.disabledContent(t.colors.onSurface),
                            (1f - a) * (enabled ? 1f : 0.38f)));
        }

        boolean hasLabel = label != null && !label.isEmpty();
        float inputY;
        float clipTop;
        if (variant == Variant.OUTLINED) {
            inputY = hasLabel ? y + 27f : Md3Text.topForLineCenter(y + h / 2f, Md3Type.BODY_LARGE);
            clipTop = hasLabel ? y + 18f : y + 6f;
        } else {
            inputY = y + 30f;
            clipTop = y + 18f;
        }
        c.pushClip(x + 8f, clipTop, w - 16f, h - (clipTop - y) - 4f);
        float tw = c.text(display(), textX, inputY, Md3Type.BODY_LARGE, textColor);
        if (focused && enabled && (System.currentTimeMillis() / 500L) % 2 == 0) {
            c.fill(textX + tw + 1f, inputY - 1f, 1f, 15f, t.colors.primary);
        }
        c.popClip();

        if (!supporting.isEmpty()) {
            c.text(supporting, x + 16f, y + h + 6f, Md3Type.BODY_SMALL, labelColor);
        }
    }
}
