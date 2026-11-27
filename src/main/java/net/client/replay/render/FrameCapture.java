package net.client.replay.render;

import net.client.replay.ReplayClient;
import net.client.mixin.ReplayGameRendererAccessor;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.GLX;

/**
 * Renders one replay frame and reads it back.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>离屏模式</b>（{@code GLX.supportsFbo()} 为真）：画进一张独立 Framebuffer，支持任意
 *       分辨率（4K 也没问题）。</li>
 *   <li><b>窗口模式</b>（没有 FBO，例如 GL4ES / 移动端转译层）：直接把这一帧画到窗口后备缓冲，
 *       再从窗口读回。分辨率被窗口尺寸限制，但没有 FBO 也能导出。</li>
 * </ul>
 */
public final class FrameCapture implements AutoCloseable {
    private static boolean windowModeAnnounced;
    private final net.minecraft.client.MinecraftClient client;
    private final int width;
    private final int height;
    private final net.minecraft.client.gl.Framebuffer framebuffer;
    private final ByteBuffer buffer;
    /** 1.0.2_18：HUD 用的 Window 每个导出任务只建一次。原来每帧 new 一个（要查 GLFW
     *  尺寸 / 缩放），4K 长片是纯粹的固定开销。导出分辨率在任务内不变，缓存是安全的。 */
    private net.minecraft.client.util.Window hudWindow;

    public FrameCapture(net.minecraft.client.MinecraftClient client, int width, int height) {
        this.client = client;
        this.width = width;
        this.height = height;
        if (com.mojang.blaze3d.platform.GLX.supportsFbo()) {
            this.framebuffer = new net.minecraft.client.gl.Framebuffer(width, height, true);
            this.framebuffer.clearColor[0] = 0.0F;
            this.framebuffer.clearColor[1] = 0.0F;
            this.framebuffer.clearColor[2] = 0.0F;
            this.framebuffer.clearColor[3] = 1.0F;
        } else {
            // 没有 FBO：不抛异常，退回窗口捕获。分辨率会被 RenderManager 预先把到窗口尺寸内。
            this.framebuffer = null;
            if (!windowModeAnnounced) {
                windowModeAnnounced = true;
                ReplayClient.LOGGER.info("[replay] 环境未提供 FBO，改用窗口捕获 {}x{}", width, height);
            }
        }
        this.buffer = BufferUtils.createByteBuffer(width * height * 4);
    }

    public void capture(float partialTicks) {
        capture(partialTicks, System.nanoTime());
    }

    /** @param chunkDeadline absolute nanoTime handed to GameRenderer for chunk upload work */
    public void capture(float partialTicks, long chunkDeadline) {
        int oldWidth = client.width;
        int oldHeight = client.height;
        float oldFov = client.options.fov;
        ReplayGameRendererAccessor renderer = (ReplayGameRendererAccessor) (Object) client.gameRenderer;
        boolean oldRenderHand = renderer.client$getRenderHand();
        boolean oldHudHidden = client.options != null && client.options.hudHidden;
        boolean oldScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        try {
            client.width = width;
            client.height = height;
            // 手和 HUD 都要进画面：F1 那种「隐藏手臂」不能带进导出。
            renderer.client$setRenderHand(true);
            if (client.options != null) {
                client.options.hudHidden = false;
            }
            if (oldScissor) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            if (framebuffer != null) {
                framebuffer.bind(true);
            }
            // 窗口模式：没有 FBO 时游戏一直画在默认帧缓冲上，这里不需要重新绑定。
            com.mojang.blaze3d.platform.GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
            com.mojang.blaze3d.platform.GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            com.mojang.blaze3d.platform.GlStateManager.viewport(0, 0, width, height);

            client.gameRenderer.renderWorld(partialTicks, chunkDeadline);

            // 物品栏 / 血量 / 准星等 HUD 不属于 renderWorld，要按导出分辨率单独画一次。
            if (client.inGameHud != null && (ReplayClient.getConfig() == null || ReplayClient.getConfig().showHud)) {
                // 用一个按导出分辨率构造的 Window，HUD 的 GUI 缩放才会跟着实际尺寸走，
                // 而不是拿屏幕的 GUI 尺寸拉伸。聊天框在回放导出里不想要，临时关掉。
                if (hudWindow == null) {
                    hudWindow = new net.minecraft.client.util.Window(client);
                }
                double guiW = hudWindow.getScaledWidth();
                double guiH = hudWindow.getScaledHeight();
                boolean oldChatVisible = net.client.ClientConfig.chatVisible;
                net.client.ClientConfig.chatVisible = false;
                com.mojang.blaze3d.platform.GlStateManager.matrixMode(GL11.GL_PROJECTION);
                com.mojang.blaze3d.platform.GlStateManager.pushMatrix();
                com.mojang.blaze3d.platform.GlStateManager.loadIdentity();
                GL11.glOrtho(0.0D, guiW, guiH, 0.0D, 1000.0D, 3000.0D);
                com.mojang.blaze3d.platform.GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                com.mojang.blaze3d.platform.GlStateManager.pushMatrix();
                com.mojang.blaze3d.platform.GlStateManager.loadIdentity();
                try {
                    client.inGameHud.render(partialTicks);
                } finally {
                    net.client.ClientConfig.chatVisible = oldChatVisible;
                    com.mojang.blaze3d.platform.GlStateManager.matrixMode(GL11.GL_PROJECTION);
                    com.mojang.blaze3d.platform.GlStateManager.popMatrix();
                    com.mojang.blaze3d.platform.GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                    com.mojang.blaze3d.platform.GlStateManager.popMatrix();
                    com.mojang.blaze3d.platform.GlStateManager.viewport(0, 0, width, height);
                }
            }

            if (framebuffer != null) {
                framebuffer.bind(false);
            }
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            buffer.clear();
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            buffer.rewind();
        } finally {
            renderer.client$setRenderHand(oldRenderHand);
            client.options.fov = oldFov;
            if (client.options != null) client.options.hudHidden = oldHudHidden;
            client.width = oldWidth;
            client.height = oldHeight;
            client.getFramebuffer().bind(true);
            com.mojang.blaze3d.platform.GlStateManager.viewport(0, 0, oldWidth, oldHeight);
            if (oldScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isWindowMode() {
        return framebuffer == null;
    }

    @Override
    public void close() {
        if (framebuffer != null) {
            framebuffer.delete();
        }
    }
}
