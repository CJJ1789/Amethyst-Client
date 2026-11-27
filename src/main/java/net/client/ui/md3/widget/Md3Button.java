package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;

/** M3 common button: filled / tonal / elevated / outlined / text. */
public class Md3Button extends Md3Widget {
    public enum Variant {
        FILLED, TONAL, ELEVATED, OUTLINED, TEXT
    }

    public Variant variant = Variant.FILLED;
    public String label = "Button";
    public String icon;
    public int surface;
    /** 1.0.2_6：标题界面的「玻璃白」样式 —— 半透明白底、自然圆角、白字，不带主题色。 */
    public boolean glassStyle;
    /** 1.0.2_9：纯图标按钮 —— 强制圆形（半径 = 高的一半），标题页右上角那两颗用。 */
    public boolean circle;
    public Md3Type labelType = Md3Type.LABEL_LARGE;

    public Md3Button() {
    }

    public Md3Button(Variant variant, String label) {
        this.variant = variant;
        this.label = label;
    }

    /** 玻璃样式给自然圆角（约 0.3 倍高度），普通 MD3 按钮仍是满高胶囊。 */
    private float radiusFor() {
        if (circle) {
            return h / 2f;
        }
        return glassStyle ? Math.min(8f, h / 2f) : h / 2f;
    }

    private boolean hoveredFactor() {
        return hoverAnim.get() > 0.5f;
    }

    @Override
    public void render(Md3Canvas c, Md3Theme t, float mx, float my, boolean down, float dt) {
        pointer(mx, my, down, dt);
        if (!visible) return;

        int content;
        int base;
        switch (variant) {
            case TONAL:
                base = glassStyle ? Md3Colors.alpha(0xFFFFFF, hoveredFactor() ? 0.56f : 0.44f)
                        : t.colors.secondaryContainer;
                content = glassStyle ? 0xFFFFFFFF : t.colors.onSecondaryContainer;
                break;
            case ELEVATED:
                base = t.colors.surfaceContainerLow;
                content = t.colors.primary;
                c.shadow(x, y, w, h, h / 2f, 1);
                break;
            case OUTLINED:
                base = surface != 0 ? surface : t.colors.surface;
                content = t.colors.primary;
                break;
            case TEXT:
                base = surface != 0 ? surface : t.colors.surface;
                content = t.colors.primary;
                break;
            case FILLED:
            default:
                base = t.colors.primary;
                content = t.colors.onPrimary;
                break;
        }

        if (!enabled) {
            int disabledBase = Md3Paint.disabledContainer(surface != 0 ? surface : t.colors.surface, t.colors.onSurface);
            c.roundRect(x, y, w, h, radiusFor(), disabledBase);
            if (variant == Variant.OUTLINED) {
                c.strokeRoundRect(x, y, w, h, h / 2f, 1f, Md3Colors.alpha(t.colors.onSurface, 0.12f));
            }
            content = Md3Paint.disabledContent(t.colors.onSurface);
        } else {
            int layerColor = variant == Variant.FILLED ? t.colors.onPrimary : content;
            int painted = Md3Paint.container(base, layerColor, hoverAnim.get(), pressAnim.get());
            c.roundRect(x, y, w, h, radiusFor(), painted);
            if (variant == Variant.OUTLINED && !glassStyle) {
                c.strokeRoundRect(x, y, w, h, radiusFor(), 1f, t.colors.outline);
            }
        }

        float iconSize = 18f;
        boolean hasLabel = label != null && !label.isEmpty();
        float gap = icon != null && hasLabel ? 8f : 0f;
        float tw = hasLabel ? c.textWidth(label, labelType) : 0f;
        float group = (icon != null ? iconSize + gap : 0f) + tw;
        float left = x + (w - group) / 2f;
        float cy = y + h / 2f;
        // 1.0.2_7：玻璃样式是白底白字，和原版按钮一样得压一层浅阴影，否则压在亮背景上糊成一片。
        if (glassStyle) {
            int shadow = Md3Colors.alpha(0x000000, 0.30f);
            if (icon != null) {
                c.icon(icon, left + 1f, cy - iconSize / 2f + 1f, iconSize, shadow);
            }
            if (hasLabel) {
                // 阴影要用「文字自己的起点」，也就是给图标让位之后的 x；直接用 left 会把
                // 阴影画到图标位置上，看起来像文字重影。
                float labelX = left + (icon != null ? iconSize + gap : 0f);
                Md3Text.leftMiddle(c, label, labelX + 1f, cy + 1f, labelType, shadow);
            }
        }
        if (icon != null) {
            c.icon(icon, left, cy - iconSize / 2f, iconSize, content);
            left += iconSize + gap;
        }
        if (hasLabel) {
            Md3Text.leftMiddle(c, label, left, cy, labelType, content);
        }
    }
}
