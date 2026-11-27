package net.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

/**
 * 1.0.2_1：把标题界面那套「全景动画背景」抽出来，供接管原版界面时复用。
 *
 * 以前接管原版界面（uiOverride）后，ScreenMixin 直接把整屏刷成 surface 色，
 * 亮色主题下就是一块纯白、暗色主题下是一块纯黑，原版 Options / 资源包这些页面
 * 就变成一张白纸。这里照 1.8.9 原版 TitleScreen 的做法重画：
 * 6 张 panorama_*.png 拼成立方体 → 渲染到 256x256 的动态纹理 → 拉满全屏，
 * 多层叠加出那种缓慢旋转、微微景深的效果。
 */
public final class ClientPanorama {
    private static final Identifier[] FACES = new Identifier[]{
            new Identifier("textures/gui/title/background/panorama_0.png"),
            new Identifier("textures/gui/title/background/panorama_1.png"),
            new Identifier("textures/gui/title/background/panorama_2.png"),
            new Identifier("textures/gui/title/background/panorama_3.png"),
            new Identifier("textures/gui/title/background/panorama_4.png"),
            new Identifier("textures/gui/title/background/panorama_5.png"),
    };

    /** 1.0.2_6：离屏分辨率从 256 抬到 1024，叠加层数从 7 降到 2。
     *  原版那套 256 + 七层叠加是为低配机压成本，代价是背景糊成一团、像蒙了层滤镜；
     *  这里把像素还回去，背景就变成能看清的全景。 */
    private static final int SIZE = 1024;
    /** 只留一层叠加：原来 7 层是为了把 8×8 网格糊掉，现在网格已经去掉，多叠只会发灰。 */
    private static final int BLUR_PASSES = 3;

    private static NativeImageBackedTexture texture;
    private static Identifier textureId;
    private static boolean failed;
    private static float lastWidth = -1f;
    private static float lastHeight = -1f;

    private ClientPanorama() {
    }

    /** 按窗口尺寸与时间画一屏全景背景（GUI 坐标，单位是 Screen.width/height）。 */
    public static void render(MinecraftClient mc, int width, int height) {
        if (mc == null || width <= 0 || height <= 0 || failed) {
            return;
        }
        try {
            ensureTexture(mc);
            if (textureId == null) {
                return;
            }
            float ticks = (float) (System.currentTimeMillis() % 10000000L) / 50f;
            // 1.0.2_20：设置页现在在世界内也会铺这层全景，而窗口的深度缓冲里还留着
            // 上一帧世界的深度 —— 不关深度测试的话，天空盒会被地形挡住，全景纹理里
            // 混进场景，背景就变成一块块糊掉的地形。
            GlStateManager.disableDepthTest();
            mc.getFramebuffer().unbind();
            GlStateManager.viewport(0, 0, SIZE, SIZE);
            drawSkybox(mc, ticks);
            for (int i = 0; i < BLUR_PASSES; i++) {
                blurFace(mc, width, height);
            }
            mc.getFramebuffer().bind(true);
            GlStateManager.viewport(0, 0, mc.width, mc.height);
            drawScreenQuad(mc, width, height);
            GlStateManager.enableDepthTest();
        } catch (Throwable ignored) {
            failed = true;
        }
    }

    private static void ensureTexture(MinecraftClient mc) {
        if (textureId != null) {
            return;
        }
        texture = new NativeImageBackedTexture(SIZE, SIZE);
        textureId = mc.getTextureManager().registerDynamicTexture("amethyst_panorama", texture);
    }

    /** 6 面立方体铺满 256x256 的离屏视口（照搬原版 renderPanorama）。 */
    private static void drawSkybox(MinecraftClient mc, float ticks) {
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(120.0f, 1.0f, 0.05f, 10.0f);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.rotate(180f, 1f, 0f, 0f);
        GlStateManager.rotate(90f, 0f, 0f, 1f);
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        // 原版是 8×8 个错位叠加的格子（每个格子亮度递减），靠后面 7 层模糊把网格吃掉。
        // 只要一张干净的全景时，网格会变成明显的方块边界，所以这里只画 1 格。
        int grid = 1;
        for (int cell = 0; cell < grid * grid; cell++) {
            GlStateManager.pushMatrix();
            float ox = ((float) (cell % grid) / grid - 0.5f) / 64f;
            float oy = ((float) (cell / grid) / grid - 0.5f) / 64f;
            GlStateManager.translate(ox, oy, 0f);
            GlStateManager.rotate(MathHelper.sin(ticks / 400f) * 25f + 20f, 1f, 0f, 0f);
            GlStateManager.rotate(-ticks * 0.1f, 0f, 1f, 0f);
            for (int face = 0; face < 6; face++) {
                GlStateManager.pushMatrix();
                if (face == 1) {
                    GlStateManager.rotate(90f, 0f, 1f, 0f);
                }
                if (face == 2) {
                    GlStateManager.rotate(180f, 0f, 1f, 0f);
                }
                if (face == 3) {
                    GlStateManager.rotate(-90f, 0f, 1f, 0f);
                }
                if (face == 4) {
                    GlStateManager.rotate(90f, 1f, 0f, 0f);
                }
                if (face == 5) {
                    GlStateManager.rotate(-90f, 1f, 0f, 0f);
                }
                mc.getTextureManager().bindTexture(FACES[face]);
                buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                int shade = 255 / (cell + 1);
                float z = 0f;
                buf.vertex(-1.0, -1.0, 1.0).texture(0f, 0f).color(255, 255, 255, shade).next();
                buf.vertex(1.0, -1.0, 1.0).texture(1f, 0f).color(255, 255, 255, shade).next();
                buf.vertex(1.0, 1.0, 1.0).texture(1f, 1f).color(255, 255, 255, shade).next();
                buf.vertex(-1.0, 1.0, 1.0).texture(0f, 1f).color(255, 255, 255, shade).next();
                tess.draw();
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
            GlStateManager.colorMask(true, true, true, false);
        }

        buf.offset(0.0, 0.0, 0.0);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepthTest();
    }

    /** 把 256x256 的一部分叠回动态纹理，叠 7 次形成景深感（照搬原版 transformPanorama）。 */
    private static void blurFace(MinecraftClient mc, int width, int height) {
        mc.getTextureManager().bindTexture(textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, SIZE, SIZE);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.colorMask(true, true, true, false);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        GlStateManager.disableAlphaTest();
        int layers = 3;
        for (int i = 0; i < layers; i++) {
            float alpha = 1f / (i + 1);
            float shift = (i - layers / 2) / (float) SIZE;
            buf.vertex(width, height, 0f).texture(0f + shift, 1f).color(1f, 1f, 1f, alpha).next();
            buf.vertex(width, 0f, 0f).texture(1f + shift, 1f).color(1f, 1f, 1f, alpha).next();
            buf.vertex(0f, 0f, 0f).texture(1f + shift, 0f).color(1f, 1f, 1f, alpha).next();
            buf.vertex(0f, height, 0f).texture(0f + shift, 0f).color(1f, 1f, 1f, alpha).next();
        }
        tess.draw();
        GlStateManager.enableAlphaTest();
        GlStateManager.colorMask(true, true, true, true);
    }

    /** 把 256x256 贴图按屏幕比例拉满全屏。 */
    private static void drawScreenQuad(MinecraftClient mc, int width, int height) {
        float f = width > height ? 120f / width : 120f / height;
        float g = height * f / 256f;
        float h = width * f / 256f;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(0.0, height, 0f).texture(0.5f - g, 0.5f + h).color(1f, 1f, 1f, 1f).next();
        buf.vertex(width, height, 0f).texture(0.5f - g, 0.5f - h).color(1f, 1f, 1f, 1f).next();
        buf.vertex(width, 0.0, 0f).texture(0.5f + g, 0.5f - h).color(1f, 1f, 1f, 1f).next();
        buf.vertex(0.0, 0.0, 0f).texture(0.5f + g, 0.5f + h).color(1f, 1f, 1f, 1f).next();
        tess.draw();
    }
}
