package net.client.replay.gui;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Text;
import net.client.ui.md3.widget.Md3TextField;
import net.client.replay.ReplayClient;
import net.client.replay.config.ReplayConfig;
import net.client.replay.render.PreviewJob;
import net.client.replay.render.RenderJob;
import net.client.replay.render.RenderManager;
import net.client.replay.util.ReplayPaths;
import net.client.ui.ClientScroll;
import net.client.ui.ClientTheme;
import net.client.ui.ClientUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.util.Locale;

/**
 * 回放控制台（主菜单）。
 *
 * <p>原版界面把回放列表写死成 4 个按钮、坐标全部按固定像素排，GUI 缩放一变就重叠。
 * 这里改成左右两栏的 M3 布局：左侧是可滚动的全部回放列表，右侧是可滚动的导出设置，
 * 小屏自动收窄列宽，列表和设置各自有滚动条，不再依赖原版 ButtonWidget/TextFieldWidget。</p>
 */
public class ReplayScreen extends Screen {
    private static final float ROW_H = 36f;

    private final Md3Canvas canvas = new Md3Canvas();
    private final ClientScroll listScroll = new ClientScroll();
    private final ClientScroll sideScroll = new ClientScroll();

    private File[] replays = new File[0];
    private int selected = -1;
    private String message = "";
    private long messageUntil;
    private File pendingDelete;
    private long pendingDeleteUntil;
    private long lastRowClickAt;
    private int lastRowClickIndex = -1;

    private long lastNanos;
    private float dt;
    private int openFrames;
    private float mouseX;
    private float mouseY;
    private boolean mouseDown;
    private boolean listBarDrag;
    private boolean sideBarDrag;

    private float panelX;
    private float panelY;
    private float panelW;
    private float panelH;
    private float headerH = 38f;
    private float listX;
    private float listY;
    private float listW;
    private float listH;
    private float rowsY;
    private float rowsH;
    private float sideX;
    private float sideY;
    private float sideW;
    private float sideH;
    private float sideContentH;

    private Md3TextField sampleField;
    private Md3TextField fpsField;
    private Md3TextField widthField;
    private Md3TextField heightField;
    private final Md3TextField[] fields = new Md3TextField[4];
    private int focusField = -1;

    private float rowSampleY;
    private float rowFpsY;
    private float rowWidthY;
    private float rowHeightY;
    private float rowEncoderY;
    private float rowAudioY;
    private float rowAutoY;

    private float actionY;
    private float btnPlayX;
    private float btnRenderX;
    private float btnDeleteX;
    private float actionW;

    public ReplayScreen() {
        this.replays = ReplayPaths.listReplays();
        this.selected = this.replays.length == 0 ? -1 : 0;
        for (int i = 0; i < this.fields.length; i++) {
            Md3TextField field = new Md3TextField();
            field.variant = Md3TextField.Variant.OUTLINED;
            field.label = "";
            field.supporting = "";
            field.maxLength = 12;
            this.fields[i] = field;
        }
        this.sampleField = this.fields[0];
        this.fpsField = this.fields[1];
        this.widthField = this.fields[2];
        this.heightField = this.fields[3];
        syncFieldsFromConfig();
    }

    private void syncFieldsFromConfig() {
        ReplayConfig config = ReplayClient.getConfig();
        if (config == null) return;
        this.sampleField.text = Integer.toString(config.sampleHz);
        this.fpsField.text = trim(config.defaultFps);
        this.widthField.text = Integer.toString(config.defaultWidth);
        this.heightField.text = Integer.toString(config.defaultHeight);
    }

    private static String trim(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public void init() {
        this.replays = ReplayPaths.listReplays();
        if (this.selected >= this.replays.length) this.selected = this.replays.length - 1;
        float margin = 6f;
        this.panelW = Math.min(980f, this.width - margin * 2f);
        this.panelH = Math.min(640f, this.height - margin * 2f);
        this.panelX = (this.width - this.panelW) / 2f;
        this.panelY = (this.height - this.panelH) / 2f;
        this.headerH = 34f;

        float inner = 8f;
        float contentY = this.panelY + this.headerH;
        float contentH = this.panelH - this.headerH - 26f;
        float gap = 8f;
        boolean two = this.panelW >= 430f;
        if (two) {
            this.listW = Math.round((this.panelW - inner * 3f - gap) * 0.54f);
        } else {
            this.listW = this.panelW - inner * 2f;
        }
        this.listX = this.panelX + inner;
        this.listY = contentY;
        this.sideX = this.listX;
        this.sideW = this.listW;
        if (two) {
            this.listH = contentH;
            this.sideW = this.panelW - inner * 3f - gap - this.listW;
            this.sideX = this.listX + this.listW + inner + gap;
            this.sideY = this.listY;
            this.sideH = this.listH;
        } else {
            // 单列：以前这里 sideX/listX 完全重合，导出设置整块盖在列表上，列表就"消失"了。
            // 改成上下堆叠，两块各自滚动。
            this.listH = Math.max(76f, Math.round(contentH * 0.46f));
            this.sideY = this.listY + this.listH + gap;
            this.sideH = Math.max(60f, contentH - this.listH - gap);
        }
        this.actionW = (this.listW - 16f) / 3f - 4f;
        this.actionY = this.listY + this.listH - 28f;
        this.btnPlayX = this.listX + 8f;
        this.btnRenderX = this.btnPlayX + this.actionW + 6f;
        this.btnDeleteX = this.btnRenderX + this.actionW + 6f;
        this.rowsY = this.listY + 24f;
        this.rowsH = Math.max(20f, this.actionY - 6f - this.rowsY);
        this.listScroll.measure(this.replays.length * ROW_H, this.rowsH);

        // 右侧设置列：行高按屏幕高度收紧，内容和滚动条都由 sideScroll 管理。
        float row = Math.min(30f, Math.max(24f, this.sideH / 11f));
        this.rowSampleY = this.sideY + 20f;
        this.rowFpsY = this.rowSampleY + row;
        this.rowWidthY = this.rowFpsY + row;
        this.rowHeightY = this.rowWidthY + row;
        this.rowEncoderY = this.rowHeightY + row + 2f;
        this.rowAudioY = this.rowEncoderY + row;
        this.rowAutoY = this.rowAudioY + row;
        this.sideContentH = this.rowAutoY + row + 28f - this.sideY;
        this.sideScroll.measure(this.sideContentH, this.sideH - 6f);

        float fieldW = Math.min(86f, Math.max(48f, this.sideW * 0.40f));
        for (int i = 0; i < this.fields.length; i++) {
            this.fields[i].setBounds(0f, 0f, fieldW, 22f);
        }
        this.lastNanos = 0L;
        this.openFrames = 0;
        this.mouseDown = false;
        this.listBarDrag = false;
        this.sideBarDrag = false;
    }

    private float[] rowYs() {
        return new float[]{this.rowSampleY, this.rowFpsY, this.rowWidthY,
                this.rowHeightY};
    }

    @Override
    public void render(int mx, int my, float tickDelta) {
        this.mouseX = mx;
        this.mouseY = my;
        long now = System.nanoTime();
        this.dt = this.lastNanos == 0L ? 0.05f : Math.min(0.05f, (now - this.lastNanos) / 1.0E9f);
        this.lastNanos = now;
        this.openFrames++;
        this.listScroll.update(this.dt);
        this.sideScroll.update(this.dt);

        Md3Theme t = ClientTheme.get();
        this.canvas.begin(this.width, this.height);
        ClientUi.backdrop(this.canvas, t, this.width, this.height, false);
        this.drawPanel(t);
        this.drawHeader(t);
        this.drawList(t);
        this.drawSide(t);
        this.drawFooter(t);
        this.canvas.end();
    }

    private void drawPanel(Md3Theme t) {
        this.canvas.shadow(this.panelX, this.panelY, this.panelW, this.panelH, 20f, 2);
        this.canvas.roundRect(this.panelX, this.panelY, this.panelW, this.panelH, 20f,
                t.colors.surfaceContainerLow);
        this.canvas.strokeRoundRect(this.panelX, this.panelY, this.panelW, this.panelH, 20f, 1f,
                Md3Colors.alpha(t.colors.outlineVariant, 0.4f));
    }

    private void drawHeader(Md3Theme t) {
        float iconX = this.panelX + 10f;
        float iconY = this.panelY + 8f;
        this.canvas.roundRect(iconX, iconY, 20f, 20f, 7f, t.colors.primaryContainer);
        this.canvas.icon("animation", iconX + 3f, iconY + 3f, 14f, t.colors.onPrimaryContainer);
        Md3Text.leftMiddle(this.canvas, "回放与录制", iconX + 28f, this.panelY + this.headerH / 2f,
                Md3Type.TITLE_SMALL, t.colors.onSurface);

        String sub = this.replays.length + " 个回放 · " + ReplayPaths.formatSize(totalBytes());
        float subX = iconX + 28f + this.canvas.textWidth("回放与录制", Md3Type.TITLE_SMALL) + 10f;
        if (subX + 60f < this.panelX + this.panelW - 80f) {
            Md3Text.leftMiddle(this.canvas, ReplayUi.fit(this.canvas, sub, Md3Type.BODY_SMALL,
                    this.panelX + this.panelW - 70f - subX), subX,
                    this.panelY + this.headerH / 2f, Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);
        }

        float closeS = 26f;
        float cx = this.panelX + this.panelW - 10f - closeS;
        float cy = this.panelY + (this.headerH - closeS) / 2f;
        ReplayUi.button(this.canvas, t, cx, cy, closeS, closeS, "", "close",
                ReplayUi.Style.ICON, true,
                ReplayUi.hit(this.mouseX, this.mouseY, cx, cy, closeS, closeS), this.mouseDown);
        float rx = cx - 4f - closeS;
        ReplayUi.button(this.canvas, t, rx, cy, closeS, closeS, "", "refresh",
                ReplayUi.Style.ICON, true,
                ReplayUi.hit(this.mouseX, this.mouseY, rx, cy, closeS, closeS), this.mouseDown);
    }

    private long totalBytes() {
        long total = 0L;
        for (File file : this.replays) total += file.length();
        return total;
    }

    private void drawList(Md3Theme t) {
        ReplayUi.card(this.canvas, t, this.listX, this.listY, this.listW, this.listH);
        Md3Text.leftMiddle(this.canvas, "回放文件", this.listX + 10f, this.listY + 12f,
                Md3Type.TITLE_SMALL, t.colors.onSurface);
        Md3Text.rightMiddle(this.canvas, "双击行可直接剪辑", this.listX + this.listW - 26f,
                this.listY + 12f, Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);

        float startY = this.rowsY;
        float endY = this.rowsY + this.rowsH;
        this.canvas.pushClip(this.listX + 4f, startY, this.listW - 12f, this.rowsH);
        if (this.replays.length == 0) {
            // 空列表时列表区可能只有十几像素高，固定写在 startY+20 会被裁剪/压到
            // 下面的按钮行上；改成按可视高度居中，并把文案缩到一行能放下的长度。
            float emptyY = startY + Math.max(10f, this.rowsH * 0.5f);
            Md3Text.leftMiddle(this.canvas, "还没有回放，按 " + ReplayClient.recordKeyName() + " 开始录制",
                    this.listX + 10f, emptyY, Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);
        } else {
            float y = startY - this.listScroll.value;
            for (int i = 0; i < this.replays.length; i++) {
                File file = this.replays[i];
                boolean sel = i == this.selected;
                boolean hover = ReplayUi.hit(this.mouseX, this.mouseY,
                        this.listX + 4f, y + 1f, this.listW - 16f, ROW_H - 2f);
                if (sel) {
                    this.canvas.roundRect(this.listX + 4f, y + 1f, this.listW - 16f, ROW_H - 2f, 8f,
                            t.colors.primaryContainer);
                } else if (hover) {
                    this.canvas.roundRect(this.listX + 4f, y + 1f, this.listW - 16f, ROW_H - 2f, 8f,
                            t.colors.surfaceContainerHighest);
                }
                int titleColor = sel ? t.colors.onPrimaryContainer : t.colors.onSurface;
                int metaColor = sel ? t.colors.onPrimaryContainer : t.colors.onSurfaceVariant;
                String name = file.getName().substring(0, Math.max(0, file.getName().length() - 4));
                Md3Text.leftMiddle(this.canvas, ReplayUi.fit(this.canvas, name, Md3Type.BODY_MEDIUM,
                        this.listW - 52f), this.listX + 10f, y + 11f, Md3Type.BODY_MEDIUM, titleColor);
                Md3Text.leftMiddle(this.canvas,
                        ReplayPaths.formatTime(file.lastModified()) + " · " + ReplayPaths.formatSize(file.length()),
                        this.listX + 10f, y + 25f, Md3Type.BODY_SMALL,
                        Md3Colors.alpha(metaColor, 0.8f));
                y += ROW_H;
            }
        }
        this.canvas.popClip();
        this.listScroll.renderBar(this.canvas, t, this.listX + this.listW - 9f, startY, 4f);

        String playLabel = "播放 / 剪辑";
        ReplayUi.button(this.canvas, t, this.btnPlayX, this.actionY, this.actionW, 22f,
                playLabel, "play_arrow", ReplayUi.Style.FILLED, this.selected >= 0,
                ReplayUi.hit(this.mouseX, this.mouseY, this.btnPlayX, this.actionY, this.actionW, 22f),
                this.mouseDown);
        boolean renderBusy = ReplayClient.getRenderManager() != null
                && ReplayClient.getRenderManager().isActive();
        ReplayUi.button(this.canvas, t, this.btnRenderX, this.actionY, this.actionW, 22f,
                "渲染", "download", ReplayUi.Style.TONAL, this.selected >= 0 && !renderBusy,
                ReplayUi.hit(this.mouseX, this.mouseY, this.btnRenderX, this.actionY, this.actionW, 22f),
                this.mouseDown);
        boolean armed = this.pendingDelete != null && System.currentTimeMillis() < this.pendingDeleteUntil
                && this.selected >= 0 && this.replays[this.selected].equals(this.pendingDelete);
        ReplayUi.button(this.canvas, t, this.btnDeleteX, this.actionY, this.actionW, 22f,
                armed ? "确认删除" : "删除", "delete", ReplayUi.Style.DANGER, this.selected >= 0,
                ReplayUi.hit(this.mouseX, this.mouseY, this.btnDeleteX, this.actionY, this.actionW, 22f),
                this.mouseDown);
    }

    private void drawSide(Md3Theme t) {
        ReplayUi.card(this.canvas, t, this.sideX, this.sideY, this.sideW, this.sideH);
        this.canvas.pushClip(this.sideX + 4f, this.sideY + 4f, this.sideW - 8f, this.sideH - 8f);
        float off = -this.sideScroll.value;
        float x = this.sideX + 10f;
        float w = this.sideW - 24f;

        Md3Text.leftMiddle(this.canvas, "录制与导出", x, this.sideY + 12f + off,
                Md3Type.TITLE_SMALL, t.colors.onSurface);

        float[] ys = rowYs();
        String[] labels = {"采样率 Hz", "帧率", "宽度", "高度"};
        for (int i = 0; i < 4; i++) {
            float y = ys[i] + off;
            Md3Text.leftMiddle(this.canvas, labels[i], x, y + 11f, Md3Type.BODY_SMALL,
                    t.colors.onSurfaceVariant);
            float fw = Math.min(86f, Math.max(48f, this.sideW * 0.40f));
            Md3TextField field = this.fields[i];
            field.x = x + w - fw;
            field.y = y;
            field.w = fw;
            field.h = 22f;
            field.focused = this.focusField == i;
            field.render(this.canvas, t, this.mouseX, this.mouseY, false, this.dt);
        }

        float encY = this.rowEncoderY + off;
        Md3Text.leftMiddle(this.canvas, "编码器", x, encY + 11f, Md3Type.BODY_SMALL,
                t.colors.onSurfaceVariant);
        String enc = ReplayClient.getConfig() == null ? "ffmpeg" : ReplayClient.getConfig().encoder;
        float chipW = Math.min(92f, Math.max(64f, this.sideW * 0.42f));
        ReplayUi.button(this.canvas, t, x + w - chipW, encY, chipW, 22f, enc.toUpperCase(Locale.ROOT),
                "animation", ReplayUi.Style.OUTLINED, true,
                ReplayUi.hit(this.mouseX, this.mouseY, x + w - chipW, encY, chipW, 22f), this.mouseDown);

        float audioY = this.rowAudioY + off;
        ReplayUi.switchRow(this.canvas, t, x, audioY, w, 24f, "录制游戏内声音",
                ReplayClient.getConfig() != null && ReplayClient.getConfig().recordGameAudio, true);
        float autoY = this.rowAutoY + off;
        ReplayUi.switchRow(this.canvas, t, x, autoY, w, 24f, "进世界自动录制",
                ReplayClient.getConfig() != null && ReplayClient.getConfig().autoRecord, true);

        this.canvas.popClip();
        this.sideScroll.renderBar(this.canvas, t, this.sideX + this.sideW - 9f, this.sideY + 4f, 4f);
    }

    private void drawFooter(Md3Theme t) {
        float y = this.panelY + this.panelH - 20f;
        String status = this.statusText();
        Md3Text.leftMiddle(this.canvas, ReplayUi.fit(this.canvas, status, Md3Type.BODY_SMALL,
                this.panelW - 24f), this.panelX + 12f, y, Md3Type.BODY_SMALL,
                this.message.isEmpty() ? t.colors.onSurfaceVariant : t.colors.primary);

        RenderJob job = ReplayClient.getRenderManager() == null ? null : ReplayClient.getRenderManager().getJob();
        if (job != null) {
            float barW = Math.min(160f, this.panelW * 0.4f);
            float bx = this.panelX + this.panelW - 12f - barW;
            this.canvas.roundRect(bx, y - 2f, barW, 4f, 2f, t.colors.surfaceContainerHighest);
            this.canvas.roundRect(bx, y - 2f, barW * Math.max(0f, Math.min(1f, job.getPercent() / 100f)),
                    4f, 2f, t.colors.primary);
        }
    }

    private String statusText() {
        long now = System.currentTimeMillis();
        if (!this.message.isEmpty() && now < this.messageUntil) return this.message;
        if (ReplayClient.getRecordingManager() != null && ReplayClient.getRecordingManager().isActive()) {
            return "录制中：" + ReplayClient.getRecordingManager().statusLine();
        }
        RenderManager manager = ReplayClient.getRenderManager();
        if (manager != null && manager.isActive()) {
            RenderJob job = manager.getJob();
            if (job != null) {
                return "导出 " + job.getPercent() + "% · " + job.getOutputDescription();
            }
            return "回放预览进行中";
        }
        if (this.replays.length == 0 || this.selected < 0) {
            return "回放目录：" + ReplayPaths.replaysDir().getAbsolutePath();
        }
        return "选中 " + this.replays[this.selected].getName()
                + " · 回放目录 " + ReplayPaths.replaysDir().getAbsolutePath();
    }

    private void say(String text) {
        this.message = text;
        this.messageUntil = System.currentTimeMillis() + 2500L;
    }

    @Override
    public void handleMouse() {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            float delta = -Integer.signum(wheel) * 40f;
            if (ReplayUi.hit(this.mouseX, this.mouseY, this.listX, this.rowsY, this.listW, this.rowsH)) {
                this.listScroll.scrollBy(delta);
            } else if (ReplayUi.hit(this.mouseX, this.mouseY, this.sideX, this.sideY, this.sideW, this.sideH)) {
                this.sideScroll.scrollBy(delta);
            }
        }
        super.handleMouse();
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (this.openFrames < 3 || button != 0) {
            this.mouseX = mx;
            this.mouseY = my;
            return;
        }
        this.mouseX = mx;
        this.mouseY = my;
        this.mouseDown = true;

        float closeS = 26f;
        float cx = this.panelX + this.panelW - 10f - closeS;
        float cy = this.panelY + (this.headerH - closeS) / 2f;
        if (ReplayUi.hit(mx, my, cx, cy, closeS, closeS)) {
            this.close();
            return;
        }
        float rx = cx - 4f - closeS;
        if (ReplayUi.hit(mx, my, rx, cy, closeS, closeS)) {
            this.refresh();
            this.say("已刷新回放列表。");
            ClientUi.click();
            return;
        }

        // 列表/设置滚动条
        if (this.listScroll.press(mx, my, this.listX + this.listW - 9f, this.rowsY, 4f, 8f)) {
            this.listBarDrag = true;
            return;
        }
        if (this.sideScroll.press(mx, my, this.sideX + this.sideW - 9f, this.sideY + 4f, 4f, 8f)) {
            this.sideBarDrag = true;
            return;
        }

        // 回放行
        if (ReplayUi.hit(mx, my, this.listX + 4f, this.rowsY, this.listW - 14f, this.rowsH)) {
            int index = (int) Math.floor((my - this.rowsY + this.listScroll.value) / ROW_H);
            if (index < 0 || index >= this.replays.length) index = -1;
            if (index >= 0) {
                long now = System.currentTimeMillis();
                if (index == this.lastRowClickIndex && now - this.lastRowClickAt < 450L) {
                    this.selected = index;
                    this.playSelected();
                } else {
                    this.selected = index;
                    this.say("已选择：" + this.replays[index].getName());
                }
                this.lastRowClickIndex = index;
                this.lastRowClickAt = now;
                ClientUi.click();
            }
            return;
        }

        if (ReplayUi.hit(mx, my, this.btnPlayX, this.actionY, this.actionW, 22f)) {
            this.playSelected();
            return;
        }
        if (ReplayUi.hit(mx, my, this.btnRenderX, this.actionY, this.actionW, 22f)) {
            this.renderSelected();
            return;
        }
        if (ReplayUi.hit(mx, my, this.btnDeleteX, this.actionY, this.actionW, 22f)) {
            this.deleteSelected();
            return;
        }

        // 右侧设置（滚动偏移参与命中）
        float off = -this.sideScroll.value;
        float x = this.sideX + 10f;
        float w = this.sideW - 24f;
        float[] ys = rowYs();
        for (int i = 0; i < 4; i++) {
            float fw = Math.min(86f, Math.max(48f, this.sideW * 0.40f));
            if (ReplayUi.hit(mx, my, x + w - fw, ys[i] + off, fw, 22f)) {
                this.setFocus(i);
                return;
            }
        }
        this.setFocus(-1);

        float chipW = Math.min(92f, Math.max(64f, this.sideW * 0.42f));
        if (ReplayUi.hit(mx, my, x + w - chipW, this.rowEncoderY + off, chipW, 22f)) {
            ReplayConfig config = ReplayClient.getConfig();
            config.encoder = ReplayConfig.nextEncoder(config.encoder);
            config.save();
            this.say("编码器已切换为 " + config.encoder.toUpperCase(Locale.ROOT));
            ClientUi.click();
            return;
        }
        if (ReplayUi.hit(mx, my, x, this.rowAudioY + off, w, 24f)) {
            ReplayConfig config = ReplayClient.getConfig();
            config.recordGameAudio = !config.recordGameAudio;
            config.save();
            this.say(config.recordGameAudio ? "下次录制会重建游戏内声音。" : "已关闭游戏内声音录制。");
            ClientUi.click();
            return;
        }
        if (ReplayUi.hit(mx, my, x, this.rowAutoY + off, w, 24f)) {
            ReplayConfig config = ReplayClient.getConfig();
            config.autoRecord = !config.autoRecord;
            config.save();
            this.say(config.autoRecord ? "进入世界后会自动开始录制。" : "已关闭自动录制。");
            ClientUi.click();
            return;
        }
    }

    private void setFocus(int index) {
        if (this.focusField >= 0 && index != this.focusField) {
            // 「保存导出设置」按钮已去掉，输入框失去焦点/回车即自动写回配置。
            this.applySettings(true);
        }
        this.focusField = index;
        for (int i = 0; i < this.fields.length; i++) {
            this.fields[i].focused = i == index;
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        this.mouseDown = false;
        this.listBarDrag = false;
        this.sideBarDrag = false;
        this.listScroll.release();
        this.sideScroll.release();
        super.mouseReleased(mx, my, button);
    }

    @Override
    protected void mouseDragged(int mx, int my, int button, long clickedAt) {
        if (this.listBarDrag) {
            this.listScroll.drag(my, this.rowsY);
            return;
        }
        if (this.sideBarDrag) {
            this.sideScroll.drag(my, this.sideY + 4f);
            return;
        }
        super.mouseDragged(mx, my, button, clickedAt);
    }

    @Override
    protected void keyPressed(char ch, int code) {
        if (this.openFrames < 3) return;
        if (code == Keyboard.KEY_ESCAPE) {
            if (this.focusField >= 0) {
                this.setFocus(-1);
            } else {
                this.close();
            }
            return;
        }
        if (this.focusField >= 0) {
            Md3TextField field = this.fields[this.focusField];
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                this.setFocus(-1);
                return;
            }
            if (code == Keyboard.KEY_BACK) {
                field.backspace();
                return;
            }
            if (ch >= '0' && ch <= '9' || ch == '.' || ch == '-') {
                field.typeChar(ch);
                return;
            }
            return;
        }
        if (code == Keyboard.KEY_RETURN && this.selected >= 0) {
            this.playSelected();
        }
    }

    @Override
    public void removed() {
        if (this.focusField >= 0) {
            this.applySettings(false);
        }
        ReplayConfig config = ReplayClient.getConfig();
        if (config != null) config.save();
        super.removed();
    }

    private void refresh() {
        this.replays = ReplayPaths.listReplays();
        if (this.selected >= this.replays.length) this.selected = this.replays.length - 1;
        if (this.selected < 0 && this.replays.length > 0) this.selected = 0;
        this.listScroll.measure(this.replays.length * ROW_H, this.rowsH);
    }

    private void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.setScreen(null);
    }

    private File selectedFile() {
        return this.selected >= 0 && this.selected < this.replays.length ? this.replays[this.selected] : null;
    }

    private void playSelected() {
        File file = this.selectedFile();
        if (file == null) {
            this.say("请先选择一个回放。");
            return;
        }
        RenderManager manager = ReplayClient.getRenderManager();
        if (manager != null && manager.isActive()) {
            this.say("已有回放 / 渲染任务在运行，请先停止。");
            return;
        }
        PreviewJob preview = ReplayClient.openPreview(file);
        if (preview == null) {
            this.say("无法启动回放预览，请查看日志。");
        }
    }

    private void renderSelected() {
        File file = this.selectedFile();
        if (file == null) {
            this.say("请先选择一个回放。");
            return;
        }
        RenderManager manager = ReplayClient.getRenderManager();
        if (manager != null && manager.isActive()) {
            this.say("已有回放 / 渲染任务在运行，请先停止。");
            return;
        }
        if (!this.applySettings(false)) return;
        ReplayConfig config = ReplayClient.getConfig();
        if (manager == null) return;
        if (ReplayFfmpegMissingScreen.needsPrompt()) {
            final File target = file;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                mc.setScreen(new ReplayFfmpegMissingScreen(new Runnable() {
                    @Override
                    public void run() {
                        RenderManager m = ReplayClient.getRenderManager();
                        ReplayConfig c = ReplayClient.getConfig();
                        if (m != null && c != null) {
                            m.start(target, c.defaultFps, c.defaultWidth, c.defaultHeight, c.encoder);
                        }
                    }
                }));
            }
            return;
        }
        // 列表页的「渲染」也用轨道文件里保存的片段范围，否则在剪辑台裁好的片段会被整段渲染。
        long trimStart = -1L;
        long trimEnd = -1L;
        try {
            java.io.File cameraFile = ReplayPaths.cameraFileFor(file);
            net.client.replay.session.CameraPath path = net.client.replay.session.CameraPath.load(cameraFile);
            if (path != null) {
                if (path.trimStartSeconds > 0.0D) trimStart = (long) (path.trimStartSeconds * 1.0e9D);
                if (path.trimEndSeconds > 0.0D) trimEnd = (long) (path.trimEndSeconds * 1.0e9D);
            }
        } catch (Throwable ignored) {
        }
        manager.start(file, config.defaultFps, config.defaultWidth, config.defaultHeight,
                config.encoder, trimStart, trimEnd);
    }

    private void deleteSelected() {
        File file = this.selectedFile();
        if (file == null) {
            this.say("请先选择一个回放。");
            return;
        }
        RenderManager manager = ReplayClient.getRenderManager();
        if (manager != null && manager.isActive()
                || ReplayClient.getRecordingManager() != null && ReplayClient.getRecordingManager().isActive()) {
            this.say("请先停止录制 / 回放 / 导出任务，再删除回放。");
            return;
        }
        long now = System.currentTimeMillis();
        if (this.pendingDelete == null || !this.pendingDelete.equals(file) || now > this.pendingDeleteUntil) {
            this.pendingDelete = file;
            this.pendingDeleteUntil = now + 5000L;
            this.say("再点一次“确认删除”才会永久删除：" + file.getName());
            return;
        }
        boolean ok = file.delete();
        File camera = ReplayPaths.cameraFileFor(file);
        if (ok && camera != null && camera.isFile()) camera.delete();
        this.pendingDelete = null;
        this.pendingDeleteUntil = 0L;
        this.refresh();
        this.say(ok ? "已删除回放：" + file.getName() : "删除失败，文件可能被占用。");
    }

    private void applySettings() {
        this.applySettings(true);
    }

    private boolean applySettings(boolean notify) {
        ReplayConfig config = ReplayClient.getConfig();
        if (config == null) return false;
        try {
            int sample = Integer.parseInt(this.sampleField.text.trim());
            double fps = Double.parseDouble(this.fpsField.text.trim());
            int width = Integer.parseInt(this.widthField.text.trim());
            int height = Integer.parseInt(this.heightField.text.trim());
            if (fps <= 0.0D || Double.isNaN(fps) || Double.isInfinite(fps)) {
                this.say("帧率必须是大于 0 的有限数字。");
                return false;
            }
            config.sampleHz = sample;
            config.defaultFps = fps;
            config.defaultWidth = width;
            config.defaultHeight = height;
            config.sanitize();
            config.save();
            this.syncFieldsFromConfig();
            if (notify) {
                this.say("设置已保存：" + config.defaultWidth + "x" + config.defaultHeight
                        + " · " + trim(config.defaultFps) + " fps · 采样 " + config.sampleHz + " Hz");
                ClientUi.click();
            }
            return true;
        } catch (Throwable t) {
            this.say("设置无效：" + (t.getMessage() == null ? "请检查数字格式" : t.getMessage()));
            return false;
        }
    }
}
