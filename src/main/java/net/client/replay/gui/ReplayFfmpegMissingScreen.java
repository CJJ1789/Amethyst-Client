package net.client.replay.gui;

import net.client.replay.ReplayClient;
import net.client.replay.config.ReplayConfig;
import net.client.replay.render.FfmpegLocator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * 没找到 ffmpeg 时先问一句，而不是闷头写一堆 PNG 帧。
 * 三个选择：改 PNG 帧、照旧渲染（无 ffmpeg 时输出纯 Java MJPEG AVI）、取消。
 */
public final class ReplayFfmpegMissingScreen extends Screen {
    private final Runnable onProceed;

    public ReplayFfmpegMissingScreen(Runnable onProceed) {
        this.onProceed = onProceed;
    }

    /** 当前配置的编码器是 ffmpeg，却找不到任何可执行的 ffmpeg 时返回 true。 */
    public static boolean needsPrompt() {
        ReplayConfig config = ReplayClient.getConfig();
        if (config == null || !"ffmpeg".equals(config.encoder)) return false;
        return FfmpegLocator.find() == null;
    }

    @Override
    public void init(MinecraftClient client, int width, int height) {
        super.init(client, width, height);
        this.buttons.clear();
        int bw = Math.min(240, (width - 40) / 2);
        int bh = 20;
        int cx = width / 2;
        int y = height / 2 + 16;
        this.buttons.add(new ButtonWidget(0, cx - bw - 4, y, bw, bh, "改用 PNG 帧"));
        this.buttons.add(new ButtonWidget(1, cx + 4, y, bw, bh, "继续渲染（无 ffmpeg 出 AVI）"));
        this.buttons.add(new ButtonWidget(2, cx - bw / 2, y + 26, bw, bh, "取消，停止渲染"));
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int cx = this.width / 2;
        this.drawCenteredString(this.textRenderer, "未找到可用的 ffmpeg", cx, this.height / 2 - 56, 0xFFFFFFFF);
        this.drawCenteredString(this.textRenderer, "无法直接合成 MP4。请选择处理方式：",
                cx, this.height / 2 - 38, 0xFFBDBDBD);
        this.drawCenteredString(this.textRenderer, "· 改用 PNG 帧：输出图片序列，可自行合成",
                cx, this.height / 2 - 20, 0xFF9E9E9E);
        this.drawCenteredString(this.textRenderer, "· 继续渲染：直接写纯 Java MJPEG 视频（.avi）",
                cx, this.height / 2 - 8, 0xFF9E9E9E);
        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        ReplayConfig config = ReplayClient.getConfig();
        if (config == null) {
            this.client.setScreen(null);
            return;
        }
        if (button.id == 0) {
            config.encoder = "png";
            config.save();
        } else if (button.id == 1) {
            config.encoder = "mjpeg";
            config.save();
        } else {
            this.client.setScreen(null);
            return;
        }
        this.client.setScreen(null);
        if (this.onProceed != null) this.onProceed.run();
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
