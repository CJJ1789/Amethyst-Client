package net.client.replay;

import net.fabricmc.api.ClientModInitializer;
import net.client.replay.config.ReplayConfig;
import net.client.replay.gui.ReplayEditorScreen;
import net.client.replay.gui.ReplayScreen;
import net.client.replay.record.RecordingManager;
import net.client.replay.render.PreviewJob;
import net.client.replay.render.RenderJob;
import net.client.replay.render.RenderManager;
import net.client.replay.util.ReplayPaths;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.io.File;

/**
 * 回放子系统门面：初始化录制 / 回放 / 导出管理器、统一处理 F7/F8/F9 热键，
 * 并提供打开回放控制台和剪辑器的入口。
 */
public class ReplayClient implements ClientModInitializer {
    public static final String MOD_ID = "client";
    public static final Logger LOGGER = LogManager.getLogger("Client/Replay");

    private static ReplayConfig config;
    private static RecordingManager recordingManager;
    private static RenderManager renderManager;
    private static ReplayClient instance;

    private boolean lastRecordKey;
    private boolean lastRenderKey;
    private boolean lastStopKey;
    private boolean lastGuiKey;
    private static boolean hotkeysEnabled = true;

    @Override
    public void onInitializeClient() {
        instance = this;
        config = ReplayConfig.load();
        MinecraftClient client = MinecraftClient.getInstance();
        recordingManager = new RecordingManager(client);
        renderManager = new RenderManager(client);
        LOGGER.info("回放子系统已加载：replays={} exports={}",
                ReplayPaths.replaysDir().getAbsolutePath(),
                ReplayPaths.exportsDir().getAbsolutePath());
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (recordingManager != null) recordingManager.shutdown();
                } catch (Throwable ignored) {
                }
            }
        }, "Client-replay-save"));
    }

    public static ReplayConfig getConfig() {
        return config;
    }

    /** 1.0.2：调试输出的统一开关（config/client-replay.json 里的 "debug"）。 */
    public static boolean debugEnabled() {
        ReplayConfig current = config;
        return current != null && current.debug;
    }

    public static RecordingManager getRecordingManager() {
        return recordingManager;
    }

    public static RenderManager getRenderManager() {
        return renderManager;
    }

    public static boolean isReady() {
        return config != null && recordingManager != null && renderManager != null;
    }

    public static File gameDir() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? new File(".") : client.runDirectory;
    }

    /** 回放正在占用可见世界：此时不允许再开启录制。 */
    /** 把 LWJGL 键码变成界面/聊天里显示的名字（跟随用户改过的绑定）。 */
    public static String keyName(int code) {
        if (code <= 0) return "未绑定";
        try {
            String name = org.lwjgl.input.Keyboard.getKeyName(code);
            return name == null || name.isEmpty() ? ("键码 " + code) : name;
        } catch (Throwable t) {
            return "键码 " + code;
        }
    }

    public static String recordKeyName() {
        return keyName(getConfig().hotkeyRecord);
    }

    public static String stopKeyName() {
        return keyName(getConfig().hotkeyStop);
    }

    public static String renderKeyName() {
        return keyName(getConfig().hotkeyRender);
    }

    public static boolean isReplayActive() {
        if (renderManager == null || !renderManager.isActive()) return false;
        return renderManager.getPreview() != null || !renderManager.isBackgroundRendering();
    }

    private static volatile boolean replayCaptureActive;

    /** 声音钩子被调用次数：用来确认 SoundManager#play 到底有没有被拦到。 */
    public static volatile long soundSeen;
    public static volatile long soundRecorded;

    public static void noteSoundSeen() {
        soundSeen++;
    }

    public static void noteSoundRecorded() {
        soundRecorded++;
    }

    public static void setReplayCaptureActive(boolean active) {
        replayCaptureActive = active;
    }

    public static boolean isReplayCaptureActive() {
        return replayCaptureActive;
    }

    /**
     * 剪辑/预览只允许在主菜单进行；世界里只能录制。
     * 例外：已经开着回放预览时，允许重新打开剪辑面板。
     */
    public static boolean canEditReplay() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        return client.world == null || (renderManager != null && renderManager.getPreview() != null);
    }

    /** 打开回放控制台（主菜单）或当前预览的剪辑器。 */
    public static void openConsole() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        if (!canEditReplay()) {
            chat("只能在主菜单编辑/渲染回放；游戏中只能录制。");
            return;
        }
        try {
            if (renderManager != null && renderManager.getPreview() != null) {
                client.setScreen(new ReplayEditorScreen(renderManager.getPreview()));
            } else {
                client.setScreen(new ReplayScreen());
            }
        } catch (Throwable t) {
            LOGGER.warn("无法打开回放界面", t);
            chat("打开回放界面失败：" + t.getMessage());
        }
    }

    /** 兼容旧命令/热键的命名。 */
    public static void openGui() {
        openConsole();
    }

    public static void chat(String message) {
        if (message == null || message.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        if (!client.isOnThread()) {
            client.execute(new java.util.concurrent.Callable<Void>() {
                @Override
                public Void call() {
                    chatNow(message);
                    return null;
                }
            });
            return;
        }
        chatNow(message);
    }

    private static void chatNow(String message) {
        LOGGER.info("[replay] {}", message);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        Text text = new LiteralText("[回放] " + message);
        try {
            if (client.player != null) {
                client.player.addMessage(text);
            } else if (client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(text);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 由 ReplayMinecraftClientMixin 在 MinecraftClient#tick 末尾调用。 */
    private static boolean autoPlayTried;

    /** 调试用：-Dclient.replay.autoPlay=<文件> 启动后自动进入该回放，绕过 UI 点击。 */
    private static void maybeAutoPlay() {
        if (autoPlayTried) return;
        String path = System.getProperty("client.replay.autoPlay");
        if (path == null || path.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world != null) return;
        if (client.currentScreen != null
                && !(client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen)) return;
        autoPlayTried = true;
        File file = new File(path);
        if (debugEnabled()) {
            LOGGER.info("[AutoPlay] opening {} (exists={})", file.getAbsolutePath(), file.exists());
        }
        openPreview(file);
    }

    public static void onClientTick() {
        maybeAutoPlay();
        if (recordingManager != null) recordingManager.clientTick();
        if (renderManager != null) renderManager.clientTick();
        if (instance != null && hotkeysEnabled) instance.pollHotkeys();
    }

    /** 由 ReplayMinecraftClientMixin 在 runGameLoop 末尾调用。 */
    public static void onFrameEnd(float tickDelta) {
        if (recordingManager != null) recordingManager.sample(System.nanoTime(), tickDelta);
        if (renderManager != null) renderManager.renderTick();
    }

    private void pollHotkeys() {
        if (config == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        boolean record = isDown(config.hotkeyRecord);
        boolean render = isDown(config.hotkeyRender);
        boolean stop = isDown(config.hotkeyStop);
        boolean gui = isDown(config.hotkeyGui);

        // 有界面（聊天、背包、设置）时一律不触发，避免在输入框里按 F7 误录。
        Screen screen = client.currentScreen;
        if (screen != null) {
            if (gui && !lastGuiKey && client.world == null) {
                openConsole();
            }
            lastGuiKey = gui;
            lastRecordKey = record;
            lastRenderKey = render;
            lastStopKey = stop;
            return;
        }

        if (gui && !lastGuiKey && canEditReplay()) {
            openConsole();
        }
        lastGuiKey = gui;

        if (record && !lastRecordKey) {
            if (recordingManager.isActive()) {
                chat("已经在录制，按 " + stopKeyName() + " 停止。");
            } else if (client.world == null || client.player == null) {
                chat("进入世界后才能开始录制；主菜单只用于编辑/渲染回放。");
            } else {
                recordingManager.start("");
            }
        }
        if (stop && !lastStopKey) {
            if (recordingManager.isActive()) {
                recordingManager.stop();
            } else if (renderManager.isActive()) {
                renderManager.stop();
                chat("已停止回放 / 渲染任务。");
            } else {
                chat("当前没有录制或渲染任务。");
            }
        }
        if (render && !lastRenderKey) {
            if (renderManager.isActive()) {
                chat("已有回放 / 渲染任务在运行，按 " + stopKeyName() + " 停止。");
            } else {
                renderManager.startLatest();
            }
        }

        lastRecordKey = record;
        lastRenderKey = render;
        lastStopKey = stop;
    }

    private static boolean isDown(int key) {
        if (key < 0) return false;
        try {
            return Keyboard.isKeyDown(key);
        } catch (Throwable t) {
            hotkeysEnabled = false;
            return false;
        }
    }

    /** 启动渲染并返回任务；UI 用返回值决定是否切到进度页。 */
    public static RenderJob startRender(File replay, double fps, int width, int height,
                                        String encoder, long startNanos, long endNanos,
                                        net.client.replay.session.CameraController camera) {
        if (renderManager == null || replay == null) return null;
        if (!renderManager.start(replay, fps, width, height, encoder, startNanos, endNanos, camera)) {
            return null;
        }
        return renderManager.getJob();
    }

    public static PreviewJob openPreview(File replay) {
        if (renderManager == null || replay == null) return null;
        if (!renderManager.startPreview(replay)) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        PreviewJob preview = renderManager.getPreview();
        if (client != null && preview != null) {
            client.setScreen(new ReplayEditorScreen(preview));
        }
        return preview;
    }
}
