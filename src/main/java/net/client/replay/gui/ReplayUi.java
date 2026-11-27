package net.client.replay.gui;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Paint;
import net.client.ui.md3.widget.Md3Surfaces;
import net.client.ui.md3.widget.Md3Text;

/**
 * 回放三套界面共用的 M3 绘制件：按钮、卡片、开关、行布局。
 *
 * <p>直接画在 {@link Md3Canvas} 上，命中测试由各 Screen 在 mouseClicked 里做，
 * 不依赖原版 ButtonWidget，所以手机端和桌面端的手感一致。</p>
 */
public final class ReplayUi {
    public enum Style {
        FILLED,
        TONAL,
        OUTLINED,
        TEXT,
        ICON,
        ICON_TONAL,
        DANGER
    }

    private ReplayUi() {
    }

    public static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public static void card(Md3Canvas c, Md3Theme t, float x, float y, float w, float h) {
        Md3Surfaces.card(c, t, x, y, w, h, Md3Surfaces.CardVariant.FILLED, Math.min(16f, h / 2f));
    }

    public static void button(Md3Canvas c, Md3Theme t, float x, float y, float w, float h,
                              String label, String icon, Style style, boolean enabled,
                              boolean hovered, boolean pressed) {
        int base;
        int content;
        switch (style) {
            case TONAL:
            case ICON_TONAL:
                base = t.colors.secondaryContainer;
                content = t.colors.onSecondaryContainer;
                break;
            case OUTLINED:
                base = t.colors.surfaceContainerLow;
                content = t.colors.primary;
                break;
            case TEXT:
                base = t.colors.surfaceContainerLow;
                content = t.colors.primary;
                break;
            case ICON:
                base = t.colors.surfaceContainerHigh;
                content = t.colors.onSurfaceVariant;
                break;
            case DANGER:
                base = t.colors.error;
                content = t.colors.onError;
                break;
            case FILLED:
            default:
                base = t.colors.primary;
                content = t.colors.onPrimary;
                break;
        }
        if (!enabled) {
            int disabled = Md3Paint.disabledContainer(base, t.colors.onSurface);
            c.roundRect(x, y, w, h, h / 2f, disabled);
            content = Md3Paint.disabledContent(t.colors.onSurface);
        } else {
            int painted = Md3Paint.container(base, content, hovered ? 0.10f : 0f, pressed ? 0.16f : 0f);
            if (style == Style.TEXT) {
                c.roundRect(x, y, w, h, 6f, painted);
            } else {
                c.roundRect(x, y, w, h, h / 2f, painted);
            }
            if (style == Style.OUTLINED) {
                c.strokeRoundRect(x, y, w, h, h / 2f, 1f, t.colors.outline);
            }
        }

        float iconSize = Math.min(18f, h - 4f);
        boolean hasLabel = label != null && !label.isEmpty();
        float gap = icon != null && hasLabel ? 6f : 0f;
        float tw = hasLabel ? c.textWidth(label, Md3Type.LABEL_MEDIUM) : 0f;
        float group = (icon != null ? iconSize + gap : 0f) + tw;
        float left = x + (w - group) / 2f;
        float cy = y + h / 2f;
        if (icon != null) {
            c.icon(icon, left, cy - iconSize / 2f, iconSize,
                    !enabled ? Md3Paint.disabledContent(t.colors.onSurface) : content);
            left += iconSize + gap;
        }
        if (hasLabel) {
            Md3Text.leftMiddle(c, label, left, cy, Md3Type.LABEL_MEDIUM,
                    !enabled ? Md3Paint.disabledContent(t.colors.onSurface) : content);
        }
    }

    /** 开关：文字在左，右侧画一个 M3 switch。 */
    public static void switchRow(Md3Canvas c, Md3Theme t, float x, float y, float w, float h,
                                 String label, boolean on, boolean enabled) {
        int color = enabled ? t.colors.onSurface : Md3Colors.alpha(t.colors.onSurface, 0.38f);
        Md3Text.leftMiddle(c, label, x, y + h / 2f, Md3Type.BODY_MEDIUM, color);
        float trackW = 36f;
        float trackH = 22f;
        float tx = x + w - trackW;
        float ty = y + (h - trackH) / 2f;
        int track = on ? t.colors.primary
                : Md3Colors.alpha(t.colors.onSurfaceVariant, 0.35f);
        if (!enabled) track = Md3Colors.alpha(t.colors.onSurface, 0.12f);
        c.roundRect(tx, ty, trackW, trackH, trackH / 2f, track);
        float thumb = 9f;
        float thumbX = tx + (on ? trackW - thumb * 2f - 2f : 2f);
        int thumbColor = enabled ? (on ? t.colors.onPrimary : t.colors.outline) : t.colors.onSurface;
        c.roundRect(thumbX, ty + (trackH - thumb * 2f) / 2f, thumb * 2f, thumb * 2f, thumb, thumbColor);
    }

    /** 只在右侧画开关，用于自己排文字的紧凑布局。 */
    public static void switchOnly(Md3Canvas c, Md3Theme t, float x, float y, float w, float h,
                                  boolean on, boolean enabled) {
        float trackW = Math.min(w, 36f);
        float trackH = Math.min(h, 22f);
        float tx = x + (w - trackW) / 2f;
        float ty = y + (h - trackH) / 2f;
        int track = on ? t.colors.primary : Md3Colors.alpha(t.colors.onSurfaceVariant, 0.35f);
        if (!enabled) track = Md3Colors.alpha(t.colors.onSurface, 0.12f);
        c.roundRect(tx, ty, trackW, trackH, trackH / 2f, track);
        float thumb = 9f;
        float thumbX = tx + (on ? trackW - thumb * 2f - 2f : 2f);
        int thumbColor = enabled ? (on ? t.colors.onPrimary : t.colors.outline) : t.colors.onSurface;
        c.roundRect(thumbX, ty + (trackH - thumb * 2f) / 2f, thumb * 2f, thumb * 2f, thumb, thumbColor);
    }

    public static void scrollbar(Md3Canvas c, Md3Theme t, float x, float y, float h, float value,
                                 float content, float viewport, boolean dragging) {
        if (content <= viewport + 0.5f) return;
        float thumbH = Math.max(20f, h * viewport / content);
        float max = content - viewport;
        float off = max <= 0f ? 0f : (value / max) * (h - thumbH);
        c.roundRect(x, y, 4f, h, 2f, Md3Colors.alpha(t.colors.onSurface, 0.06f));
        c.roundRect(x, y + off, 4f, thumbH, 2f,
                dragging ? t.colors.primary : Md3Colors.alpha(t.colors.onSurfaceVariant, 0.55f));
    }

    /** 用于长文本的结尾省略（Md3Canvas.textWidth 按 MD3 图集计算）。 */
    public static String fit(Md3Canvas c, String text, Md3Type type, float maxWidth) {
        return net.client.ui.ClientUi.fit(c, text, type, maxWidth);
    }

    public static void section(Md3Canvas c, Md3Theme t, String title, float x, float cy) {
        Md3Text.leftMiddle(c, title, x, cy, Md3Type.TITLE_SMALL, t.colors.onSurfaceVariant);
    }

    public static void value(Md3Canvas c, Md3Theme t, String label, String value,
                             float x, float cy, float w) {
        Md3Text.leftMiddle(c, label, x, cy, Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);
        Md3Text.rightMiddle(c, value, x + w, cy, Md3Type.BODY_SMALL, t.colors.onSurface);
    }
}
