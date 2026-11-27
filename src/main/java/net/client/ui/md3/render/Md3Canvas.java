package net.client.ui.md3.render;

import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Type;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Immediate-mode Material 3 drawing surface.
 *
 * Coordinates are Minecraft GUI pixels (the same space {@code Screen} uses),
 * while all masks are rasterised at physical resolution so corners and text
 * stay sharp on every GUI scale.
 */
public final class Md3Canvas {
    /** 1.0.2_4：接管原版页面时需要在 Screen 之外自建一个画布来铺毛玻璃。 */
    public Md3Canvas() {
    }

    private final Deque<int[]> clips = new ArrayDeque<int[]>();

    public int width;
    public int height;
    public int scale = 1;
    public float pixelScale = 1f;
    /** GUI 文字可以吸附物理像素；世界空间文字（名牌、告示牌）不能吸附。 */
    public boolean pixelSnap = true;
    private int framebufferHeight;

    private boolean textureOn = true;
    private int boundTexture = -1;
    private int currentColor = 0xFFFFFFFF;
    private boolean active;
    private boolean prevDepth;
    private boolean prevCull;
    private boolean prevAlpha;
    private boolean prevLighting;
    private boolean prevBlend;
    private boolean prevTexture;
    private boolean prevScissor;
    private int prevTextureId;

    public void begin(int width, int height) {
        beginState(width, height, true);
    }

    /**
     * Begin drawing in the caller's current projection/model-view matrix.  Used
     * by text that lives in the 3D world (name tags, signs) where replacing the
     * projection would move glyphs away from the entity.
     */
    public void beginPreserve(int width, int height) {
        beginState(width, height, false);
    }

    private void beginState(int width, int height, boolean guiMatrix) {
        this.width = width;
        this.height = height;
        this.pixelScale = width > 0 ? Display.getWidth() / (float) width : 1f;
        this.scale = Math.max(1, Math.round(pixelScale));
        this.pixelSnap = true;
        this.framebufferHeight = Display.getHeight();
        this.active = true;
        this.clips.clear();

        // World text (signs, name tags) is drawn in the middle of the world
        // pass.  Keep depth testing there, otherwise the glyphs and every sign
        // rendered afterwards show through terrain.  GUI text still draws on
        // top.  All changed state is restored in endState().
        this.prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        this.prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        this.prevAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        this.prevLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        this.prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        this.prevTexture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        this.prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        this.prevTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        if (guiMatrix) {
            GlStateManager.disableDepthTest();
        } else if (this.isWorldProjection()) {
            // Sign/name-tag text must still be occluded by terrain.  The sign
            // renderer calls depthMask(false) but leaves depth testing on;
            // force it only for a real 3D projection so chat/HUD stays on top.
            GlStateManager.enableDepthTest();
        }
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableAlphaTest();
        GlStateManager.disableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        textureOn = true;
        boundTexture = -1;
        currentColor = 0xFFFFFFFF;

        if (guiMatrix) {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(0f, 0f, -2000f);
        }
    }

    public void end() {
        endState(true);
    }

    public void endPreserve() {
        endState(false);
    }

    private boolean isWorldProjection() {
        FloatBuffer projection = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        return Math.abs(projection.get(0)) > 0.02f;
    }

    private void endState(boolean guiMatrix) {
        if (!active) return;
        active = false;
        while (!clips.isEmpty()) popClip();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.bindTexture(0);
        GlStateManager.enableTexture();
        GlStateManager.color(1f, 1f, 1f, 1f);
        textureOn = true;
        boundTexture = -1;
        if (guiMatrix) {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }

        // GUI passes deliberately run without depth testing; leave it off for
        // the next GUI primitive.  World text restores exactly what its caller
        // had, which is what keeps signs depth-tested against terrain.
        if (guiMatrix) GlStateManager.disableDepthTest();
        else if (this.isWorldProjection()) GlStateManager.enableDepthTest();
        else if (this.prevDepth) GlStateManager.enableDepthTest();
        else GlStateManager.disableDepthTest();
        if (this.prevCull) GlStateManager.enableCull();
        else GlStateManager.disableCull();
        if (this.prevAlpha) GlStateManager.enableAlphaTest();
        else GlStateManager.disableAlphaTest();
        if (this.prevLighting) GlStateManager.enableLighting();
        else GlStateManager.disableLighting();
        if (this.prevBlend) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
        if (this.prevTexture) GlStateManager.enableTexture();
        else GlStateManager.disableTexture();
        if (this.prevTextureId > 0) GlStateManager.bindTexture(this.prevTextureId);
        if (this.prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        textureOn = this.prevTexture;
        boundTexture = this.prevTextureId;
    }

    /* ------------------------------------------------------------------ */
    /* state                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Forget the cached texture / colour state.  Call this after drawing
     * through a foreign renderer (vanilla {@code DrawableHelper}, an item or a
     * resource-pack icon) so subsequent MD3 primitives rebind correctly.
     */
    public void invalidateState() {
        boundTexture = -1;
        currentColor = 0xFFFFFFFF;
        textureOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void color(int argb) {
        if (argb == currentColor) return;
        currentColor = argb;
        GlStateManager.color(Md3Colors.r(argb) / 255f, Md3Colors.g(argb) / 255f,
                Md3Colors.b(argb) / 255f, Md3Colors.a(argb) / 255f);
    }

    private void texture(int id) {
        if (id <= 0) {
            if (textureOn) {
                GlStateManager.disableTexture();
                textureOn = false;
                boundTexture = -1;
            }
            return;
        }
        if (!textureOn) {
            GlStateManager.enableTexture();
            textureOn = true;
            boundTexture = -1;
        }
        if (id != boundTexture) {
            GlStateManager.bindTexture(id);
            boundTexture = id;
        }
    }

    private void quad(float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x0, y0);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x1, y0);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x1, y1);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x0, y1);
        GL11.glEnd();
    }

    /* ------------------------------------------------------------------ */
    /* primitives                                                         */
    /* ------------------------------------------------------------------ */

    public void fill(float x, float y, float w, float h, int argb) {
        if (w <= 0f || h <= 0f || Md3Colors.a(argb) == 0) return;
        texture(0);
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    public void image(int textureId, float x, float y, float w, float h,
                      float u0, float v0, float u1, float v1, int argb) {
        if (w <= 0f || h <= 0f || Md3Colors.a(argb) == 0) return;
        texture(textureId);
        color(argb);
        quad(x, y, x + w, y + h, u0, v0, u1, v1);
    }

    public void roundRect(float x, float y, float w, float h, float radius, int argb) {
        if (w <= 0f || h <= 0f || Md3Colors.a(argb) == 0) return;
        float r = Math.min(radius, Math.min(w, h) / 2f);
        if (r < 0.75f) {
            fill(x, y, w, h, argb);
            return;
        }
        int rpx = Math.max(1, Math.round(r * scale));
        int tex = Md3Shapes.fillCorner(rpx);
        fill(x + r, y, w - 2f * r, h, argb);
        fill(x, y + r, r, h - 2f * r, argb);
        fill(x + w - r, y + r, r, h - 2f * r, argb);
        image(tex, x, y, r, r, 0f, 0f, 1f, 1f, argb);
        image(tex, x + w - r, y, r, r, 1f, 0f, 0f, 1f, argb);
        image(tex, x, y + h - r, r, r, 0f, 1f, 1f, 0f, argb);
        image(tex, x + w - r, y + h - r, r, r, 1f, 1f, 0f, 0f, argb);
    }

    /**
     * 用纹理填充一个圆角矩形（圆角处按圆弧裁掉，不会露出直角）。
     *
     * 中间和左右是条带，四个角各用一块「四分之一圆盘」的三角扇补上（扇心就是圆角圆心），
     * 采样区域严格等于圆角形状本身，相邻控件不会互相「框」出方块边。
     */
    public void imageRound(int textureId, float x, float y, float w, float h, float radius,
                           float u0, float v0, float u1, float v1, int argb) {
        if (w <= 0f || h <= 0f || Md3Colors.a(argb) == 0) return;
        float r = Math.min(radius, Math.min(w, h) / 2f);
        if (r < 0.75f) {
            image(textureId, x, y, w, h, u0, v0, u1, v1, argb);
            return;
        }
        float sx = (u1 - u0) / w;
        float sy = (v1 - v0) / h;
        image(textureId, x + r, y, w - 2f * r, h,
                u0 + sx * r, v0, u1 - sx * r, v1, argb);
        image(textureId, x, y + r, r, h - 2f * r,
                u0, v0 + sy * r, u0 + sx * r, v1 - sy * r, argb);
        image(textureId, x + w - r, y + r, r, h - 2f * r,
                u1 - sx * r, v0 + sy * r, u1, v1 - sy * r, argb);

        texture(textureId);
        color(argb);
        cornerDisc(x, y, w, h, u0, v0, u1, v1, x + r, y + r, r, 180f, 270f);
        cornerDisc(x, y, w, h, u0, v0, u1, v1, x + w - r, y + r, r, 270f, 360f);
        cornerDisc(x, y, w, h, u0, v0, u1, v1, x + w - r, y + h - r, r, 0f, 90f);
        cornerDisc(x, y, w, h, u0, v0, u1, v1, x + r, y + h - r, r, 90f, 180f);
    }

    /** 四分之一圆盘（圆角头）：扇心是圆角圆心，弧由 from→to。 */
    private void cornerDisc(float x, float y, float w, float h,
                            float u0, float v0, float u1, float v1,
                            float cx, float cy, float rad,
                            float from, float to) {
        int seg = 8;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glTexCoord2f(u0 + (cx - x) / w * (u1 - u0), v0 + (cy - y) / h * (v1 - v0));
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(from + (to - from) * (i / (float) seg));
            float vx = cx + (float) Math.cos(a) * rad;
            float vy = cy + (float) Math.sin(a) * rad;
            GL11.glTexCoord2f(u0 + (vx - x) / w * (u1 - u0), v0 + (vy - y) / h * (v1 - v0));
            GL11.glVertex2f(vx, vy);
        }
        GL11.glEnd();
    }

    /** Rounded on the top corners only (filled text fields). */
    public void roundRectTop(float x, float y, float w, float h, float radius, int argb) {
        if (w <= 0f || h <= 0f || Md3Colors.a(argb) == 0) return;
        float r = Math.min(radius, Math.min(w / 2f, h));
        if (r < 0.75f) {
            fill(x, y, w, h, argb);
            return;
        }
        int rpx = Math.max(1, Math.round(r * scale));
        int tex = Md3Shapes.fillCorner(rpx);
        fill(x, y + r, w, h - r, argb);
        fill(x + r, y, w - 2f * r, r, argb);
        image(tex, x, y, r, r, 0f, 0f, 1f, 1f, argb);
        image(tex, x + w - r, y, r, r, 1f, 0f, 0f, 1f, argb);
    }

    public void strokeRoundRect(float x, float y, float w, float h, float radius,
                                float stroke, int argb) {
        if (w <= 0f || h <= 0f || Md3Colors.a(argb) == 0) return;
        float r = Math.min(radius, Math.min(w, h) / 2f);
        float s = Math.max(stroke, 1f / scale);
        int spx = Math.max(1, Math.round(s * scale));
        if (r < 0.75f) {
            fill(x, y, w, s, argb);
            fill(x, y + h - s, w, s, argb);
            fill(x, y + s, s, h - 2f * s, argb);
            fill(x + w - s, y + s, s, h - 2f * s, argb);
            return;
        }
        int rpx = Math.max(1, Math.round(r * scale));
        int tex = Md3Shapes.strokeCorner(rpx, spx);
        fill(x + r, y, w - 2f * r, s, argb);
        fill(x + r, y + h - s, w - 2f * r, s, argb);
        fill(x, y + r, s, h - 2f * r, argb);
        fill(x + w - s, y + r, s, h - 2f * r, argb);
        image(tex, x, y, r, r, 0f, 0f, 1f, 1f, argb);
        image(tex, x + w - r, y, r, r, 1f, 0f, 0f, 1f, argb);
        image(tex, x, y + h - r, r, r, 0f, 1f, 1f, 0f, argb);
        image(tex, x + w - r, y + h - r, r, r, 1f, 1f, 0f, 0f, argb);
    }

    /** Soft M3-style elevation shadow, built from a few expanding layers. */
    public void shadow(float x, float y, float w, float h, float radius, int level) {
        if (level <= 0 || w <= 0f || h <= 0f) return;
        float blur = 2.0f + level * 1.5f;
        int layers = 4 + level;
        float total = 0.10f + level * 0.022f;
        float per = total / layers;
        int alpha = Math.max(1, (int) (per * 255f));
        int color = Md3Colors.argb(alpha, 0, 0, 0);
        float offsetY = level * 0.7f;
        for (int i = layers; i >= 1; i--) {
            float grow = blur * i / layers;
            roundRect(x - grow, y - grow + offsetY, w + 2f * grow, h + 2f * grow,
                    radius + grow, color);
        }
    }

    public void glow(float x, float y, float w, float h, int argb) {
        image(Md3Shapes.glow(), x, y, w, h, 0f, 0f, 1f, 1f, argb);
    }

    public void verticalGradient(float x, float y, float w, float h, int top, int bottom) {
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            float t0 = i / (float) bands;
            float t1 = (i + 1) / (float) bands;
            int c = Md3Colors.lerp(top, bottom, (t0 + t1) * 0.5f);
            fill(x, y + h * t0, w, h * (t1 - t0) + 0.5f, c);
        }
    }

    public void pushMatrix() {
        GlStateManager.pushMatrix();
    }

    public void popMatrix() {
        GlStateManager.popMatrix();
    }

    public void scale(float factor) {
        GlStateManager.scale(factor, factor, 1f);
    }

    public void translate(float x, float y) {
        GlStateManager.translate(x, y, 0f);
    }

    public void rotate(float degrees) {
        GlStateManager.rotate(degrees, 0f, 0f, 1f);
    }

    /* ------------------------------------------------------------------ */
    /* text + icons                                                       */
    /* ------------------------------------------------------------------ */

    public float text(String text, float x, float yTop, Md3Type type, int color) {
        return Md3Fonts.draw(this, text, x, yTop, type, color);
    }

    public float textWidth(String text, Md3Type type) {
        return Md3Fonts.width(text, type);
    }

    public void textCentered(String text, float centerX, float yTop, Md3Type type, int color) {
        text(text, centerX - textWidth(text, type) / 2f, yTop, type, color);
    }

    public void icon(String name, float x, float y, float size, int color) {
        Md3Icons.draw(this, name, x, y, size, color);
    }

    /* ------------------------------------------------------------------ */
    /* clipping                                                           */
    /* ------------------------------------------------------------------ */

    public void pushClip(float x, float y, float w, float h) {
        int nx = (int) Math.floor(x);
        int ny = (int) Math.floor(y);
        int nr = (int) Math.ceil(x + w);
        int nb = (int) Math.ceil(y + h);
        if (!clips.isEmpty()) {
            int[] p = clips.peek();
            nx = Math.max(nx, p[0]);
            ny = Math.max(ny, p[1]);
            nr = Math.min(nr, p[0] + p[2]);
            nb = Math.min(nb, p[1] + p[3]);
        }
        int nw = Math.max(0, nr - nx);
        int nh = Math.max(0, nb - ny);
        clips.push(new int[]{nx, ny, nw, nh});
        applyScissor(nx, ny, nw, nh);
    }

    public void popClip() {
        if (clips.isEmpty()) return;
        clips.pop();
        if (clips.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            int[] p = clips.peek();
            applyScissor(p[0], p[1], p[2], p[3]);
        }
    }

    private void applyScissor(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, 0, 0);
            return;
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        // Use the exact design->framebuffer ratio; integer rounding clips the
        // right/bottom edge on fractional GUI scales (e.g. 3.28x).
        int px = (int) Math.floor(x * pixelScale);
        int py = (int) Math.floor(framebufferHeight - (y + h) * pixelScale);
        int pw = (int) Math.ceil(w * pixelScale);
        int ph = (int) Math.ceil(h * pixelScale);
        GL11.glScissor(px, py, pw, ph);
    }
}
