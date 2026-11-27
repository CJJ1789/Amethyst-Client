package net.client.ui.md3.widget;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Type;

/** Text alignment helpers. */
public final class Md3Text {
    private Md3Text() {
    }

    /** y offset that visually centres a line of type around {@code centerY}. */
    public static float topForCenter(float centerY, Md3Type type) {
        return centerY - type.size * 0.56f;
    }

    /** 按真实行高把单行文字垂直居中，修正搜索框等输入框文字偏低。 */
    public static float topForLineCenter(float centerY, Md3Type type) {
        return centerY - type.lineHeight * 0.5f;
    }

    public static void centered(Md3Canvas canvas, String text, float cx, float cy,
                                Md3Type type, int color) {
        canvas.textCentered(text, cx, topForCenter(cy, type), type, color);
    }

    public static void leftMiddle(Md3Canvas canvas, String text, float x, float cy,
                                  Md3Type type, int color) {
        canvas.text(text, x, topForCenter(cy, type), type, color);
    }

    public static void rightMiddle(Md3Canvas canvas, String text, float rightX, float cy,
                                   Md3Type type, int color) {
        canvas.text(text, rightX - canvas.textWidth(text, type), topForCenter(cy, type), type, color);
    }
}
