package net.client.ui.md3.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.client.ui.md3.theme.Md3Type;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/** One baked font atlas (a size + weight) with per-glyph metrics. */
public final class Md3Font {
    public static final class Glyph {
        public final int x, y, w, h;
        public final float ox, oy, advance;
        public final float u0, v0, u1, v1;

        Glyph(int x, int y, int w, int h, float ox, float oy, float advance, float atlas) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.ox = ox;
            this.oy = oy;
            this.advance = advance;
            this.u0 = x / atlas;
            this.v0 = y / atlas;
            this.u1 = (x + w) / atlas;
            this.v1 = (y + h) / atlas;
        }
    }

    public final float ascent;
    public final float descent;
    public final float lineHeight;
    public final float supersample;
    public final float renderScale;
    public final int texture;
    public final Map<Character, Glyph> glyphs = new HashMap<Character, Glyph>();
    /** 字号档位的目标像素高度，用于把兜底字形缩放到当前字号。 */
    public float targetSize = 1f;
    /** 本档位图集缺字时使用的兜底图集（同字重、覆盖更全的那一档）。 */
    public Md3Font fallback;
    private static final Map<String, Integer> TEXTURES = new HashMap<String, Integer>();

    private Md3Font(float ascent, float descent, float lineHeight, float supersample,
                    float renderScale, int texture) {
        this.ascent = ascent;
        this.descent = descent;
        this.lineHeight = lineHeight;
        this.supersample = supersample;
        this.renderScale = renderScale;
        this.texture = texture;
    }

    public static Md3Font load(Md3Type type) {
        String base = "/assets/md3/font/" + type.atlasKey();
        try (InputStreamReader reader = new InputStreamReader(
                Md3Font.class.getResourceAsStream(base + ".json"), "UTF-8")) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            float atlas = root.get("atlas").getAsFloat();
            float ss = root.get("supersample").getAsFloat();
            Integer cached = TEXTURES.get(base);
            int tex;
            if (cached != null) {
                tex = cached;
            } else {
                java.awt.image.BufferedImage image = Md3Gl.loadPng(base + ".png");
                if (image == null) return null;
                tex = Md3Gl.uploadText(image);
                TEXTURES.put(base, tex);
            }
            float atlasSize = root.has("size") ? root.get("size").getAsFloat() : type.size;
            float renderScale = atlasSize > 0f ? type.size / atlasSize : 1f;
            Md3Font font = new Md3Font(
                    root.get("ascent").getAsFloat(),
                    root.get("descent").getAsFloat(),
                    root.get("lineHeight").getAsFloat(),
                    ss, renderScale, tex);
            font.targetSize = type.size;
            JsonObject glyphs = root.getAsJsonObject("glyphs");
            for (Map.Entry<String, JsonElement> e : glyphs.entrySet()) {
                String key = e.getKey();
                if (key.isEmpty() || key.length() > 1) continue;
                JsonObject g = e.getValue().getAsJsonObject();
                if (!g.has("x")) {
                    float adv = g.get("adv").getAsFloat();
                    font.glyphs.put(key.charAt(0), new Glyph(0, 0, 0, 0, 0f, 0f, adv, atlas));
                    continue;
                }
                font.glyphs.put(key.charAt(0), new Glyph(
                        g.get("x").getAsInt(), g.get("y").getAsInt(),
                        g.get("w").getAsInt(), g.get("h").getAsInt(),
                        g.get("ox").getAsFloat(), g.get("oy").getAsFloat(),
                        g.get("adv").getAsFloat(), atlas));
            }
            return font;
        } catch (Exception e) {
            System.err.println("[MD3] font atlas failed: " + type.atlasKey() + " " + e);
            return null;
        }
    }

    public float width(String text, float tracking) {
        float w = 0f;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            // 原版按钮有时带 §r/§7 之类的格式码；自定义字体不解析颜色码，
            // 直接跳过，否则会把 § 和后面的字母画成 “Sr” 之类乱码。
            if (ch == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            Glyph g = glyphs.get(ch);
            if (g == null) {
                if (fallback != null) {
                    Glyph fg = fallback.glyphs.get(ch);
                    if (fg != null) {
                        w += fg.advance * fallback.renderScale * shift(fallback, this) + tracking;
                    }
                }
                continue;
            }
            w += g.advance * this.renderScale + tracking;
        }
        return Math.max(0f, w - tracking);
    }

    private static float shift(Md3Font fb, Md3Font host) {
        return fb.targetSize > 0f ? host.targetSize / fb.targetSize : 1f;
    }

    /** 把某个字形按给定渲染缩放画到笔位；GUI 下吸附物理像素，世界里用线性放大。 */
    private static void glyph(Md3Canvas canvas, Md3Font font, Glyph g, float rs,
                              float pen, float yTop, int color) {
        if (canvas.pixelSnap) {
            // GUI 文字对齐到物理像素，避免小数坐标让最近邻采样抖动。
            float ps = Math.max(1f, canvas.pixelScale);
            float dx = Math.round((pen + g.ox * rs) * ps) / ps;
            float dy = Math.round((yTop + g.oy * rs) * ps) / ps;
            float gw = Math.max(1f, Math.round(g.w / font.supersample * rs * ps)) / ps;
            float gh = Math.max(1f, Math.round(g.h / font.supersample * rs * ps)) / ps;
            canvas.image(font.texture, dx, dy, gw, gh, g.u0, g.v0, g.u1, g.v1, color);
        } else {
            // 世界空间文字会被模型视图缩放/旋转，不能按 GUI 像素吸附，
            // 否则每个字母宽高被各自取整，看起来歪斜、发糊。
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, font.texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            canvas.image(font.texture, pen + g.ox * rs, yTop + g.oy * rs,
                    g.w / font.supersample * rs, g.h / font.supersample * rs,
                    g.u0, g.v0, g.u1, g.v1, color);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
    }

    /**
     * 1.0.2_1：图集现在是单通道 GL_ALPHA 纹理，RGB 需要由当前颜色提供。
     * COMBINE：RGB = PRIMARY_COLOR，ALPHA = 纹理 alpha × 当前颜色 alpha。
     */
    private static void beginAlphaText() {
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
    }

    private static void endAlphaText() {
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
    }

    public void draw(Md3Canvas canvas, String text, float x, float yTop, int color, float tracking) {
        float pen = x;
        beginAlphaText();
        try {
            drawRun(canvas, text, pen, yTop, color, tracking);
        } finally {
            endAlphaText();
        }
    }

    private void drawRun(Md3Canvas canvas, String text, float x, float yTop, int color,
                         float tracking) {
        float pen = x;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            Glyph g = glyphs.get(ch);
            if (g == null) {
                // 小字号图集只烘焙了常用汉字，个别字（如「烁」）会缺位。
                // 缺字时从同字重、覆盖更全的档位借字形并按字号比例缩放，
                // 否则文字会被整字吞掉（「关闭受击闪烁」显示成「关闭受击闪」）。
                Md3Font fb = this.fallback;
                if (fb != null) {
                    Glyph fg = fb.glyphs.get(ch);
                    if (fg != null) {
                        float scale = fb.targetSize > 0f ? this.targetSize / fb.targetSize : 1f;
                        glyph(canvas, fb, fg, fb.renderScale * scale, pen, yTop, color);
                        pen += fg.advance * fb.renderScale * shift(fb, this) + tracking;
                        continue;
                    }
                }
                continue;
            }
            if (g.w > 0) {
                glyph(canvas, this, g, this.renderScale, pen, yTop, color);
            }
            pen += g.advance * this.renderScale + tracking;
        }
    }
}
