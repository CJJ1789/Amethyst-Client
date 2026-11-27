package net.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * 1.0.2_10：照 ReGlass（github.com/RedxAx/ReGlass）的做法重写的液态玻璃。
 *
 * 旧版是用 9 个（后来 25 个）取样点做平均 —— 那是「偏移叠加」，半径一大就出格子、
 * 出重影，本质不是 blur。现在改成真正的 GPU 管线：
 *
 *   1. capture()  把整屏拷进一张纹理（每帧一次 glCopyTexSubImage2D）；
 *   2. blur()     半分辨率降采样 + 可分离高斯（水平一遍、垂直一遍，权重按 sigma 生成），
 *                 再线性升采样 —— 得到一张平滑、无格子的背景模糊图；
 *   3. drawPanel() 用一段 SDF 着色器画面板：圆角矩形距离场 → 法线 → 边缘折射
 *                 （沿法线把采样坐标往外推，越靠边推得越多：玻璃有厚度感）、
 *                 三通道错位（色散）、菲涅尔边缘高光、左上角高光；
 *   4. drawRound() 控件直接采样那张模糊图（不再自己叠 tap）。
 *
 * 编译失败 / 没有 FBO 支持时自动退回旧的取样实现，不会黑屏。
 */
public final class ClientGlass {
    /** GPU 管线不可用时的回退参数。 */
    private static final float TAPS_SPREAD = 12.0f;
    private static final float TAPS_COVERAGE = 0.55f;

    /** 高斯模糊半径（半分辨率下的像素）。 */
    private static final int BLUR_RADIUS = 14;
    // 1.0.2_18：原来是写死的 true —— 每个 GL 调用后都 glGetError（同步点，会打断
    // GPU 流水线），模糊不可用时还每帧 println。改成需要时用
    // -Dclient.glass.debug=true 打开。
    private static final boolean DEBUG = Boolean.getBoolean("client.glass.debug");

    private static int tex;
    private static int texW = -1;
    private static int texH = -1;
    private static boolean ready;

    /* ---------------- 高斯管线 ---------------- */
    private static int blurTexA;
    private static int blurTexB;
    private static int fbo;
    private static int blurW = -1;
    private static int blurH = -1;
    private static int blurProg = -1;
    private static int panelProg = -1;
    private static boolean gpuOk;
    private static boolean DBG_ONCE = true;
    private static boolean DBG_ONCE2 = true;
    public static boolean ENABLE_BLUR = false;
    private static boolean DRAW_BLUR = true;
    private static final boolean USE_VP = true;
    private static final boolean USE_PROG = true;
    private static final boolean USE_ORTHO = true;
    private static final boolean USE_FBO = true;
    private static final boolean USE_STATE = true;
    private static final boolean USE_TARGETS = true;

    public static boolean ENABLE_SHADER = true;
    private static void chk(String tag) {
        if (!DEBUG) return;
        int e = GL11.glGetError();
        if (e != 0) System.out.println("[glass] GL error 0x" + Integer.toHexString(e) + " after " + tag);
    }
    private static boolean gpuTried;
    private static boolean blurFresh;
    /** 复用给 glGetInteger 的直接缓冲，别每帧 allocateDirect(64)。 */
    private static final IntBuffer PROBE = ByteBuffer.allocateDirect(64)
            .order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();

    private ClientGlass() {
    }

    public static boolean ready() {
        return ready;
    }

    /** 本帧的模糊图是否可用（不可用时上层退回取样实现）。 */
    public static boolean blurred() {
        return gpuOk && blurFresh;
    }

    /* ------------------------------------------------------------------ */
    /* 抓屏                                                               */
    /* ------------------------------------------------------------------ */

    /** 把当前帧缓冲抓进纹理。必须在 canvas.begin() 之后调用。 */
    public static void capture(Md3Canvas c) {
        int w = Display.getWidth();
        int h = Display.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        int prevTex = 0;
        try {
            if (tex == 0) {
                tex = GL11.glGenTextures();
            }
            PROBE.clear();
            GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D, PROBE);
            prevTex = PROBE.get(0);
            GlStateManager.bindTexture(tex);
            if (w != texW || h != texH) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                texW = w;
                texH = h;
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            chk("copyTex");
            ready = true;
        } catch (Throwable ignored) {
            ready = false;
        } finally {
            // 关键：直接 glBindTexture 会和 GlStateManager 的缓存错位，
            // 之后画字体时以为已绑定、实际绑的是我们的抓屏纹理（文字变成白块）。
            if (prevTex != 0) {
                GlStateManager.bindTexture(prevTex);
            }
        }
        blurFresh = false;
        if (c != null) {
            c.invalidateState();
        }
    }

    /* ------------------------------------------------------------------ */
    /* 可分离高斯                                                          */
    /* ------------------------------------------------------------------ */

    /** 在 capture() 之后调用一次：把抓到的画面做成一张平滑模糊图。 */
    public static void blur() {
        blur(1);
    }

    /**
     * @param stages 叠几组「水平 + 垂直」，2 组等于 sigma ×√2，越透明给得越大。
     */
    public static void blur(int stages) {
        if (!ENABLE_BLUR) {
            blurFresh = false;
            return;
        }
        if (!ready) {
            blurFresh = false;
            return;
        }
        if (!gpuTried) {
            initGpu();
        }
        if (DEBUG && !gpuOk) {
            System.out.println("[glass] blur skipped (gpuOk=" + gpuOk + ")");
        }
        if (!gpuOk) {
            blurFresh = false;
            return;
        }
        int w = Math.max(1, texW / 2);
        int h = Math.max(1, texH / 2);
        int prevProg = 0;
        int prevFbo = 0;
        int prevTex = 0;
        boolean texOn = false;
        boolean blendOn = false;
        boolean ortho = false;
        int[] vp = null;
        try {
            if (USE_TARGETS) { ensureTargets(w, h); }

            prevProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            prevFbo = GL11.glGetInteger(GL30Compat.FBO_BINDING);
            IntBuffer viewport = PROBE;
            PROBE.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            int vx = viewport.get(0);
            int vy = viewport.get(1);
            int vw = viewport.get(2);
            int vh = viewport.get(3);
            vp = new int[]{vx, vy, vw, vh};

            texOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            blendOn = GL11.glIsEnabled(GL11.GL_BLEND);
            prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

            boolean doState = USE_STATE;
            boolean doVp = USE_VP;
            boolean doProg = USE_PROG;
            boolean doOrtho = USE_ORTHO;
            boolean doFbo = USE_FBO;

            if (doState) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glColor4f(1f, 1f, 1f, 1f);
            }

            if (doVp) {
                GL11.glViewport(0, 0, w, h);
            }
            if (doProg) {
                GL20.glUseProgram(blurProg);
            }
            if (doOrtho) {
                setOrtho(w, h);
                ortho = true;
            }

            int src = tex;
            int rounds = Math.max(1, Math.min(3, stages));
            for (int r = 0; r < rounds; r++) {
                // 水平 -> A
                if (doFbo) { GL30Compat.bindFbo(fbo, blurTexA); }
            chk("bindA");
                GL20.glUniform2f(GL20.glGetUniformLocation(blurProg, "dir"), 1f / w, 0f);
                if (DRAW_BLUR) drawBlitQuad(w, h, src);
                // 垂直 -> B
                if (doFbo) { GL30Compat.bindFbo(fbo, blurTexB); }
                GL20.glUniform2f(GL20.glGetUniformLocation(blurProg, "dir"), 0f, 1f / h);
                if (DRAW_BLUR) drawBlitQuad(w, h, blurTexA);
            chk("blurV");
                src = blurTexB;
            }

            chk("unbind");
            blurFresh = true;
        } catch (Throwable t) {
            blurFresh = false;
            if (DEBUG) {
                t.printStackTrace();
            }
        } finally {
            if (ortho) {
                restoreOrtho();
                chk("restoreOrtho");
            }
            try {
                GL30Compat.bindFbo(prevFbo, 0);
            } catch (Throwable ignored2) {
            }
            try {
                GL20.glUseProgram(prevProg);
                chk("restoreProg");
            } catch (Throwable ignored2) {
            }
            if (USE_VP && vp != null) {
                GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
            }
            chk("restoreViewport");
            if (USE_STATE) {
                GlStateManager.bindTexture(prevTex);
                if (!texOn) GL11.glDisable(GL11.GL_TEXTURE_2D);
                if (blendOn) GL11.glEnable(GL11.GL_BLEND);
            }
            chk("restoreTex");
        }
    }

    private static final FloatBuffer savedProj = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer savedModel = BufferUtils.createFloatBuffer(16);

    /** 直接覆盖矩阵：不 push/pop，避免和 GuiScreen 的矩阵栈错位。 */
    private static void setOrtho(float w, float h) {
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, savedProj);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, savedModel);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, w, h, 0.0D, -1.0D, 1.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
    }

    private static void restoreOrtho() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrix(savedProj);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadMatrix(savedModel);
    }

    /** 全屏 quad，uv 0..1（v 轴与纹理一致，不翻转）。 */
    private static void drawBlitQuad(float w, float h, int texture) {
        GlStateManager.bindTexture(texture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(0f, 0f);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(w, 0f);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(w, h);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(0f, h);
        GL11.glEnd();
    }

    /* ------------------------------------------------------------------ */
    /* 面板玻璃（SDF + 折射 + 边缘高光）                                    */
    /* ------------------------------------------------------------------ */

    /**
     * 画一块液态玻璃面板。
     *
     * @param strength  玻璃覆盖度（面板越透给得越大；<=0.02 时不画）
     * @param tint      玻璃本体 ARGB
     * @param refractPx 边缘折射位移（物理像素）
     * @param lift      边缘/菲涅尔高光强度（0~1）
     */
    public static void drawPanel(Md3Canvas c, float x, float y, float w, float h,
                                 float radius, float strength, int tint, float refractPx, float lift) {
        if (!ready || strength <= 0.02f || w <= 1f || h <= 1f) {
            return;
        }
        if (!ENABLE_SHADER || !blurred() || panelProg <= 0) {
            drawRound(c, x, y, w, h, radius, strength, tint, 0xFFFFFF,
                    2, TAPS_SPREAD, TAPS_COVERAGE);
            return;
        }
        int prevProg = 0;
        int prevTex = 0;
        boolean texOn = false;
        boolean blendOn = false;
        try {
            prevProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            texOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            blendOn = GL11.glIsEnabled(GL11.GL_BLEND);

            GL20.glUseProgram(panelProg);
            chk("useProg");
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            if (DEBUG && DBG_ONCE) {
                DBG_ONCE = false;
                System.out.println("[glass] panel rect=" + x + "," + y + "," + w + "," + h
                        + " view=" + texW + "x" + texH + " canvas=" + c.width + "x" + c.height
                        + " fboBound=" + GL11.glGetInteger(GL30Compat.FBO_BINDING));
            }
            GL20.glUniform2f(u("viewSize"), texW, texH);
            GL20.glUniform1f(u("guiScale"), c.width > 0 ? texW / (float) c.width : 1f);
            GL20.glUniform4f(u("rect"), x, y, w, h);
            GL20.glUniform1f(u("radius"), radius);
            GL20.glUniform1f(u("thickness"), Math.max(6f, Math.min(28f, Math.min(w, h) * 0.45f)));
            GL20.glUniform1f(u("refract"), refractPx);
            GL20.glUniform4f(u("tint"), Md3Colors.r(tint) / 255f, Md3Colors.g(tint) / 255f,
                    Md3Colors.b(tint) / 255f, Md3Colors.a(tint) / 255f);
            GL20.glUniform1f(u("alpha"), Math.min(1f, strength));
            GL20.glUniform1f(u("lift"), lift);
            GL20.glUniform1f(u("rimWidth"), Math.max(6f, radius * 0.9f));
            GL20.glUniform1i(u("blurTex"), 0);
            chk("uniforms");
            GlStateManager.bindTexture(blurTexB);
            chk("bindBlur");

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0f, 0f);
            GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(1f, 0f);
            GL11.glVertex2f(x + w, y);
            GL11.glTexCoord2f(1f, 1f);
            GL11.glVertex2f(x + w, y + h);
            GL11.glTexCoord2f(0f, 1f);
            GL11.glVertex2f(x, y + h);
            GL11.glEnd();
            chk("panelDraw");
            if (DEBUG && DBG_ONCE2) { DBG_ONCE2 = false; System.out.println("[glass] blend=" + GL11.glIsEnabled(GL11.GL_BLEND) + " tex2d=" + GL11.glIsEnabled(GL11.GL_TEXTURE_2D) + " prog=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)); }
        } catch (Throwable t) {
            if (DEBUG) t.printStackTrace();
        } finally {
            GL20.glUseProgram(prevProg);
            GlStateManager.bindTexture(prevTex);
            if (!texOn) GL11.glDisable(GL11.GL_TEXTURE_2D);
            if (!blendOn) GL11.glDisable(GL11.GL_BLEND);
            chk("panelRestore");
            if (c != null) {
                c.invalidateState();
            }
        }
    }

    private static int u(String name) {
        return GL20.glGetUniformLocation(panelProg, name);
    }

    /* ------------------------------------------------------------------ */
    /* 控件玻璃                                                            */
    /* ------------------------------------------------------------------ */

    public static void drawRound(Md3Canvas c, float x, float y, float w, float h,
                                 float radius, float strength, int tint, int tapRgb) {
        drawRound(c, x, y, w, h, radius, strength, tint, tapRgb, 0, TAPS_SPREAD, TAPS_COVERAGE);
    }

    /** @param passes 0 = 自动（有模糊图就单次采样，没有就叠多遍取样） */
    public static void drawRound(Md3Canvas c, float x, float y, float w, float h,
                                 float radius, float strength, int tint, int tapRgb,
                                 int passes, float spread, float coverage) {
        if (!ready || strength <= 0.02f || w <= 1f || h <= 1f) {
            return;
        }
        float s = Math.min(1f, strength);
        float r = Math.min(radius, Math.min(w, h) / 2f);

        int tintA = (int) (s * Md3Colors.a(tint));
        c.roundRect(x, y, w, h, radius, Md3Colors.alpha(tint, tintA));

        if (blurred()) {
            // 控件直接用面板那张模糊图：同一块玻璃不做二次模糊，只叠一层很淡的色。
            sampleBlurRound(c, x, y, w, h, r, Md3Colors.alpha(tapRgb, (int) (s * 255f * 0.35f)));
            return;
        }
        // 1.0.2_13：背景模糊交给 Blur 模组的 shader 管线，控件不再自己抓屏取样
        //（取样叠加会出格子/重影）。这里只补一层很淡的白，保证玻璃有「亮」的质感。
        c.roundRect(x, y, w, h, radius, Md3Colors.alpha(tapRgb, (int) (s * 26f)));
        if (coverage <= 0.01f) {
            return;
        }
        int grid = spread > 7f ? 5 : 3;
        int n = grid * grid;
        float perTap = 1f - (float) Math.pow(1f - Math.min(0.999f, coverage), 1f / n);
        int a = Math.max(1, (int) (s * 255f * perTap));
        int tap = Md3Colors.alpha(tapRgb, a);
        int layers = passes > 0 ? passes : 2;
        for (int p = 0; p < layers; p++) {
            float jitter = (p % 2 == 0) ? 0f : 0.5f;
            tapRound(c, x, y, w, h, r, tap, spread, grid, jitter);
        }
    }

    private static void sampleBlurRound(Md3Canvas c, float x, float y, float w, float h,
                                        float radius, int color) {
        float u0 = x / c.width;
        float u1 = (x + w) / c.width;
        float v0 = 1f - (y + h) / c.height;
        float v1 = 1f - y / c.height;
        c.imageRound(blurTexB, x, y, w, h, radius, u0, v0, u1, v1, color);
    }

    private static void tapRound(Md3Canvas c, float x, float y, float w, float h,
                                 float radius, int tap, float spread, int grid, float jitter) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        float u0 = x / c.width;
        float u1 = (x + w) / c.width;
        float v0 = 1f - (y + h) / c.height;
        float v1 = 1f - y / c.height;
        float du = spread / c.width;
        float dv = spread / c.height;
        float step = 2f / (grid - 1);
        for (int gy = 0; gy < grid; gy++) {
            for (int gx = 0; gx < grid; gx++) {
                float ox = ((gx * step - 1f) + jitter * step) * du;
                float oy = ((gy * step - 1f) + jitter * step) * dv;
                c.imageRound(tex, x, y, w, h, radius,
                        u0 + ox, v0 + oy, u1 + ox, v1 + oy, tap);
            }
        }
    }

    /** 整屏一层玻璃（接管原版页面时铺在背景上）。 */
    public static void drawFull(Md3Canvas c, float strength, int tint, int tapRgb) {
        drawRound(c, 0f, 0f, c.width, c.height, 0f, strength, tint, tapRgb);
    }

    /* ------------------------------------------------------------------ */
    /* GPU 初始化                                                          */
    /* ------------------------------------------------------------------ */

    private static void ensureTargets(int w, int h) {
        if (blurTexA == 0) {
            blurTexA = GL11.glGenTextures();
            blurTexB = GL11.glGenTextures();
        }
        if (w != blurW || h != blurH) {
            for (int t : new int[]{blurTexA, blurTexB}) {
                GlStateManager.bindTexture(t);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            }
            blurW = w;
            blurH = h;
        }
    }

    private static void initGpu() {
        gpuTried = true;
        try {
            if (!GL30Compat.available()) {
                gpuOk = false;
                return;
            }
            fbo = GL30Compat.genFbo();
            if (fbo == 0) {
                gpuOk = false;
                return;
            }
            blurProg = buildProgram(SIMPLE_VERT, blurFragment(BLUR_RADIUS));
            panelProg = buildProgram(SIMPLE_VERT, PANEL_FRAG);
            gpuOk = blurProg > 0 && panelProg > 0;
            if (DEBUG) {
                System.out.println("[glass] gpuOk=" + gpuOk + " blurProg=" + blurProg
                        + " panelProg=" + panelProg + " fbo=" + fbo);
                System.out.println("[glass] panel link=" + GL20.glGetProgrami(panelProg, GL20.GL_LINK_STATUS)
                        + " validate=" + GL20.glGetProgrami(panelProg, GL20.GL_VALIDATE_STATUS)
                        + " attached=" + GL20.glGetProgrami(panelProg, GL20.GL_ATTACHED_SHADERS));
                System.out.println("[glass] panelLog=" + GL20.glGetProgramInfoLog(panelProg, 4000));
                System.out.println("[glass] afterUseProgErr=" + Integer.toHexString(GL11.glGetError()));
            }
        } catch (Throwable ignored) {
            gpuOk = false;
        }
    }

    private static int buildProgram(String vert, String frag) {
        int v = compile(GL20.GL_VERTEX_SHADER, vert);
        int f = compile(GL20.GL_FRAGMENT_SHADER, frag);
        if (v == 0 || f == 0) {
            return -1;
        }
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        int status = GL20.glGetProgrami(p, GL20.GL_LINK_STATUS);
        if (status != GL11.GL_TRUE) {
            System.out.println("[glass] link failed: " + GL20.glGetProgramInfoLog(p, 2000));
        }
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        return status == GL11.GL_TRUE ? p : -1;
    }

    private static int compile(int type, String src) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            System.out.println("[glass] shader compile failed: " + GL20.glGetShaderInfoLog(id, 2000));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }

    /* ------------------------------------------------------------------ */
    /* shader 源码                                                         */
    /* ------------------------------------------------------------------ */

    private static final String SIMPLE_VERT =
            "#version 120\n"
                    + "varying vec2 vUV;\n"
                    + "void main() {\n"
                    + "  vUV = gl_MultiTexCoord0.xy;\n"
                    + "  gl_Position = ftransform();\n"
                    + "}\n";

    /** 可分离高斯：每遍 2*R+1 个采样，权重按 sigma=R/2.5 归一化。 */
    private static String blurFragment(int radius) {
        StringBuilder sb = new StringBuilder();
        sb.append("#version 120\n");
        sb.append("uniform sampler2D tex;\n");
        sb.append("uniform vec2 dir;\n");
        sb.append("varying vec2 vUV;\n");
        sb.append("void main() {\n");
        sb.append("  vec4 sum = texture2D(tex, vUV) * ").append(fmt(weight(0, radius))).append(";\n");
        for (int i = 1; i <= radius; i++) {
            String w = fmt(weight(i, radius));
            sb.append("  sum += texture2D(tex, vUV + dir * ").append(fmt((double) i)).append(") * ").append(w).append(";\n");
            sb.append("  sum += texture2D(tex, vUV - dir * ").append(fmt((double) i)).append(") * ").append(w).append(";\n");
        }
        sb.append("  gl_FragColor = sum;\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static double weight(int i, int radius) {
        double sigma = Math.max(0.5, radius / 2.5);
        double g = Math.exp(-(i * i) / (2.0 * sigma * sigma));
        double sum = 0.0;
        for (int k = -radius; k <= radius; k++) {
            sum += Math.exp(-(k * k) / (2.0 * sigma * sigma));
        }
        return g / sum;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.8f", v);
    }

    private static final String PANEL_FRAG =
            "#version 120\n"
                    + "uniform sampler2D blurTex;\n"
                    + "uniform vec2 viewSize;\n"
                    + "uniform float guiScale;\n"
                    + "uniform vec4 rect;\n"
                    + "uniform float radius;\n"
                    + "uniform float thickness;\n"
                    + "uniform float refract;\n"
                    + "uniform vec4 tint;\n"
                    + "uniform float alpha;\n"
                    + "uniform float lift;\n"
                    + "uniform float rimWidth;\n"
                    + "void main() {\n"
                    + "  vec2 fg = gl_FragCoord.xy;\n"
                    + "  vec2 g = vec2(fg.x, viewSize.y - fg.y) / guiScale;\n"
                    + "  vec2 hb = rect.zw * 0.5;\n"
                    + "  vec2 p = g - (rect.xy + hb);\n"
                    + "  vec2 q = abs(p) - hb + radius;\n"
                    + "  float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;\n"
                    + "  if (d > 1.5) discard;\n"
                    + "  vec2 n = vec2(0.0);\n"
                    + "  float ql = length(max(q, 0.0));\n"
                    + "  if (ql > 1e-5) n = normalize(max(q, 0.0)) * sign(p);\n"
                    + "  else if (abs(q.x) > abs(q.y)) n = vec2(sign(p.x), 0.0);\n"
                    + "  else n = vec2(0.0, sign(p.y));\n"
                    + "  float m = max(-d, 0.0);\n"
                    + "  float e = 0.0;\n"
                    + "  if (m < thickness) {\n"
                    + "    float xR = 1.0 - m / thickness;\n"
                    + "    float thetaI = asin(clamp(xR * xR, 0.0, 1.0));\n"
                    + "    float thetaT = asin(clamp(sin(thetaI) / 1.45, 0.0, 1.0));\n"
                    + "    e = -tan(thetaT - thetaI);\n"
                    + "  }\n"
                    + "  vec2 off = -n * e * refract / viewSize;\n"
                    + "  vec2 uv = fg / viewSize;\n"
                    + "  vec3 col;\n"
                    + "  col.r = texture2D(blurTex, uv + off * 0.985).r;\n"
                    + "  col.g = texture2D(blurTex, uv + off).g;\n"
                    + "  col.b = texture2D(blurTex, uv + off * 1.015).b;\n"
                    + "  col = mix(col, tint.rgb, tint.a);\n"
                    + "  float rim = exp(-m / max(rimWidth, 0.5));\n"
                    + "  col = mix(col, vec3(1.0), rim * lift);\n"
                    + "  vec2 lightDir = normalize(vec2(-0.45, 0.8));\n"
                    + "  float spec = pow(max(dot(n, lightDir), 0.0), 3.0);\n"
                    + "  col += vec3(1.0) * spec * rim * lift * 0.55;\n"
                    + "  float cover = 1.0 - smoothstep(-1.0, 1.0, d);\n"
                    + "  gl_FragColor = vec4(col, alpha * cover);\n"
                    + "}\n";
}
