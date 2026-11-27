package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;

/**
 * M3 list item: leading icon or monogram badge, one/two-line text and an
 * optional trailing icon.  Interactive state layers follow the spec.
 */
public class Md3ListItem extends Md3Widget {
    public String headline = "";
    public String supporting;
    public String leadingIcon;
    public String leadingText;
    public String trailingIcon;
    public String trailingText;
    public boolean selected;
    public boolean interactive = true;
    public boolean showDivider;
    public int surface;

    public float headlineTop = 0.5f;

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        if (!visible) return;

        int container = selected ? t.colors.secondaryContainer
                : (surface != 0 ? surface : t.colors.surface);
        if (selected) {
            c.roundRect(x, y, w, h, 14f, container);
        } else if (interactive && hoverAnim.get() > 0.01f) {
            c.roundRect(x, y, w, h, 14f,
                    Md3Colors.alpha(t.colors.onSurface, 0.055f * hoverAnim.get()));
        }

        int content = selected ? t.colors.onSecondaryContainer : t.colors.onSurface;
        int support = selected ? Md3Colors.alpha(t.colors.onSecondaryContainer, 0.78f)
                : t.colors.onSurfaceVariant;

        float left = x + 16f;
        float cy = y + h / 2f;
        if (leadingIcon != null) {
            float d = 32f;
            c.roundRect(left, cy - d / 2f, d, d, d / 2f,
                    selected ? Md3Colors.alpha(t.colors.onSecondaryContainer, 0.10f)
                            : t.colors.surfaceContainerHighest);
            c.icon(leadingIcon, left + 6f, cy - 10f, 20f, content);
            left += d + 14f;
        } else if (leadingText != null && !leadingText.isEmpty()) {
            float d = 32f;
            c.roundRect(left, cy - d / 2f, d, d, d / 2f,
                    selected ? Md3Colors.alpha(t.colors.onSecondaryContainer, 0.10f)
                            : t.colors.surfaceContainerHighest);
            Md3Text.centered(c, leadingText.substring(0, 1).toUpperCase(), left + d / 2f, cy,
                    Md3Type.TITLE_SMALL, content);
            left += d + 14f;
        }

        float right = x + w - 14f;
        if (trailingIcon != null) {
            float d = 36f;
            c.roundRect(right - d, cy - d / 2f, d, d, d / 2f,
                    hoverAnim.get() > 0.01f && interactive
                            ? Md3Colors.alpha(t.colors.onSurface, 0.06f) : 0);
            c.icon(trailingIcon, right - d + 8f, cy - 10f, 20f, content);
            right -= d + 6f;
        } else if (trailingText != null && !trailingText.isEmpty()) {
            float tw = c.textWidth(trailingText, Md3Type.LABEL_MEDIUM);
            right -= tw;
            Md3Text.rightMiddle(c, trailingText, right + tw, cy, Md3Type.LABEL_MEDIUM,
                    selected ? t.colors.onSecondaryContainer : t.colors.onSurfaceVariant);
            right -= 10f;
        }

        float textRight = right - 6f;
        float textWidth = Math.max(20f, textRight - left);
        if (supporting != null && !supporting.isEmpty()) {
            float top = y + (h - 34f) / 2f;
            String head = fit(c, headline, Md3Type.BODY_LARGE, textWidth);
            c.pushClip(left, top, textWidth, 34f);
            c.text(head, left, top, Md3Type.BODY_LARGE, content);
            String sub = fit(c, supporting, Md3Type.BODY_MEDIUM, textWidth);
            c.text(sub, left, top + 18f, Md3Type.BODY_MEDIUM, support);
            c.popClip();
        } else {
            float top = y + h / 2f;
            c.pushClip(left, y, textWidth, h);
            Md3Text.leftMiddle(c, fit(c, headline, Md3Type.BODY_LARGE, textWidth),
                    left, top, Md3Type.BODY_LARGE, content);
            c.popClip();
        }

        if (showDivider) {
            c.fill(x + 16f, y + h - 1f, w - 32f, 1f, t.colors.outlineVariant);
        }
    }

    public boolean consumeActivation() {
        return consumeClick();
    }

    private static String fit(Md3Canvas c, String text, Md3Type type, float maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0f) return "";
        if (c.textWidth(text, type) <= maxWidth) return text;
        String ellipsis = "...";
        float ew = c.textWidth(ellipsis, type);
        int end = text.length();
        while (end > 0 && c.textWidth(text.substring(0, end), type) + ew > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }
}
