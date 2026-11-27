package net.client.ui.md3.render;

import net.client.ui.md3.theme.Md3Type;

import java.util.HashMap;
import java.util.Map;

/** Font atlas registry + text helpers. */
public final class Md3Fonts {
    private static final Map<String, Md3Font> CACHE = new HashMap<String, Md3Font>();

    private Md3Fonts() {
    }

    public static Md3Font get(Md3Type type) {
        String key = type.atlasKey() + "@" + type.size + "@" + type.tracking;
        Md3Font font = CACHE.get(key);
        if (font == null) {
            font = Md3Font.load(type);
            if (font != null) {
                font.fallback = fallbackFor(type);
                CACHE.put(key, font);
            }
        }
        return font;
    }

    /**
     * 同字重里覆盖最全的档位，专门用来补小字号图集中缺掉的字形
     * （medium_13/15/18 只烘焙了约 1000 个常用字，medium_12 有 7600+）。
     */
    private static Md3Font fallbackFor(Md3Type type) {
        Md3Type fbType;
        if (type.weight == Md3Type.Weight.MEDIUM) {
            fbType = Md3Type.BODY_MEDIUM;   // medium_12
        } else {
            fbType = Md3Type.BODY_LARGE;    // regular_14
        }
        if (fbType.size == type.size && fbType.weight == type.weight) {
            return null;
        }
        Md3Font fb = CACHE.get(fbType.atlasKey() + "@" + fbType.size + "@" + fbType.tracking);
        if (fb == null) {
            fb = Md3Font.load(fbType);
            if (fb == null) {
                return null;
            }
            fb.fallback = null;
            CACHE.put(fbType.atlasKey() + "@" + fbType.size + "@" + fbType.tracking, fb);
        }
        return fb;
    }

    public static float width(String text, Md3Type type) {
        Md3Font font = get(type);
        if (font == null) return text.length() * type.size * 0.55f;
        return font.width(text, type.tracking);
    }

    public static float draw(Md3Canvas canvas, String text, float x, float yTop, Md3Type type, int color) {
        Md3Font font = get(type);
        if (font == null) return 0f;
        font.draw(canvas, text, x, yTop, color, type.tracking);
        return font.width(text, type.tracking);
    }

    /** Greedy word/CJK wrapping. */
    public static java.util.List<String> wrap(String text, Md3Type type, float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder line = new StringBuilder();
        int lastBreak = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                lines.add(line.toString());
                line.setLength(0);
                lastBreak = -1;
                continue;
            }
            line.append(ch);
            if (ch == ' ' || isCjk(ch)) lastBreak = line.length();
            if (width(line.toString(), type) > maxWidth && line.length() > 1) {
                int cut = lastBreak > 0 ? lastBreak : line.length() - 1;
                String head = line.substring(0, cut).trim();
                if (!head.isEmpty()) lines.add(head);
                String tail = line.substring(cut).trim();
                line.setLength(0);
                line.append(tail);
                lastBreak = -1;
            }
        }
        if (line.length() > 0) lines.add(line.toString().trim());
        return lines;
    }

    private static boolean isCjk(char c) {
        return c >= 0x2E80 && c <= 0x9FFF || c >= 0xF900 && c <= 0xFAFF;
    }
}
