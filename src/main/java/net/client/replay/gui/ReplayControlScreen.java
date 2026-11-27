package net.client.replay.gui;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Text;
import net.client.replay.ReplayClient;
import net.client.replay.config.ReplayConfig;
import net.client.replay.record.RecordingManager;
import net.client.ui.ClientTheme;
import net.client.ui.ClientUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * 游戏内“回放”页：查看录制状态、改录制/停止热键、直接开始或结束录制。
 * 从客户端设置的回放标签进入；主菜单下同一个标签会直接打开回放控制台。
 */
public class ReplayControlScreen extends Screen {
    private static final int RECORD_KEY = 1;
    private static final int STOP_KEY = 2;
    private static final int BACK = 5;

    private final Md3Canvas canvas = new Md3Canvas();
    private final Screen parent;
    private int capture;          // 0 无，1 录制键，2 停止键
    private String message = "";
    private long messageUntil;
    private float mouseX;
    private float mouseY;
    private boolean mouseDown;

    private float panelX;
    private float panelY;
    private float panelW;
    private float panelH;

    public ReplayControlScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.panelW = Math.min(360f, this.width - 24f);
        this.panelH = Math.min(268f, this.height - 24f);
        this.panelX = (this.width - this.panelW) / 2f;
        this.panelY = (this.height - this.panelH) / 2f;
    }

    private RecordingManager recording() {
        return ReplayClient.getRecordingManager();
    }

    private static String keyName(int code) {
        if (code < 0) return "未设置";
        String name = Keyboard.getKeyName(code);
        return name == null || name.isEmpty() ? ("键码 " + code) : name;
    }

    private void say(String text) {
        this.message = text;
        this.messageUntil = System.currentTimeMillis() + 2600L;
    }

    @Override
    public void render(int mx, int my, float tickDelta) {
        this.mouseX = mx;
        this.mouseY = my;
        Md3Theme t = ClientTheme.get();
        this.canvas.begin(this.width, this.height);
        ClientUi.backdrop(this.canvas, t, this.width, this.height, true);

        this.canvas.shadow(this.panelX, this.panelY, this.panelW, this.panelH, 20f, 2);
        this.canvas.roundRect(this.panelX, this.panelY, this.panelW, this.panelH, 20f,
                t.colors.surfaceContainerLow);
        this.canvas.strokeRoundRect(this.panelX, this.panelY, this.panelW, this.panelH, 20f, 1f,
                Md3Colors.alpha(t.colors.outlineVariant, 0.35f));

        float x = this.panelX + 14f;
        float w = this.panelW - 28f;
        float y = this.panelY + 14f;

        this.canvas.roundRect(x, y, 26f, 26f, 9f, t.colors.primaryContainer);
        this.canvas.icon("schedule", x + 5f, y + 5f, 16f, t.colors.onPrimaryContainer);
        Md3Text.leftMiddle(this.canvas, "回放", x + 36f, y + 13f,
                Md3Type.TITLE_SMALL, t.colors.onSurface);
        boolean active = this.recording() != null && this.recording().isActive();
        Md3Text.leftMiddle(this.canvas, active ? "录制中" : "空闲", x + 36f + 90f, y + 13f,
                Md3Type.BODY_SMALL, active ? t.colors.primary : t.colors.onSurfaceVariant);

        y += 40f;
        Md3Text.leftMiddle(this.canvas, "点一下热键行，再按下要绑定的键（Esc 取消）", x, y,
                Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);

        y += 22f;
        drawKeyRow(t, x, y, w, "开始录制", keyName(ReplayClient.getConfig().hotkeyRecord),
                this.capture == RECORD_KEY);
        y += 34f;
        drawKeyRow(t, x, y, w, "结束录制", keyName(ReplayClient.getConfig().hotkeyStop),
                this.capture == STOP_KEY);

        y += 44f;
        float gap = 8f;
        float bw = (w - gap * 2f) / 3f;
        ReplayUi.button(this.canvas, t, x, y, bw, 26f, "开始录制", "play_arrow",
                ReplayUi.Style.FILLED, !active && canRecord(), hit(x, y, bw, 26f), this.mouseDown);
        ReplayUi.button(this.canvas, t, x + bw + gap, y, bw, 26f, "结束录制", "square",
                ReplayUi.Style.DANGER, active, hit(x + bw + gap, y, bw, 26f), this.mouseDown);
        ReplayUi.button(this.canvas, t, x + (bw + gap) * 2f, y, bw, 26f, "返回", "arrow_back",
                ReplayUi.Style.TEXT, true, hit(x + (bw + gap) * 2f, y, bw, 26f), this.mouseDown);

        y += 36f;
        String status = this.recording() == null ? "回放子系统未加载"
                : (active ? this.recording().statusLine() : "进入世界后点“开始录制”，或直接按热键。");
        Md3Text.leftMiddle(this.canvas, ReplayUi.fit(this.canvas, status, Md3Type.BODY_SMALL, w),
                x, y, Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);

        if (!this.message.isEmpty() && System.currentTimeMillis() < this.messageUntil) {
            Md3Text.leftMiddle(this.canvas, ReplayUi.fit(this.canvas, this.message, Md3Type.BODY_SMALL, w),
                    x, y + 16f, Md3Type.BODY_SMALL, t.colors.primary);
        }
        this.canvas.end();
    }

    private void drawKeyRow(Md3Theme t, float x, float y, float w, String label, String key, boolean capturing) {
        int base = capturing ? t.colors.primaryContainer : t.colors.surfaceContainerHigh;
        this.canvas.roundRect(x, y, w, 26f, 9f, base);
        Md3Text.leftMiddle(this.canvas, label, x + 10f, y + 13f, Md3Type.BODY_MEDIUM,
                capturing ? t.colors.onPrimaryContainer : t.colors.onSurface);
        String value = capturing ? "按下按键…" : key;
        Md3Text.rightMiddle(this.canvas, value, x + w - 10f, y + 13f, Md3Type.LABEL_MEDIUM,
                capturing ? t.colors.onPrimaryContainer : t.colors.primary);
    }

    private boolean hit(float x, float y, float w, float h) {
        return ReplayUi.hit(this.mouseX, this.mouseY, x, y, w, h);
    }

    private boolean canRecord() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.world != null && client.player != null;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            super.mouseClicked(mx, my, button);
            return;
        }
        this.mouseDown = true;
        float x = this.panelX + 14f;
        float w = this.panelW - 28f;
        float keyY = this.panelY + 14f + 40f + 22f;
        if (ReplayUi.hit(mx, my, x, keyY, w, 26f)) {
            this.capture = RECORD_KEY;
            ClientUi.click();
            return;
        }
        if (ReplayUi.hit(mx, my, x, keyY + 34f, w, 26f)) {
            this.capture = STOP_KEY;
            ClientUi.click();
            return;
        }
        float btnY = keyY + 78f;
        float gap = 8f;
        float bw = (w - gap * 2f) / 3f;
        if (ReplayUi.hit(mx, my, x, btnY, bw, 26f)) {
            startRecording();
            return;
        }
        if (ReplayUi.hit(mx, my, x + bw + gap, btnY, bw, 26f)) {
            stopRecording();
            return;
        }
        if (ReplayUi.hit(mx, my, x + (bw + gap) * 2f, btnY, bw, 26f)) {
            this.close();
            return;
        }
        super.mouseClicked(mx, my, button);
    }

    private void startRecording() {
        if (this.recording() == null) {
            say("回放子系统未加载。");
            return;
        }
        if (this.recording().isActive()) {
            say("已经在录制。");
            return;
        }
        if (!canRecord()) {
            say("进入世界后才能录制。");
            return;
        }
        this.recording().start("");
        say("已开始录制，返回游戏后按 " + keyName(ReplayClient.getConfig().hotkeyStop) + " 结束。");
    }

    private void stopRecording() {
        if (this.recording() != null && this.recording().isActive()) {
            this.recording().stop();
            say("已结束录制。");
        } else {
            say("当前没有录制任务。");
        }
    }

    @Override
    protected void keyPressed(char ch, int code) {
        if (this.capture != 0) {
            if (code == Keyboard.KEY_ESCAPE) {
                this.capture = 0;
                say("已取消改键。");
                return;
            }
            ReplayConfig config = ReplayClient.getConfig();
            if (config == null) return;
            if (this.capture == RECORD_KEY) {
                config.hotkeyRecord = code;
                say("开始录制热键已设为 " + keyName(code) + "。");
            } else {
                config.hotkeyStop = code;
                say("结束录制热键已设为 " + keyName(code) + "。");
            }
            config.save();
            this.capture = 0;
            return;
        }
        if (code == Keyboard.KEY_ESCAPE) {
            this.close();
            return;
        }
        super.keyPressed(ch, code);
    }

    private void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.setScreen(this.parent);
        }
    }

    @Override
    public void removed() {
        this.capture = 0;
        // 从这里返回客户端设置/游戏时不能再 grab 鼠标，否则指针会消失。
        try {
            Mouse.setGrabbed(false);
        } catch (Throwable ignored) {
        }
    }
}
