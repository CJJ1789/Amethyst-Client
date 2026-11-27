package net.client.ui;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

/**
 * 1.0.2_10：FBO 的最小封装。
 * 1.8.9 跑在 OpenGL 2.1 起的环境里，FBO 可能是 core（GL 3.0+）也可能只有 EXT 版本，
 * 这里两条路径都认，缺哪个返回不可用，调用方自动退回取样实现。
 */
public final class GL30Compat {
    /** GL_FRAMEBUFFER_BINDING（core 与 EXT 同值 0x8CA6）。 */
    public static final int FBO_BINDING = 0x8CA6;
    private static Boolean core;
    private static Boolean ext;

    private GL30Compat() {
    }

    private static ContextCapabilities caps() {
        return GLContext.getCapabilities();
    }

    private static boolean useCore() {
        if (core == null) {
            try {
                core = caps().OpenGL30;
            } catch (Throwable t) {
                core = false;
            }
        }
        return core;
    }

    private static boolean useExt() {
        if (ext == null) {
            try {
                ext = !useCore() && caps().GL_EXT_framebuffer_object;
            } catch (Throwable t) {
                ext = false;
            }
        }
        return ext;
    }

    public static String status() {
        try {
            if (useCore()) {
                return "0x" + Integer.toHexString(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
            }
        } catch (Throwable ignored) {
        }
        return "n/a";
    }

    public static boolean available() {
        return useCore() || useExt();
    }

    public static int genFbo() {
        try {
            if (useCore()) {
                return GL30.glGenFramebuffers();
            }
            if (useExt()) {
                return EXTFramebufferObject.glGenFramebuffersEXT();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static void bindFbo(int fbo, int texture) {
        if (useCore()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            if (fbo != 0) {
                // 绑定到 FBO 后必须把绘制目标改成 COLOR_ATTACHMENT0，
                // 否则默认的 GL_BACK 在这个 FBO 上非法，所有绘制直接丢
                // （表现为 glGetError 反复 0x506 GL_INVALID_FRAMEBUFFER_OPERATION）。
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                if (texture != 0) {
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                            GL11.GL_TEXTURE_2D, texture, 0);
                }
            } else {
                GL11.glDrawBuffer(GL11.GL_BACK);
            }
        } else if (useExt()) {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, fbo);
            if (fbo != 0) {
                GL11.glDrawBuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT);
            } else {
                GL11.glDrawBuffer(GL11.GL_BACK);
            }
            if (fbo != 0 && texture != 0) {
                EXTFramebufferObject.glFramebufferTexture2DEXT(
                        EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                        EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT,
                        GL11.GL_TEXTURE_2D, texture, 0);
            }
        }
    }
}
