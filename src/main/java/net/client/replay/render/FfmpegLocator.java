package net.client.replay.render;

import net.client.replay.ReplayClient;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds an ffmpeg binary.
 *
 * <p>Android 启动器把 ffmpeg 放在很多奇怪的位置：Pojav 的 FFmpeg 插件通常在
 * {@code /data/app/~~xxxx==&#47;<包名>/lib/arm64/libffmpeg.so}，Zalith/MIO 的插件同理；
 * 老版 Pojav 会在 {@code files/} 里放一个 {@code ffmpeg} 可执行文件。这里既查环境变量
 * 和常见固定路径，也会对几个根目录做有深度上限的扫描，找到就加入候选。
 * 用户仍然可以在配置里填 {@code ffmpegPath} 直接指定。
 */
public final class FfmpegLocator {
    private static final String[] NAMES = {"ffmpeg", "ffmpeg.bin", "libffmpeg.so", "ffmpeg-arm64"};
    /** 扫描上限，防止在 /storage 上刨太久。 */
    private static final int MAX_VISITS = 6000;
    private static List<String> cached;
    private static long cachedNanos;

    private FfmpegLocator() {
    }

    public static String find() {
        List<String> candidates = findAllCached();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** 结果缓存 30 秒：每次渲染都会查一遍，扫描不该重复做。 */
    public static synchronized List<String> findAllCached() {
        long now = System.nanoTime();
        if (cached != null && now - cachedNanos < 30_000_000_000L) {
            return cached;
        }
        cached = findAll();
        cachedNanos = now;
        return cached;
    }

    /** All plausible binaries, in priority order, so RenderJob can retry. */
    public static List<String> findAll() {
        List<String> candidates = new ArrayList<String>();
        String configured = ReplayClient.getConfig() == null ? "" : ReplayClient.getConfig().ffmpegPath;
        addCandidate(candidates, configured == null ? "" : configured.trim());

        // 1. 环境变量：Pojav 插件 / 启动器导出。
        addEnv(candidates, "POJAV_FFMPEG_PATH");
        addEnv(candidates, "FFMPEG_PATH");
        addEnv(candidates, "FFMPEG_BIN");
        String nativeDir = System.getenv("POJAV_NATIVEDIR");
        addCandidate(candidates, nativeDir == null ? null : nativeDir + "/ffmpeg");
        addCandidate(candidates, nativeDir == null ? null : nativeDir + "/ffmpeg.bin");
        addCandidate(candidates, nativeDir == null ? null : nativeDir + "/libffmpeg.so");
        // 2. LD_LIBRARY_PATH 里找 libffmpeg.so。
        String ldPath = System.getenv("LD_LIBRARY_PATH");
        if (ldPath != null) {
            for (String dir : ldPath.split(File.pathSeparator)) {
                addCandidate(candidates, dir + "/libffmpeg.so");
                addCandidate(candidates, dir + "/ffmpeg");
            }
        }
        // 3. 老版 Pojav / 常见固定路径。
        addCandidate(candidates, "/data/data/net.kdt.pojavlaunch.ffmpeg/files/ffmpeg");
        addCandidate(candidates, "/data/user/0/net.kdt.pojavlaunch.ffmpeg/files/ffmpeg");
        addCandidate(candidates, "/data/data/net.kdt.pojavlaunch/files/ffmpeg");
        addCandidate(candidates, "/data/user/0/net.kdt.pojavlaunch/files/ffmpeg");
        addCandidate(candidates, "/system/bin/ffmpeg");
        addCandidate(candidates, "/sbin/ffmpeg");
        addCandidate(candidates, "/system/xbin/ffmpeg");

        // 4. 目录扫描：插件 APK 的 native 库目录、启动器 files 目录。
        scan(candidates, new File("/data/app"), 4);
        String launcher = System.getenv("HOME");
        if (launcher != null) {
            scan(candidates, new File(launcher, "files"), 4);
            scan(candidates, new File(launcher, "components"), 4);
        }
        scan(candidates, new File("/storage/emulated/0/Android/data"), 4);
        scan(candidates, new File("/data/local/tmp"), 3);

        // 5. PATH。
        if (isOnPath("ffmpeg")) addCandidate(candidates, "ffmpeg");
        if (isOnPath("ffmpeg.exe")) addCandidate(candidates, "ffmpeg.exe");
        return candidates;
    }

    /** 深度受限、按名字过滤的广度扫描。 */
    private static void scan(List<String> out, File root, int maxDepth) {
        if (root == null || !root.isDirectory()) return;
        ArrayDeque<Object[]> queue = new ArrayDeque<Object[]>();
        queue.add(new Object[]{root, 0});
        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_VISITS) {
            Object[] item = queue.poll();
            File dir = (File) item[0];
            int depth = (Integer) item[1];
            visited++;
            String lower = dir.getName().toLowerCase(Locale.ROOT);
            // 目录名不相关的浅层目录才继续下潜，避免把整个存储盘刨一遍。
            boolean promising = depth <= 1 || lower.contains("ffmpeg") || lower.contains("mio")
                    || lower.contains("pojav") || lower.contains("zalith") || lower.contains("lib");
            for (String name : NAMES) {
                addCandidate(out, new File(dir, name).getAbsolutePath());
            }
            if (depth >= maxDepth || !promising) continue;
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) queue.add(new Object[]{child, depth + 1});
            }
        }
    }

    private static void addCandidate(List<String> list, String candidate) {
        if (candidate == null || candidate.isEmpty()) return;
        if (list.contains(candidate)) return;
        File file = new File(candidate);
        if (file.isFile() && file.canExecute()) {
            for (String existing : list) {
                if (existing.equals(file.getAbsolutePath())) return;
            }
            list.add(file.getAbsolutePath());
        } else if (isOnPath(candidate)) {
            for (String existing : list) {
                if (existing.equals(candidate)) return;
            }
            list.add(candidate);
        }
    }

    private static void addEnv(List<String> list, String name) {
        String value = System.getenv(name);
        if (value != null && !value.trim().isEmpty()) addCandidate(list, value.trim());
    }

    private static boolean isOnPath(String command) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        String[] parts = path.split(File.pathSeparator);
        for (String part : parts) {
            File file = new File(part, command);
            if (file.isFile() && file.canExecute()) return true;
        }
        return false;
    }
}
