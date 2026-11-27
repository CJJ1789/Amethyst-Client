package net.client.replay.gui;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Progress;
import net.client.ui.md3.widget.Md3Text;
import net.client.replay.ReplayClient;
import net.client.replay.render.RenderJob;
import net.client.replay.util.ReplayPaths;
import net.client.ui.ClientTheme;
import net.client.ui.ClientUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

/**
 * 导出进度页，用 M3 卡片替换原来的原版控件 + 手绘像素光标。
 * 取消保留已渲染部分，关闭回到回放控制台。
 */
public class ReplayProgressScreen extends Screen {
    private final Md3Canvas canvas = new Md3Canvas();
    private final RenderJob job;

    private float cx;
    private float cy;
    private float cw;
    private float ch;
    private float btnCancelX;
    private float btnCloseX;
    private float btnY;
    private float btnW;

    private long lastNanos;
    private float dt;
    private float mouseX;
    private float mouseY;
    private boolean mouseDown;
    private int openFrames;
    private String message = "";

    public ReplayProgressScreen(RenderJob job) {
        this.job = job;
    }

    @Override
    public void init() {
        this.cw = Math.min(560f, this.width - 24f);
        this.ch = Math.min(330f, this.height - 24f);
        this.cx = (this.width - this.cw) / 2f;
        this.cy = (this.height - this.ch) / 2f;
        this.btnW = (this.cw - 44f) / 2f;
        this.btnCancelX = this.cx + 16f;
        this.btnCloseX = this.cx + 16f + this.btnW + 12f;
        this.btnY = this.cy + this.ch - 40f;
        this.lastNanos = 0L;
        this.openFrames = 0;
    }

    private boolean running() {
        return this.job != null && !this.job.isFinished() && !this.job.isCancelled()
                && ReplayClient.getRenderManager() != null
                && ReplayClient.getRenderManager().getJob() == this.job;
    }

    @Override
    public void render(int mx, int my, float tickDelta) {
        this.mouseX = mx;
        this.mouseY = my;
        long now = System.nanoTime();
        this.dt = this.lastNanos == 0L ? 0.05f : Math.min(0.05f, (now - this.lastNanos) / 1.0E9f);
        this.lastNanos = now;
        this.openFrames++;

        Md3Theme t = ClientTheme.get();
        this.canvas.begin(this.width, this.height);
        ClientUi.backdrop(this.canvas, t, this.width, this.height, MinecraftClient.getInstance().world != null);
        this.canvas.shadow(this.cx, this.cy, this.cw, this.ch, 24f, 3);
        this.canvas.roundRect(this.cx, this.cy, this.cw, this.ch, 24f, t.colors.surfaceContainerHigh);
        this.canvas.strokeRoundRect(this.cx, this.cy, this.cw, this.ch, 24f, 1f,
                Md3Colors.alpha(t.colors.outlineVariant, 0.4f));

        int percent = this.job == null ? 0 : this.job.getPercent();
        Md3Text.leftMiddle(this.canvas, "正在导出回放", this.cx + 18f, this.cy + 20f,
                Md3Type.TITLE_MEDIUM, t.colors.onSurface);
        String file = this.job == null ? "-" : this.job.getReplayFile().getName();
        Md3Text.leftMiddle(this.canvas, ReplayUi.fit(this.canvas, file, Md3Type.BODY_SMALL, this.cw - 36f),
                this.cx + 18f, this.cy + 38f, Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);

        float px = this.cx + 18f;
        float pw = this.cw - 36f;
        Md3Text.leftMiddle(this.canvas, percent + "%", px, this.cy + 64f,
                Md3Type.DISPLAY_SMALL, t.colors.onSurface);
        if (this.job != null) {
            Md3Text.rightMiddle(this.canvas,
                    String.format(Locale.ROOT, "%.1f 帧/秒", this.job.getActualFps()),
                    px + pw, this.cy + 64f, Md3Type.BODY_MEDIUM, t.colors.onSurfaceVariant);
        }
        Md3Progress.linear(this.canvas, px, this.cy + 84f, pw,
                t.colors.primary, t.colors.surfaceContainerHighest,
                percent / 100f, this.job == null, (float) (now / 1.0E9D));

        float rowY = this.cy + 100f;
        float rowH = 18f;
        if (this.job != null) {
            this.row(t, "帧", this.job.getFrameIndex() + " / " + this.job.getTotalFrames(), rowY);
            double eta = this.job.getEtaSeconds();
            this.row(t, "预计剩余", eta < 0.0D ? "计算中…"
                    : ReplayPaths.formatDuration(eta), rowY + rowH);
            this.row(t, "已用时", ReplayPaths.formatDuration(this.job.getElapsedMillis() / 1000.0D),
                    rowY + rowH * 2f);
            if (this.ch >= 300f) {
                this.row(t, "编码器", this.job.getEncoder().toUpperCase(Locale.ROOT), rowY + rowH * 3f);
                String out = this.job.getOutputDescription();
                if (out == null || out.isEmpty()) out = "等待初始化…";
                this.row(t, "输出", ReplayUi.fit(this.canvas, out, Md3Type.BODY_SMALL, pw - 60f),
                        rowY + rowH * 4f);
            }
        }

        String hint = this.hint();
        if (!hint.isEmpty()) {
            int color = this.job != null && !this.job.getLastError().isEmpty()
                    ? t.colors.error : t.colors.onSurfaceVariant;
            Md3Text.leftMiddle(this.canvas, ReplayUi.fit(this.canvas, hint, Md3Type.BODY_SMALL, pw),
                    px, this.cy + (this.ch >= 300f ? 196f : 160f), Md3Type.BODY_SMALL, color);
        }

        boolean active = this.running();
        String cancelLabel = active ? "取消并保留" : "已结束";
        ReplayUi.button(this.canvas, t, this.btnCancelX, this.btnY, this.btnW, 26f,
                cancelLabel, active ? "stop" : "check", ReplayUi.Style.TONAL, active,
                ReplayUi.hit(mx, my, this.btnCancelX, this.btnY, this.btnW, 26f), this.mouseDown);
        ReplayUi.button(this.canvas, t, this.btnCloseX, this.btnY, this.btnW, 26f,
                "返回控制台", "arrow_back", ReplayUi.Style.FILLED, !active,
                ReplayUi.hit(mx, my, this.btnCloseX, this.btnY, this.btnW, 26f), this.mouseDown);

        if (!this.message.isEmpty()) {
            Md3Text.leftMiddle(this.canvas, ReplayUi.fit(this.canvas, this.message, Md3Type.BODY_SMALL, pw),
                    px, this.cy + (this.ch >= 300f ? 210f : 172f), Md3Type.BODY_SMALL, t.colors.primary);
        }
        this.canvas.end();
    }

    private void row(Md3Theme t, String label, String value, float y) {
        Md3Text.leftMiddle(this.canvas, label, this.cx + 18f, y, Md3Type.BODY_SMALL,
                t.colors.onSurfaceVariant);
        Md3Text.rightMiddle(this.canvas, value, this.cx + this.cw - 18f, y, Md3Type.BODY_SMALL,
                t.colors.onSurface);
    }

    private String hint() {
        if (this.job == null) return "";
        if (!this.job.getLastError().isEmpty()) {
            String error = this.job.getLastError();
            if (error.contains("回放没有成功创建世界") || error.contains("回放世界加载失败")) {
                return "回放世界创建失败，请查看日志；与编码器无关。";
            }
            if ("png".equalsIgnoreCase(this.job.getEncoder())) {
                return "PNG 模式写入失败，请检查 exports 目录权限。";
            }
            return "导出失败：" + error;
        }
        if (this.job.isFinished()) {
            return this.job.isCancelled()
                    ? "已取消，已经渲染的部分保留在输出目录。"
                    : "导出完成，可以关闭此页面。";
        }
        return "导出期间请保持此页面打开；Esc 会取消并保留已渲染部分。";
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (this.openFrames < 3 || button != 0) return;
        this.mouseX = mx;
        this.mouseY = my;
        this.mouseDown = true;
        if (ReplayUi.hit(mx, my, this.btnCancelX, this.btnY, this.btnW, 26f) && this.running()) {
            ReplayClient.getRenderManager().stop();
            this.message = "已取消导出，已渲染部分会保留。";
            ClientUi.click();
            return;
        }
        if (ReplayUi.hit(mx, my, this.btnCloseX, this.btnY, this.btnW, 26f) && !this.running()) {
            this.closeToConsole();
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        this.mouseDown = false;
        super.mouseReleased(mx, my, button);
    }

    @Override
    protected void keyPressed(char ch, int code) {
        if (code != Keyboard.KEY_ESCAPE) return;
        if (this.running()) {
            ReplayClient.getRenderManager().stop();
            this.message = "已取消导出，已渲染部分会保留。";
            this.closeToConsole();
        } else {
            this.closeToConsole();
        }
    }

    private void closeToConsole() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.setScreen(new ReplayScreen());
    }

    @Override
    public void removed() {
        // 已经结束/取消的任务在这里释放回放世界；运行中不能 dispose。
        if (this.job != null && (this.job.isFinished() || this.job.isCancelled())) {
            this.job.dispose();
        }
        super.removed();
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
