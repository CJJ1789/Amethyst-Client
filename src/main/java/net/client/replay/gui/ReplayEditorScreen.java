package net.client.replay.gui;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Text;
import net.client.ui.md3.widget.Md3TextField;
import net.client.replay.ReplayClient;
import net.client.replay.config.ReplayConfig;
import net.client.replay.render.PreviewJob;
import net.client.replay.session.CameraController;
import net.client.replay.session.CameraPath;
import net.client.replay.util.ReplayPaths;
import net.client.ui.ClientTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 回放播放 / 剪辑台。透明地叠在回放画面之上（shouldPauseGame=false），
 * T 呼出面板和鼠标，Esc 只收起面板不结束回放，F5 切视角，F1 切 HUD。
 *
 * <p>和原实现的区别：所有控件改成 M3 绘制并按屏幕尺寸重排，手机上不会再把
 * 方向键或数值框排到屏幕外；裁剪把手、播放头和关键帧标记共用一条时间轴。</p>
 */
public class ReplayEditorScreen extends Screen {
    private static final int PAUSE = 1;
    private static final int BACK = 2;
    private static final int FORWARD = 3;
    private static final int RENDER = 4;
    private static final int EXIT = 5;
    private static final int HIDE = 6;
    private static final int MODE_PLAYER = 10;
    private static final int MODE_FREE = 11;
    private static final int MODE_TARGET = 12;
    private static final int ADD_KEY = 20;
    private static final int CLEAR_KEY = 21;
    private static final int SAVE_KEY = 22;
    private static final int JUMP_START = 32;
    private static final int JUMP_END = 33;
    private static final int LOOK_LEFT = 40;
    private static final int APPLY_CAM = 50;

    private static final int F_TRIM_START = 0;
    private static final int F_TRIM_END = 1;
    private static final int F_CAM_X = 2;

    private final Md3Canvas canvas = new Md3Canvas();
    private final PreviewJob preview;
    private final List<Btn> buttons = new ArrayList<Btn>();
    private final Md3TextField[] fields = new Md3TextField[8];
    private final float[] fieldX = new float[8];
    private final float[] fieldY = new float[8];
    private final float[] fieldW = new float[8];
    private final float[] fieldH = new float[8];
    private boolean[] fieldVisible = new boolean[8];

    private boolean panelVisible;
    private boolean compact;
    private float mouseX;
    private float mouseY;
    private boolean mouseDown;
    private long lastNanos;
    private float dt;
    private int openFrames;
    /** 轨道里保存的起止/播放头是否已经回填过一次。 */
    private boolean trimRestored;
    private String message = "";
    private long messageUntil;

    private float headerH;
    private float timelineX;
    private float timelineY;
    private float timelineW;
    private float timelineH;
    private float leftX;
    private float leftY;
    private float leftW;
    private float leftH;
    private float rightX;
    private float rightY;
    private float rightW;
    private float rightH;

    private int focusField = -1;
    private int timelineDrag;
    private boolean draggingCamera;
    private boolean blankClick;
    private float dragX;
    private float dragY;
    private int targetIndex;
    private boolean lastF5;
    private long lastPerspectiveMs;
    private CameraPath.Keyframe pendingDelete;
    /** 轨道是否有未保存改动；退出 / 渲染前先询问。 */
    private boolean pathDirty;
    /** 退出 / 渲染前待确认的动作：0 无，1 退出，2 渲染片段。 */
    private int pendingLeave;
    private float dlgX;
    private float dlgY;
    private float dlgW;
    private float dlgH;

    public ReplayEditorScreen(PreviewJob preview) {
        this.preview = preview;
        for (int i = 0; i < this.fields.length; i++) {
            Md3TextField field = new Md3TextField();
            field.variant = Md3TextField.Variant.OUTLINED;
            field.label = "";
            field.supporting = "";
            field.maxLength = 10;
            this.fields[i] = field;
        }
        this.panelVisible = false;
        if (preview != null) preview.setLoop(true);
    }

    private CameraController camera() {
        return this.preview == null || this.preview.getSession() == null
                ? null : this.preview.getSession().getCameraController();
    }

    @Override
    public void init() {
        this.compact = this.height < 310f || this.width < 420f;
        this.headerH = this.compact ? 22f : 26f;
        this.timelineX = 8f;
        this.timelineY = this.compact ? 26f : 30f;
        this.timelineW = Math.max(40f, this.width - 16f);
        this.timelineH = 12f;

        float actionsY = this.timelineY + this.timelineH + 4f;
        float contentY = actionsY + 24f;
        float contentH = Math.max(80f, this.height - contentY - 18f);
        if (this.compact) {
            this.leftX = 6f;
            this.leftY = contentY;
            this.leftW = this.width - 12f;
            this.leftH = contentH;
            this.rightX = this.leftX;
            this.rightY = this.leftY;
            this.rightW = this.leftW;
            this.rightH = this.leftH;
            layoutCompact(actionsY);
        } else {
            this.leftX = 8f;
            this.rightX = this.width / 2f + 2f;
            this.leftY = contentY;
            this.rightY = contentY;
            this.leftW = this.width / 2f - 10f;
            this.rightW = this.width / 2f - 10f;
            this.leftH = contentH;
            this.rightH = contentH;
            layoutFull(actionsY);
        }
        this.lastNanos = 0L;
        this.openFrames = 0;
        this.focusField = -1;
        this.timelineDrag = 0;
        this.draggingCamera = false;
        this.setGrabbed(!this.panelVisible);
    }

    private static final class Btn {
        int id;
        float x;
        float y;
        float w;
        float h;
        String label;
        String icon;
        ReplayUi.Style style;
    }

    private void btn(int id, float x, float y, float w, float h, String label, String icon,
                     ReplayUi.Style style) {
        Btn b = new Btn();
        b.id = id;
        b.x = x;
        b.y = y;
        b.w = w;
        b.h = h;
        b.label = label;
        b.icon = icon;
        b.style = style;
        this.buttons.add(b);
    }

    private void fieldBox(int index, int kind, float x, float y, float w, float h) {
        this.fieldX[index] = x;
        this.fieldY[index] = y;
        this.fieldW[index] = w;
        this.fieldH[index] = h;
        this.fieldVisible[index] = true;
        this.fields[index].setBounds(x, y, w, h);
    }

    private void layoutFull(float actionsY) {
        float gap = 4f;
        float quarter = (this.width - 16f - gap * 4f) / 5f;
        this.btn(PAUSE, 8f, actionsY, quarter, 22f, "暂停", "pause", ReplayUi.Style.TONAL);
        this.btn(BACK, 8f + quarter + gap, actionsY, quarter, 22f, "-5 秒", "fast_rewind", ReplayUi.Style.TEXT);
        this.btn(FORWARD, 8f + (quarter + gap) * 2f, actionsY, quarter, 22f, "+5 秒", "fast_forward", ReplayUi.Style.TEXT);
        this.btn(RENDER, 8f + (quarter + gap) * 3f, actionsY, quarter, 22f, "渲染片段", "download", ReplayUi.Style.FILLED);
        this.btn(EXIT, 8f + (quarter + gap) * 4f, actionsY, quarter, 22f, "退出", "close", ReplayUi.Style.TONAL);

        // 所有控制按钮沉到底部，中间整块留作观察区；不再画卡片底板。
        float rowH = 22f;
        float rowGap = 10f;
        float margin = 8f;
        float w = this.width - margin * 2f;
        float keyY = this.height - margin - rowH;
        float modeY = keyY - rowH - rowGap;
        float trimY = modeY - rowH - rowGap;

        float fieldW = 74f;
        this.fieldBox(F_TRIM_START, 0, margin, trimY, fieldW, rowH);
        this.fieldBox(F_TRIM_END, 1, margin + fieldW + gap, trimY, fieldW, rowH);
        float bx = margin + fieldW * 2f + gap * 2f;
        float bw = (w - (bx - margin) - gap) / 2f;
        this.btn(JUMP_START, bx, trimY, bw, rowH, "跳到起点", null, ReplayUi.Style.TONAL);
        this.btn(JUMP_END, bx + bw + gap, trimY, bw, rowH, "跳到终点", null, ReplayUi.Style.TONAL);

        float m3 = (w - gap * 2f) / 3f;
        this.btn(MODE_PLAYER, margin, modeY, m3, rowH, "玩家视角", null, ReplayUi.Style.TONAL);
        this.btn(MODE_FREE, margin + (m3 + gap), modeY, m3, rowH, "自由相机", null, ReplayUi.Style.TONAL);
        this.btn(MODE_TARGET, margin + (m3 + gap) * 2f, modeY, m3, rowH, "目标玩家", null, ReplayUi.Style.TONAL);

        float k3 = (w - gap * 2f) / 3f;
        this.btn(ADD_KEY, margin, keyY, k3, rowH, "添加关键帧", null, ReplayUi.Style.TONAL);
        this.btn(CLEAR_KEY, margin + (k3 + gap), keyY, k3, rowH, "清空轨道", null, ReplayUi.Style.TONAL);
        this.btn(SAVE_KEY, margin + (k3 + gap) * 2f, keyY, k3, rowH, "保存轨道", null, ReplayUi.Style.TONAL);
    }

    private void layoutCompact(float actionsY) {
        float gap = 3f;
        float quarter = (this.width - 12f - gap * 4f) / 5f;
        this.btn(PAUSE, 6f, actionsY, quarter, 20f, "暂停", "pause", ReplayUi.Style.TONAL);
        this.btn(BACK, 6f + quarter + gap, actionsY, quarter, 20f, "◀5s", "fast_rewind", ReplayUi.Style.TEXT);
        this.btn(FORWARD, 6f + (quarter + gap) * 2f, actionsY, quarter, 20f, "5s▶", "fast_forward", ReplayUi.Style.TEXT);
        this.btn(RENDER, 6f + (quarter + gap) * 3f, actionsY, quarter, 20f, "渲染", "download", ReplayUi.Style.FILLED);
        this.btn(EXIT, 6f + (quarter + gap) * 4f, actionsY, quarter, 20f, "退出", "close", ReplayUi.Style.TONAL);

        float rowH = 20f;
        float rowGap = 7f;
        float margin = 6f;
        float w = this.width - margin * 2f;
        float keyY = this.height - margin - rowH;
        float modeY = keyY - rowH - rowGap;
        float trimY = modeY - rowH - rowGap;

        float fieldW = Math.min(60f, w / 4f);
        this.fieldBox(F_TRIM_START, 0, margin, trimY, fieldW, rowH);
        this.fieldBox(F_TRIM_END, 1, margin + fieldW + gap, trimY, fieldW, rowH);
        float bx = margin + fieldW * 2f + gap * 2f;
        float bw = (w - (bx - margin) - gap) / 2f;
        this.btn(JUMP_START, bx, trimY, bw, rowH, "起点", null, ReplayUi.Style.TONAL);
        this.btn(JUMP_END, bx + bw + gap, trimY, bw, rowH, "终点", null, ReplayUi.Style.TONAL);

        float m3 = (w - gap * 2f) / 3f;
        this.btn(MODE_PLAYER, margin, modeY, m3, rowH, "玩家", null, ReplayUi.Style.TONAL);
        this.btn(MODE_FREE, margin + (m3 + gap), modeY, m3, rowH, "自由", null, ReplayUi.Style.TONAL);
        this.btn(MODE_TARGET, margin + (m3 + gap) * 2f, modeY, m3, rowH, "目标", null, ReplayUi.Style.TONAL);

        float k3 = (w - gap * 2f) / 3f;
        this.btn(ADD_KEY, margin, keyY, k3, rowH, "添加关键帧", null, ReplayUi.Style.TONAL);
        this.btn(CLEAR_KEY, margin + (k3 + gap), keyY, k3, rowH, "清空", null, ReplayUi.Style.TONAL);
        this.btn(SAVE_KEY, margin + (k3 + gap) * 2f, keyY, k3, rowH, "保存", null, ReplayUi.Style.TONAL);
    }

    private int keyCount() {
        CameraController controller = this.camera();
        return controller == null || controller.getPath() == null ? 0 : controller.getPath().keyframes.size();
    }

    @Override
    public void render(int mx, int my, float tickDelta) {
        this.mouseX = mx;
        this.mouseY = my;
        long now = System.nanoTime();
        this.dt = this.lastNanos == 0L ? 0.05f : Math.min(0.05f, (now - this.lastNanos) / 1.0E9f);
        this.lastNanos = now;
        this.openFrames++;
        // 预览会话可能比界面晚一帧才就绪：等到能拿到 CameraController 的那一帧，
        // 把轨道里的起止和播放头回填一次。
        if (!this.trimRestored && this.preview != null) {
            CameraController controller = camera();
            if (controller != null) {
                this.trimRestored = true;
                double total = Math.max(0.01D, this.preview.getTotalSeconds());
                double savedStart = controller.getSavedTrimStart();
                double savedEnd = controller.getSavedTrimEnd();
                // 不回填的话 trimStart()/trimEnd() 读到的是空输入框的默认值
                // （0 和全长），再保存就等于把范围抹回整段。
                if (savedStart >= 0.0D && savedStart < total - 0.05D
                        && (this.fields[F_TRIM_START].text == null || this.fields[F_TRIM_START].text.isEmpty())) {
                    this.fields[F_TRIM_START].text = fmt(savedStart);
                }
                if (savedEnd > savedStart + 0.05D && savedEnd <= total
                        && (this.fields[F_TRIM_END].text == null || this.fields[F_TRIM_END].text.isEmpty())) {
                    this.fields[F_TRIM_END].text = fmt(savedEnd);
                }
                double saved = controller.getSavedPlayhead();
                if (saved > 0.05D) {
                    // 上次保存时的播放头，重新打开剪辑台就回到这一帧。
                    this.preview.seekToSeconds(saved);
                }
            }
        }
        tickEditor(now);

        Md3Theme t = ClientTheme.get();
        this.canvas.begin(this.width, this.height);
        if (!this.panelVisible) {
            this.canvas.fill(0f, 0f, this.width, 22f, 0x66000000);
            Md3Text.centered(this.canvas, "T 呼出剪辑面板 · 拖动屏幕转视角 · F5 切视角 · F1 切 HUD",
                    this.width / 2f, 11f, Md3Type.BODY_SMALL, 0xFFFFFFFF);
            String time = timeText();
            Md3Text.centered(this.canvas, time, this.width / 2f, 30f, Md3Type.LABEL_MEDIUM, 0xFFFFF0A0);
            this.canvas.end();
            return;
        }
        drawCards(t);
        drawHeader(t);
        drawTimeline(t);
        drawButtons(t);
        drawFields(t);
        drawCardLabels(t);
        drawFooter(t);
        if (this.pendingDelete != null) drawDeleteDialog(t);
        else if (this.pendingLeave != 0) drawLeaveDialog(t);
        this.canvas.end();
    }

    private void setGrabbed(boolean grabbed) {
        try {
            Mouse.setGrabbed(grabbed);
        } catch (Throwable ignored) {
        }
    }

    private void tickEditor(long now) {
        CameraController controller = this.camera();
        boolean f5;
        try {
            f5 = Keyboard.isKeyDown(Keyboard.KEY_F5);
        } catch (Throwable t) {
            f5 = false;
        }
        if (f5 && !this.lastF5) cyclePerspective();
        this.lastF5 = f5;

        if (!this.panelVisible && controller != null
                && controller.getMode() == CameraController.MODE_FREE) {
            // 面板收起时用原始鼠标位移转视角（鼠标被 grab）。
            int dx = Mouse.getDX();
            int dy = Mouse.getDY();
            if (dx != 0 || dy != 0) controller.rotateFree(dx * 0.45f, -dy * 0.45f);
        }
        if (this.focusField < 0 && controller != null
                && controller.getMode() == CameraController.MODE_FREE) {
            // 面板展开时鼠标已释放，WASD 依然可以直接推机位。
            double forward = (Keyboard.isKeyDown(Keyboard.KEY_W) ? 1.0D : 0.0D)
                    - (Keyboard.isKeyDown(Keyboard.KEY_S) ? 1.0D : 0.0D);
            double strafe = (Keyboard.isKeyDown(Keyboard.KEY_D) ? 1.0D : 0.0D)
                    - (Keyboard.isKeyDown(Keyboard.KEY_A) ? 1.0D : 0.0D);
            double vertical = (Keyboard.isKeyDown(Keyboard.KEY_SPACE) ? 1.0D : 0.0D)
                    - ((Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) ? 1.0D : 0.0D);
            if (forward != 0.0D || strafe != 0.0D || vertical != 0.0D) {
                controller.moveFree(forward, strafe, vertical, this.dt * 20.0D);
            }
        }
        syncCameraFields();
        if (this.preview != null) {
            this.preview.setClipRange((long) (trimStart() * 1.0e9D), (long) (trimEnd() * 1.0e9D));
        }
        if (this.preview == null || this.preview.isFinished()) {
            this.message = "回放预览已结束。";
        }
        if (this.messageUntil > 0L && now / 1000000L > this.messageUntil) {
            this.message = "";
            this.messageUntil = 0L;
        }
    }

    private void drawHeader(Md3Theme t) {
        this.canvas.fill(0f, 0f, this.width, this.headerH, 0xE6101018);
        String file = this.preview == null ? "-" : this.preview.getFile().getName();
        float x = 8f;
        this.canvas.icon("animation", x, this.headerH / 2f - 7f, 14f, 0xFFFFFFFF);
        Md3Text.leftMiddle(this.canvas, ReplayUi.fit(this.canvas,
                "剪辑台 · " + file, Md3Type.TITLE_SMALL, this.width * 0.5f),
                x + 20f, this.headerH / 2f, Md3Type.TITLE_SMALL, 0xFFFFFFFF);
        String time = timeText();
        Md3Text.rightMiddle(this.canvas, time, this.width - 8f, this.headerH / 2f,
                Md3Type.LABEL_MEDIUM, 0xFFFFE082);
    }

    private String timeText() {
        if (this.preview == null) return "0:00 / 0:00";
        double total = Math.max(0.01D, this.preview.getTotalSeconds());
        return ReplayPaths.formatDuration(this.preview.getCurrentSeconds()) + " / "
                + ReplayPaths.formatDuration(total);
    }

    private void drawTimeline(Md3Theme t) {
        this.canvas.roundRect(this.timelineX, this.timelineY, this.timelineW, this.timelineH,
                this.timelineH / 2f, 0xCC202028);
        double total = this.preview == null ? 1.0D : Math.max(0.01D, this.preview.getTotalSeconds());
        float sx = this.timeX(trimStart());
        float ex = this.timeX(trimEnd());
        this.canvas.roundRect(sx, this.timelineY, Math.max(2f, ex - sx), this.timelineH,
                this.timelineH / 2f, 0xCC4F378B);
        // 裁剪把手
        this.canvas.roundRect(sx - 2f, this.timelineY - 2f, 4f, this.timelineH + 4f, 2f, 0xFFFFF176);
        this.canvas.roundRect(ex - 2f, this.timelineY - 2f, 4f, this.timelineH + 4f, 2f, 0xFFFF8A80);
        // 播放头
        float px = this.timeX(this.preview == null ? 0.0D : this.preview.getCurrentSeconds());
        this.canvas.fill(px, this.timelineY - 3f, 1.5f, this.timelineH + 6f, 0xFFFFFFFF);
        // 关键帧
        CameraController controller = this.camera();
        if (controller != null && controller.getPath() != null) {
            for (CameraPath.Keyframe frame : controller.getPath().keyframes) {
                float kx = this.timeX(frame.time);
                int color = frame.mode == CameraController.MODE_PLAYER ? 0xFF69F0AE
                        : frame.mode == CameraController.MODE_ENTITY ? 0xFFFFD54F : 0xFF40C4FF;
                this.canvas.roundRect(kx - 1.5f, this.timelineY + 2f, 3f, 3f, 1.5f, color);
                this.canvas.roundRect(kx - 1.5f, this.timelineY + 7f, 3f, 3f, 1.5f, color);
            }
        }
        if (Mouse.isButtonDown(0) && this.timelineDrag != 0 && this.preview != null) {
            double value = this.secondsAt(this.mouseX);
            if (this.timelineDrag == 1) {
                double end = trimEnd();
                this.fields[F_TRIM_START].text = fmt(Math.max(0.0D, Math.min(value, end - 0.05D)));
                this.pathDirty = true;
            } else if (this.timelineDrag == 2) {
                double start = trimStart();
                this.fields[F_TRIM_END].text = fmt(Math.min(total, Math.max(value, start + 0.05D)));
                this.pathDirty = true;
            } else {
                this.preview.seekToSeconds(value);
            }
        }
        String totalText = ReplayPaths.formatDuration(total);
        Md3Text.rightMiddle(this.canvas, totalText, this.timelineX + this.timelineW,
                this.timelineY - 5f, Md3Type.LABEL_SMALL, 0xCCFFFFFF);
    }

    private float timeX(double seconds) {
        double total = this.preview == null ? 1.0D : Math.max(0.01D, this.preview.getTotalSeconds());
        float ratio = (float) Math.max(0.0D, Math.min(1.0D, seconds / total));
        return this.timelineX + ratio * this.timelineW;
    }

    private double secondsAt(float mx) {
        double total = this.preview == null ? 0.0D : Math.max(0.01D, this.preview.getTotalSeconds());
        float ratio = Math.max(0f, Math.min(1f, (mx - this.timelineX) / this.timelineW));
        return ratio * total;
    }

    private void drawButtons(Md3Theme t) {
        CameraController controller = this.camera();
        int mode = controller == null ? -1 : controller.getMode();
        for (Btn b : this.buttons) {
            boolean hover = ReplayUi.hit(this.mouseX, this.mouseY, b.x, b.y, b.w, b.h);
            String label = b.label;
            String icon = b.icon;
            ReplayUi.Style style = b.style;
            if (b.id == PAUSE) {
                label = this.preview != null && this.preview.isPaused() ? "继续" : "暂停";
                icon = this.preview != null && this.preview.isPaused() ? "play_arrow" : "pause";
            } else if (b.id == MODE_PLAYER && mode == CameraController.MODE_PLAYER) {
                style = ReplayUi.Style.FILLED;
            } else if (b.id == MODE_FREE && mode == CameraController.MODE_FREE) {
                style = ReplayUi.Style.FILLED;
            } else if (b.id == MODE_TARGET && mode == CameraController.MODE_ENTITY) {
                style = ReplayUi.Style.FILLED;
                label = "目标:" + targetLabel();
            } else if (b.id == CLEAR_KEY) {
                label = "清空轨道 " + keyCount();
            }
            boolean enabled = buttonEnabled(b.id);
            ReplayUi.button(this.canvas, t, b.x, b.y, b.w, b.h, label, icon, style, enabled,
                    hover, hover && this.mouseDown);
        }
    }

    private boolean buttonEnabled(int id) {
        if (this.preview == null) return false;
        if (id == EXIT || id == HIDE) return true;
        if (id == RENDER) return !this.preview.isFinished() && !busy();
        if (id == CLEAR_KEY) return keyCount() > 0;
        // 保存轨道：有关键帧、或者改过片段范围、或者有未保存改动时都该能点。
        if (id == SAVE_KEY) {
            if (keyCount() > 0 || this.pathDirty) return true;
            double total = this.preview.getTotalSeconds();
            double start = trimStart();
            double end = trimEnd();
            return start > 0.05D || (total > 0.0D && end < total - 0.05D);
        }
        if (id >= MODE_PLAYER && id <= MODE_TARGET) return this.camera() != null;
        if (id >= LOOK_LEFT && id < LOOK_LEFT + 10) return this.camera() != null;
        if (id == APPLY_CAM) return this.camera() != null;
        return !this.preview.isFinished();
    }

    private boolean busy() {
        return ReplayClient.getRenderManager() != null
                && ReplayClient.getRenderManager().isActive()
                && ReplayClient.getRenderManager().getPreview() == null;
    }

    private void drawFields(Md3Theme t) {
        String[] labels = {"起点", "终点", "X", "Y", "Z", "Yaw", "Pitch", "FOV"};
        for (int i = 0; i < this.fields.length; i++) {
            if (!this.fieldVisible[i]) continue;
            if (i < F_CAM_X) {
                Md3Text.leftMiddle(this.canvas, labels[i] + " (秒)", this.fieldX[i],
                        this.fieldY[i] - 8f, Md3Type.LABEL_SMALL, 0xCCFFFFFF);
            } else {
                Md3Text.rightMiddle(this.canvas, labels[i], this.fieldX[i] - 2f,
                        this.fieldY[i] + this.fieldH[i] / 2f, Md3Type.LABEL_SMALL, t.colors.onSurfaceVariant);
            }
            Md3TextField field = this.fields[i];
            field.focused = this.focusField == i;
            field.render(this.canvas, t, this.mouseX, this.mouseY, false, this.dt);
        }
    }

    /** 剪辑台不再画卡片底板：控制按钮直接浮在回放画面上，中间整块留作观察区。 */
    private void drawCards(Md3Theme t) {
    }

    private void drawCardLabels(Md3Theme t) {
    }

    private void layoutDialog() {
        this.dlgW = Math.min(280f, this.width - 32f);
        this.dlgH = 118f;
        this.dlgX = (this.width - this.dlgW) / 2f;
        this.dlgY = (this.height - this.dlgH) / 2f;
    }

    /** 右键时间轴上的关键帧：确认后只删这一帧。 */
    private void drawDeleteDialog(Md3Theme t) {
        layoutDialog();
        this.canvas.fill(0f, 0f, this.width, this.height, 0x88000000);
        this.canvas.shadow(this.dlgX, this.dlgY, this.dlgW, this.dlgH, 16f, 2);
        this.canvas.roundRect(this.dlgX, this.dlgY, this.dlgW, this.dlgH, 16f,
                t.colors.surfaceContainerHigh);
        Md3Text.leftMiddle(this.canvas, "删除这个关键帧？", this.dlgX + 16f, this.dlgY + 18f,
                Md3Type.TITLE_SMALL, t.colors.onSurface);
        String info = String.format(Locale.ROOT, "%.2f 秒 · %s", this.pendingDelete.time,
                modeLabel(this.pendingDelete.mode));
        Md3Text.leftMiddle(this.canvas, info, this.dlgX + 16f, this.dlgY + 42f,
                Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);
        float gap = 8f;
        float bw = (this.dlgW - 32f - gap) / 2f;
        float by = this.dlgY + this.dlgH - 34f;
        ReplayUi.button(this.canvas, t, this.dlgX + 16f, by, bw, 24f, "删除", "delete",
                ReplayUi.Style.DANGER, true,
                ReplayUi.hit(this.mouseX, this.mouseY, this.dlgX + 16f, by, bw, 24f), this.mouseDown);
        ReplayUi.button(this.canvas, t, this.dlgX + 16f + bw + gap, by, bw, 24f, "取消", "close",
                ReplayUi.Style.OUTLINED, true,
                ReplayUi.hit(this.mouseX, this.mouseY, this.dlgX + 16f + bw + gap, by, bw, 24f),
                this.mouseDown);
    }

    /** 退出 / 渲染前：轨道有未保存改动时先询问是否保存。 */
    private void drawLeaveDialog(Md3Theme t) {
        layoutDialog();
        this.canvas.fill(0f, 0f, this.width, this.height, 0x88000000);
        this.canvas.shadow(this.dlgX, this.dlgY, this.dlgW, this.dlgH, 16f, 2);
        this.canvas.roundRect(this.dlgX, this.dlgY, this.dlgW, this.dlgH, 16f,
                t.colors.surfaceContainerHigh);
        String title = this.pendingLeave == 1 ? "退出剪辑台？" : "渲染片段？";
        Md3Text.leftMiddle(this.canvas, title, this.dlgX + 16f, this.dlgY + 18f,
                Md3Type.TITLE_SMALL, t.colors.onSurface);
        Md3Text.leftMiddle(this.canvas, "轨道有未保存的关键帧改动，要先保存吗？",
                this.dlgX + 16f, this.dlgY + 42f, Md3Type.BODY_SMALL, t.colors.onSurfaceVariant);
        float gap = 8f;
        float bw = (this.dlgW - 32f - gap * 2f) / 3f;
        float by = this.dlgY + this.dlgH - 34f;
        float bx = this.dlgX + 16f;
        ReplayUi.button(this.canvas, t, bx, by, bw, 24f, "保存", "save",
                ReplayUi.Style.FILLED, true,
                ReplayUi.hit(this.mouseX, this.mouseY, bx, by, bw, 24f), this.mouseDown);
        ReplayUi.button(this.canvas, t, bx + bw + gap, by, bw, 24f, "不保存", "delete",
                ReplayUi.Style.DANGER, true,
                ReplayUi.hit(this.mouseX, this.mouseY, bx + bw + gap, by, bw, 24f), this.mouseDown);
        ReplayUi.button(this.canvas, t, bx + (bw + gap) * 2f, by, bw, 24f, "取消", "close",
                ReplayUi.Style.OUTLINED, true,
                ReplayUi.hit(this.mouseX, this.mouseY, bx + (bw + gap) * 2f, by, bw, 24f),
                this.mouseDown);
    }

    private static String modeLabel(int mode) {
        if (mode == CameraController.MODE_PLAYER) return "玩家视角";
        if (mode == CameraController.MODE_ENTITY) return "目标玩家";
        return "自由相机";
    }

    private void drawFooter(Md3Theme t) {
        if (this.message.isEmpty()) return;
        Md3Text.centered(this.canvas, ReplayUi.fit(this.canvas, this.message, Md3Type.BODY_SMALL,
                this.width - 20f), this.width / 2f, this.height - 14f,
                Md3Type.BODY_SMALL, t.colors.primary);
    }

    private String modeName() {
        CameraController controller = this.camera();
        if (controller == null) return "无";
        if (controller.isPathActive()) return "关键帧轨道";
        if (controller.getMode() == CameraController.MODE_FREE) return "自由相机";
        if (controller.getMode() == CameraController.MODE_ENTITY) return "目标玩家";
        return "玩家视角";
    }

    private String targetLabel() {
        CameraController controller = this.camera();
        if (controller == null || this.preview == null || this.preview.getSession() == null
                || this.preview.getSession().getWorld() == null) return "无";
        int id = controller.getTargetEntityId();
        if (id < 0) return "无";
        for (Entity entity : this.preview.getSession().getWorld().getLoadedEntities()) {
            if (entity != null && entity.getEntityId() == id && entity instanceof PlayerEntity) {
                PlayerEntity player = (PlayerEntity) entity;
                return player.getGameProfile() == null ? ("#" + id) : player.getGameProfile().getName();
            }
        }
        return "#" + id;
    }

    /* ---------------- input ---------------- */

    private boolean timelineContains(float mx, float my) {
        return ReplayUi.hit(mx, my, this.timelineX - 6f, this.timelineY - 6f,
                this.timelineW + 12f, this.timelineH + 12f);
    }

    private CameraPath.Keyframe keyframeAt(float mx, float my) {
        CameraController controller = this.camera();
        if (controller == null || controller.getPath() == null) return null;
        CameraPath.Keyframe best = null;
        float bestDistance = 6f;
        for (CameraPath.Keyframe frame : controller.getPath().keyframes) {
            float x = timeX(frame.time);
            float d = Math.abs(mx - x);
            if (d <= bestDistance) {
                bestDistance = d;
                best = frame;
            }
        }
        return best;
    }

    private Btn hitButton(float mx, float my) {
        for (Btn b : this.buttons) {
            if (ReplayUi.hit(mx, my, b.x, b.y, b.w, b.h)) return b;
        }
        return null;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        this.mouseX = mx;
        this.mouseY = my;
        this.mouseDown = true;
        if (this.openFrames < 2 || this.preview == null) return;

        if (this.pendingDelete != null) {
            if (button != 0) return;
            layoutDialog();
            float gap = 8f;
            float bw = (this.dlgW - 32f - gap) / 2f;
            float by = this.dlgY + this.dlgH - 34f;
            if (ReplayUi.hit(mx, my, this.dlgX + 16f, by, bw, 24f)) {
                CameraController controller = this.camera();
                CameraPath.Keyframe frame = this.pendingDelete;
                this.pendingDelete = null;
                if (controller != null && controller.deleteKeyframe(frame)) {
                    this.pathDirty = true;
                    this.message = "已删除关键帧。";
                }
                return;
            }
            if (ReplayUi.hit(mx, my, this.dlgX + 16f + bw + gap, by, bw, 24f)) {
                this.pendingDelete = null;
                return;
            }
            return;
        }

        if (this.pendingLeave != 0) {
            if (button != 0) return;
            layoutDialog();
            float gap = 8f;
            float bw = (this.dlgW - 32f - gap * 2f) / 3f;
            float by = this.dlgY + this.dlgH - 34f;
            float bx = this.dlgX + 16f;
            if (ReplayUi.hit(mx, my, bx, by, bw, 24f)) {
                saveThenContinue();
            } else if (ReplayUi.hit(mx, my, bx + bw + gap, by, bw, 24f)) {
                discardThenContinue();
            } else {
                this.pendingLeave = 0;
            }
            return;
        }

        if (button == 1) {
            if (this.panelVisible && this.timelineContains(mx, my)) {
                CameraPath.Keyframe hit = this.keyframeAt(mx, my);
                if (hit != null) {
                    this.pendingDelete = hit;
                    this.focusField = -1;
                }
            }
            return;
        }
        if (button != 0) return;

        if (!this.panelVisible) {
            // 面板隐藏时也只在自由相机下允许拖动；点一下不会改视角模式。
            CameraController controller = this.camera();
            if (controller != null && controller.getMode() == CameraController.MODE_FREE) {
                this.draggingCamera = true;
                this.dragX = mx;
                this.dragY = my;
            }
            return;
        }

        // 时间轴（含把手）
        if (ReplayUi.hit(mx, my, this.timelineX - 4f, this.timelineY - 4f,
                this.timelineW + 8f, this.timelineH + 8f)) {
            float sx = this.timeX(trimStart());
            float ex = this.timeX(trimEnd());
            if (Math.abs(mx - sx) <= 6f) {
                this.timelineDrag = 1;
            } else if (Math.abs(mx - ex) <= 6f) {
                this.timelineDrag = 2;
            } else {
                this.timelineDrag = 3;
                this.preview.seekToSeconds(this.secondsAt(mx));
            }
            this.focusField = -1;
            return;
        }

        // 输入框
        for (int i = 0; i < this.fields.length; i++) {
            if (!this.fieldVisible[i]) continue;
            if (ReplayUi.hit(mx, my, this.fieldX[i], this.fieldY[i], this.fieldW[i], this.fieldH[i])) {
                this.focusField = i;
                return;
            }
        }
        this.focusField = -1;

        Btn b = hitButton(mx, my);
        if (b != null) {
            if (b.id == HIDE) {
                this.panelVisible = false;
                this.setGrabbed(true);
                return;
            }
            if (buttonEnabled(b.id)) action(b.id);
            return;
        }

        // 空白处拖动相机：按钮、输入框、时间轴、标题栏都已在上面消费命中，剩下的区域都算空白。
        // 只有已经在自由相机里才拖动；点一下空白不再强行切到自由视角。
        {
            CameraController controller = this.camera();
            if (controller != null && controller.getMode() == CameraController.MODE_FREE) {
                this.draggingCamera = true;
                this.blankClick = true;
                this.dragX = mx;
                this.dragY = my;
            }
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        this.mouseDown = false;
        this.timelineDrag = 0;
        boolean wasBlank = this.blankClick;
        this.draggingCamera = false;
        this.blankClick = false;
        // 自由相机下点一下（不是拖动）命中玩家，就切到该玩家视角。
        if (wasBlank && button == 0 && this.panelVisible) {
            float dx = mx - this.dragX;
            float dy = my - this.dragY;
            // 触屏点按的抖动比鼠标大得多，阈值放宽到 12 个 GUI 像素。
            if (dx * dx + dy * dy < 144f) pickTargetPlayer(mx, my);
        }
        super.mouseReleased(mx, my, button);
    }

    /**
     * 自由相机下把点击位置投影回世界：先按当前机位/朝向/FOV 把每个玩家投到屏幕上，
     * 取离点击点最近的那个（像在游戏里瞄准点人），再退回 15° 锥角作为兜底。
     */
    private void pickTargetPlayer(int mx, int my) {
        CameraController controller = this.camera();
        if (controller == null || this.preview == null || this.preview.getSession() == null
                || this.preview.getSession().getWorld() == null) return;
        if (controller.getMode() != CameraController.MODE_FREE) return;
        MinecraftClient client = MinecraftClient.getInstance();
        double ox = controller.getFreeX();
        double oy = controller.getFreeY();
        double oz = controller.getFreeZ();
        double yawRad = Math.toRadians(controller.getFreeYaw());
        double pitchRad = Math.toRadians(controller.getFreePitch());
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        // 与渲染里的 applyTransform（先 pitch 再 yaw）一致的右向量与上向量。
        double rx = -Math.cos(yawRad);
        double ry = 0.0D;
        double rz = -Math.sin(yawRad);
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        float fov = controller.getFreeFov();
        double tanV = Math.tan(Math.toRadians(Math.max(10.0F, Math.min(160.0F, fov))) * 0.5D);
        double tanH = tanV * ((double) this.width / (double) Math.max(1, this.height));

        PlayerEntity best = null;
        double bestPixels = 64.0D;          // 点击点 64 像素内算点到了人
        double bestDepth = Double.MAX_VALUE;
        for (Entity entity : this.preview.getSession().getWorld().getLoadedEntities()) {
            if (!(entity instanceof PlayerEntity) || entity == client.player) continue;
            PlayerEntity player = (PlayerEntity) entity;
            double dx = player.x - ox;
            double dy = (player.y + player.getEyeHeight()) - oy;
            double dz = player.z - oz;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth <= 0.08D) continue;                     // 在相机背后
            double ndcX = (dx * rx + dy * ry + dz * rz) / depth / tanH;
            double ndcY = (dx * ux + dy * uy + dz * uz) / depth / tanV;
            double px = this.width * 0.5D * (1.0D + ndcX);
            double py = this.height * 0.5D * (1.0D - ndcY);
            double off = Math.sqrt((px - mx) * (px - mx) + (py - my) * (py - my));
            if (off <= bestPixels && depth < bestDepth) {
                best = player;
                bestPixels = off;
                bestDepth = depth;
            }
        }
        if (best == null) {
            // 兜底：正对着人点但投影有偏差时，用 15° 锥角 + 32 格找最近的玩家。
            double bestDist = 32.0D;
            for (Entity entity : this.preview.getSession().getWorld().getLoadedEntities()) {
                if (!(entity instanceof PlayerEntity) || entity == client.player) continue;
                PlayerEntity player = (PlayerEntity) entity;
                double dx = player.x - ox;
                double dy = (player.y + player.getEyeHeight()) - oy;
                double dz = player.z - oz;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < 0.1D || dist > bestDist) continue;
                double dot = (dx * fx + dy * fy + dz * fz) / dist;
                if (dot < 0.966D) continue;                   // cos15°
                best = player;
                bestDist = dist;
            }
        }
        if (best != null) {
            controller.setMode(CameraController.MODE_ENTITY);
            controller.setTargetEntity(best.getEntityId());
            this.targetIndex = best.getEntityId();
            this.message = "目标玩家：" + (best.getGameProfile() == null
                    ? ("#" + best.getEntityId()) : best.getGameProfile().getName());
        } else {
            this.message = "点到的位置没有玩家（对准人身上再点，32 格内）。";
        }
    }

    @Override
    protected void mouseDragged(int mx, int my, int button, long clickedAt) {
        if (this.draggingCamera) {
            CameraController controller = this.camera();
            if (controller != null) {
                controller.rotateFree((mx - this.dragX) * 0.35f, (my - this.dragY) * 0.35f);
                this.dragX = mx;
                this.dragY = my;
                syncCameraFields();
            }
        }
        super.mouseDragged(mx, my, button, clickedAt);
    }

    @Override
    protected void keyPressed(char ch, int code) {
        if (this.openFrames < 2) return;
        if (this.pendingLeave != 0) {
            if (code == Keyboard.KEY_ESCAPE) this.pendingLeave = 0;
            return;
        }
        if (this.focusField >= 0) {
            Md3TextField field = this.fields[this.focusField];
            if (code == Keyboard.KEY_ESCAPE || code == Keyboard.KEY_RETURN) {
                this.focusField = -1;
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
        if (code == Keyboard.KEY_ESCAPE) {
            this.panelVisible = false;
            this.setGrabbed(true);
            this.message = "面板已收起，按 T 重新呼出。";
            return;
        }
        if (code == Keyboard.KEY_T) {
            this.panelVisible = !this.panelVisible;
            this.setGrabbed(!this.panelVisible);
            this.message = this.panelVisible ? "已呼出剪辑面板和鼠标。" : "面板已收起。";
            return;
        }
        if (code == Keyboard.KEY_F5) {
            cyclePerspective();
            return;
        }
        if (code == Keyboard.KEY_F1) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null) {
                client.options.hudHidden = !client.options.hudHidden;
                this.message = client.options.hudHidden ? "HUD 已隐藏。" : "HUD 已显示。";
            }
        }
    }

    private void cyclePerspective() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;
        long now = System.currentTimeMillis();
        if (now - this.lastPerspectiveMs < 250L) return;
        this.lastPerspectiveMs = now;
        client.options.perspective = (client.options.perspective + 1) % 3;
        CameraController controller = this.camera();
        if (controller != null) controller.setPerspective(client.options.perspective);
        this.message = client.options.perspective == 0 ? "第一人称"
                : client.options.perspective == 1 ? "第三人称（背后）" : "第三人称（正面）";
    }

    private void action(int id) {
        CameraController controller = this.camera();
        if (id == PAUSE) {
            this.preview.setPaused(!this.preview.isPaused());
            this.message = this.preview.isPaused() ? "已暂停，可以调整相机。" : "继续播放。";
            return;
        }
        if (id == BACK) {
            this.preview.seekToSeconds(this.preview.getCurrentSeconds() - 5.0D);
            return;
        }
        if (id == FORWARD) {
            this.preview.seekToSeconds(this.preview.getCurrentSeconds() + 5.0D);
            return;
        }
        if (id == MODE_PLAYER) {
            if (controller != null) controller.setMode(CameraController.MODE_PLAYER);
            this.message = "切到玩家视角（录制时的自己）。";
            return;
        }
        if (id == MODE_FREE) {
            if (controller != null) {
                if (controller.getMode() != CameraController.MODE_FREE) {
                    controller.resetFreeFromPlayer();
                }
                if (applyCameraFields(controller)) {
                    controller.setMode(CameraController.MODE_FREE);
                    this.message = "自由相机：拖动空白处转视角。";
                } else {
                    controller.setMode(CameraController.MODE_FREE);
                    this.message = "自由相机（相机数值无效，已忽略）。";
                }
            }
            return;
        }
        if (id == MODE_TARGET) {
            cycleTarget(controller);
            return;
        }
        if (id == ADD_KEY) {
            if (controller == null) return;
            if (controller.getMode() == CameraController.MODE_FREE && !applyCameraFields(controller)) return;
            double time = this.preview.getCurrentSeconds();
            controller.addCurrentKeyframe(time);
            this.pathDirty = true;
            this.message = String.format(Locale.ROOT, "已在 %.2f 秒添加关键帧（%s）。", time, modeName());
            return;
        }
        if (id == CLEAR_KEY) {
            if (controller != null) {
                controller.clearPath();
                this.pathDirty = true;
            }
            this.message = "关键帧轨道已清空。";
            return;
        }
        if (id == SAVE_KEY) {
            if (controller == null) return;
            double start = trimStart();
            double end = trimEnd();
            boolean hasKeys = controller.hasPath();
            double total = this.preview == null ? 0.0D : this.preview.getTotalSeconds();
            boolean hasTrim = start > 0.05D || (total > 0.0D && end < total - 0.05D);
            if (!hasKeys && !hasTrim) {
                this.message = "还没有关键帧或片段范围可保存。";
                return;
            }
            // 片段起止也要一起写进轨道文件，否则重新打开会被重置成整段。
            controller.setTrimRange(start, end);
            if (this.preview != null) controller.setPlayhead(this.preview.getCurrentSeconds());
            controller.savePath();
            this.pathDirty = false;
            this.message = "已保存：" + (hasKeys ? "关键帧 " + keyCount() + " 个" : "无关键帧")
                    + " · 片段 " + fmt(start) + "s → " + fmt(end) + "s"
                    + " → " + controller.getPathFile().getName();
            return;
        }
        if (id == JUMP_START) {
            this.preview.seekToSeconds(trimStart());
            this.message = "已跳到片段起点。";
            return;
        }
        if (id == JUMP_END) {
            this.preview.seekToSeconds(trimEnd());
            this.message = "已跳到片段终点。";
            return;
        }
        if (id == RENDER) {
            if (this.pathDirty) {
                this.pendingLeave = 2;
            } else {
                renderClip(controller);
            }
            return;
        }
        if (id == EXIT) {
            if (this.pathDirty) {
                this.pendingLeave = 1;
            } else {
                doExit();
            }
            return;
        }
        if (id == APPLY_CAM) {
            if (controller != null && applyCameraFields(controller)) {
                this.message = "相机坐标已应用。";
            }
            return;
        }
        if (id >= LOOK_LEFT && id < LOOK_LEFT + 10) {
            if (controller == null) return;
            if (controller.getMode() != CameraController.MODE_FREE) {
                controller.setMode(CameraController.MODE_FREE);
            }
            if (id == LOOK_LEFT) controller.rotateFree(-12f, 0f);
            else if (id == LOOK_LEFT + 1) controller.rotateFree(12f, 0f);
            else if (id == LOOK_LEFT + 2) controller.rotateFree(0f, -12f);
            else if (id == LOOK_LEFT + 3) controller.rotateFree(0f, 12f);
            else if (id == LOOK_LEFT + 4) nudge(controller, 1.0D, 0.0D, 0.0D);
            else if (id == LOOK_LEFT + 5) nudge(controller, -1.0D, 0.0D, 0.0D);
            else if (id == LOOK_LEFT + 6) nudge(controller, 0.0D, -1.0D, 0.0D);
            else if (id == LOOK_LEFT + 7) nudge(controller, 0.0D, 1.0D, 0.0D);
            else if (id == LOOK_LEFT + 8) nudge(controller, 0.0D, 0.0D, 1.0D);
            else if (id == LOOK_LEFT + 9) nudge(controller, 0.0D, 0.0D, -1.0D);
            syncCameraFields();
            this.message = String.format(Locale.ROOT, "自由相机 X %.2f Y %.2f Z %.2f",
                    controller.getFreeX(), controller.getFreeY(), controller.getFreeZ());
        }
    }

    private void nudge(CameraController controller, double forward, double strafe, double up) {
        for (int i = 0; i < 3; i++) controller.moveFree(forward, strafe, up);
    }

    private void doExit() {
        if (this.preview != null) this.preview.stop();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.setScreen(new ReplayScreen());
    }

    /** 保存未保存的轨道，再执行之前被拦下的退出 / 渲染动作。 */
    private void saveThenContinue() {
        CameraController controller = this.camera();
        if (controller != null) {
            controller.setTrimRange(trimStart(), trimEnd());
            controller.savePath();
        }
        this.pathDirty = false;
        int action = this.pendingLeave;
        this.pendingLeave = 0;
        if (action == 1) {
            doExit();
        } else if (action == 2) {
            renderClip(controller);
        }
    }

    /** 放弃未保存改动，继续执行被拦下的动作。 */
    private void discardThenContinue() {
        this.pathDirty = false;
        int action = this.pendingLeave;
        this.pendingLeave = 0;
        if (action == 1) {
            doExit();
        } else if (action == 2) {
            renderClip(this.camera());
        }
    }

    private boolean applyCameraFields(CameraController controller) {
        try {
            double x = Double.parseDouble(this.fields[F_CAM_X].text.trim());
            double y = Double.parseDouble(this.fields[F_CAM_X + 1].text.trim());
            double z = Double.parseDouble(this.fields[F_CAM_X + 2].text.trim());
            float yaw = Float.parseFloat(this.fields[F_CAM_X + 3].text.trim());
            float pitch = Float.parseFloat(this.fields[F_CAM_X + 4].text.trim());
            float fov = Float.parseFloat(this.fields[F_CAM_X + 5].text.trim());
            controller.setFreeTransform(x, y, z, yaw, pitch, fov);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void cycleTarget(CameraController controller) {
        if (controller == null || this.preview == null || this.preview.getSession() == null
                || this.preview.getSession().getWorld() == null) {
            this.message = "回放世界尚未加载。";
            return;
        }
        List<PlayerEntity> players = new ArrayList<PlayerEntity>();
        for (Entity entity : this.preview.getSession().getWorld().getLoadedEntities()) {
            if (entity instanceof PlayerEntity
                    && entity != MinecraftClient.getInstance().player) {
                players.add((PlayerEntity) entity);
            }
        }
        if (players.isEmpty()) {
            this.message = "回放里没有其他玩家。";
            return;
        }
        this.targetIndex = (this.targetIndex + 1) % players.size();
        PlayerEntity target = players.get(this.targetIndex);
        controller.setMode(CameraController.MODE_ENTITY);
        controller.setTargetEntity(target.getEntityId());
        this.message = "目标玩家：" + (target.getGameProfile() == null
                ? ("#" + target.getEntityId()) : target.getGameProfile().getName());
    }

    private void renderClip(CameraController controller) {
        if (ReplayFfmpegMissingScreen.needsPrompt()) {
            // 没有 ffmpeg 就别默默吐 PNG：先让用户选。
            MinecraftClient mc = MinecraftClient.getInstance();
            final CameraController cam = controller;
            if (mc != null) {
                mc.setScreen(new ReplayFfmpegMissingScreen(new Runnable() {
                    @Override
                    public void run() {
                        renderClip(cam);
                    }
                }));
            }
            return;
        }
        double start = trimStart();
        double end = trimEnd();
        double total = this.preview.getTotalSeconds();
        if (start >= end) {
            this.message = "片段终点必须大于起点。";
            return;
        }
        start = Math.min(start, total);
        end = Math.min(end, total);
        ReplayConfig config = ReplayClient.getConfig();
        if (config == null) return;
        ReplayClient.LOGGER.info("[replay] editor render clip: {}s -> {}s", start, end);
        this.preview.stop();
        if (ReplayClient.startRender(this.preview.getFile(), config.defaultFps, config.defaultWidth,
                config.defaultHeight, config.encoder, (long) (start * 1.0e9D),
                (long) (end * 1.0e9D), controller) != null) {
            this.message = "已开始导出片段，进度页见。";
        } else {
            this.message = "导出启动失败，请查看日志。";
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) client.setScreen(new ReplayScreen());
        }
    }

    private double trimStart() {
        double value = parse(this.fields[F_TRIM_START], 0.0D);
        double total = this.preview == null ? 0.01D : Math.max(0.01D, this.preview.getTotalSeconds());
        return Math.max(0.0D, Math.min(total - 0.05D, value));
    }

    private double trimEnd() {
        double total = this.preview == null ? 0.01D : Math.max(0.01D, this.preview.getTotalSeconds());
        double value = parse(this.fields[F_TRIM_END], total);
        return Math.max(trimStart() + 0.05D, Math.min(total, value));
    }

    private double parse(Md3TextField field, double fallback) {
        try {
            return Double.parseDouble(field.text.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void syncCameraFields() {
        CameraController controller = this.camera();
        if (controller == null || controller.getMode() != CameraController.MODE_FREE) return;
        if (this.focusField >= F_CAM_X) return;
        this.fields[F_CAM_X].text = fmt(controller.getFreeX());
        this.fields[F_CAM_X + 1].text = fmt(controller.getFreeY());
        this.fields[F_CAM_X + 2].text = fmt(controller.getFreeZ());
        this.fields[F_CAM_X + 3].text = fmt(controller.getFreeYaw());
        this.fields[F_CAM_X + 4].text = fmt(controller.getFreePitch());
        this.fields[F_CAM_X + 5].text = fmt(controller.getFreeFov());
    }

    @Override
    public void init(MinecraftClient client, int width, int height) {
        super.init(client, width, height);
        CameraController controller = this.camera();
        if (controller != null) {
            if (controller.hasPath()) controller.usePath(true);
            double savedStart = controller.getSavedTrimStart();
            double savedEnd = controller.getSavedTrimEnd();
            // 只在输入框还空着时回填，否则 init() 在缩放/旋转时被再次调用
            // 会把用户刚改、还没保存的范围冲掉。
            if (this.fields[F_TRIM_START].text == null || this.fields[F_TRIM_START].text.isEmpty()) {
                this.fields[F_TRIM_START].text = fmt(savedStart >= 0.0D ? savedStart
                        : (this.preview == null ? 0.0D : this.preview.getStartTimeNanos() / 1.0e9D));
            }
            if (this.fields[F_TRIM_END].text == null || this.fields[F_TRIM_END].text.isEmpty()) {
                this.fields[F_TRIM_END].text = fmt(savedEnd > 0.0D ? savedEnd
                        : (this.preview == null ? 0.0D : this.preview.getEndTimeNanos() / 1.0e9D));
            }
            syncCameraFields();
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    @Override
    public void removed() {
        this.setGrabbed(false);
        super.removed();
    }
}
