package net.client.replay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.client.replay.ReplayClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * 回放/录制子系统的 JSON 配置，保存在 {@code config/client-replay.json}。
 * 所有字段都做了范围收敛，写盘使用 UTF-8 + 临时文件原子替换。
 */
public class ReplayConfig {
    /** 每秒实体快照数，0 = 只记录网络包。60 在 1.8.9 上已经足够顺滑。 */
    public int sampleHz = 60;

    /** 默认导出帧率。上限不人为限制，但默认 60 而不是 240，避免一开渲染就生成超大文件。 */
    public double defaultFps = 60.0D;
    public int defaultWidth = 1280;
    public int defaultHeight = 720;

    /** ffmpeg | png | mjpeg。 */
    public String encoder = "ffmpeg";

    /** 留空表示自动探测；PojavLauncher 上可以指向 FFmpeg 插件里的可执行文件。 */
    public String ffmpegPath = "";

    /** 插在输出文件之前的额外 FFmpeg 参数。 */
    public String ffmpegArgs = "";

    /** 每个客户端帧最多花多少毫秒渲染导出帧。 */
    public int renderBudgetMs = 25;

    /** 导出时跳过首个区块包之后的若干秒，避开世界加载的抖动。 */
    public double skipStartSeconds = 1.0D;

    /** 每帧允许 GameRenderer 上传区块的毫秒预算。 */
    public int chunkBuildBudgetMs = 2;

    /** 导出画面里是否显示第一人称手。 */
    public boolean showHand = true;

    /** 导出画面里是否叠加 HUD（物品栏 / 血量 / 准星）。 */
    public boolean showHud = true;

    /** 根据声音事件重建游戏内音频轨。 */
    public boolean recordGameAudio = true;

    /** 导出成 MP4 后是否保留中间 PNG 帧目录。 */
    public boolean keepFrames = false;

    /** 进入世界后自动开始录制。默认关闭。 */
    public boolean autoRecord = false;

    /** LWJGL 键码：F7 开始录制、F8 停止、F9 渲染最新回放。 */
    public int hotkeyRecord = 65;
    public int hotkeyRender = 67;
    public int hotkeyStop = 66;

    /** 回放控制台快捷键，-1 = 关闭（标题界面按钮与客户端设置里都能打开）。 */
    public int hotkeyGui = -1;

    /** 可选相机关键帧文件（相对回放文件）。 */
    public String cameraFile = "";

    public boolean debug = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File file;

    public static synchronized ReplayConfig load() {
        File dir = new File(ReplayClient.gameDir(), "config");
        file = new File(dir, "client-replay.json");
        ReplayConfig config = new ReplayConfig();
        if (file.isFile()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                ReplayConfig loaded = GSON.fromJson(reader, ReplayConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Throwable t) {
                ReplayClient.LOGGER.warn("无法读取回放配置，使用默认值", t);
                config = new ReplayConfig();
            }
        }
        config.sanitize();
        config.save();
        return config;
    }

    public void sanitize() {
        sampleHz = clamp(sampleHz, 0, 240);
        if (!finite(defaultFps) || defaultFps <= 0.0D) {
            defaultFps = 60.0D;
        }
        defaultFps = Math.min(1000.0D, defaultFps);
        defaultWidth = even(clamp(defaultWidth, 64, 7680));
        defaultHeight = even(clamp(defaultHeight, 64, 4320));
        encoder = normalizeEncoder(encoder);
        if (ffmpegPath == null) ffmpegPath = "";
        if (ffmpegArgs == null) ffmpegArgs = "";
        renderBudgetMs = clamp(renderBudgetMs, 1, 200);
        if (!finite(skipStartSeconds) || skipStartSeconds < 0.0D) {
            skipStartSeconds = 0.0D;
        }
        skipStartSeconds = Math.min(30.0D, skipStartSeconds);
        chunkBuildBudgetMs = clamp(chunkBuildBudgetMs, 0, 100);
        if (hotkeyGui < -1) hotkeyGui = -1;
        hotkeyRecord = validKey(hotkeyRecord, 65);
        hotkeyRender = validKey(hotkeyRender, 67);
        hotkeyStop = validKey(hotkeyStop, 66);
        if (cameraFile == null) cameraFile = "";
    }

    /** 写入磁盘：UTF-8 + 先写临时文件再原子替换。 */
    public synchronized void save() {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(tmp.toPath()), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            ReplayClient.LOGGER.warn("无法写入回放配置", t);
        }
    }

    public static String normalizeEncoder(String value) {
        String v = value == null ? "ffmpeg" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("avi".equals(v) || "mjpeg-avi".equals(v)) v = "mjpeg";
        if (!"ffmpeg".equals(v) && !"png".equals(v) && !"mjpeg".equals(v)) v = "ffmpeg";
        return v;
    }

    public static String nextEncoder(String current) {
        if ("ffmpeg".equals(current)) return "png";
        if ("png".equals(current)) return "mjpeg";
        return "ffmpeg";
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int even(int v) {
        return (v & 1) != 0 ? v - 1 : v;
    }

    private static int validKey(int key, int fallback) {
        return key < 0 || key > 255 ? fallback : key;
    }
}
