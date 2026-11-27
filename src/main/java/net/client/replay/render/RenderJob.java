package net.client.replay.render;

import net.client.replay.ReplayClient;
import net.client.replay.session.CameraController;
import net.client.replay.session.ReplayReader;
import net.client.replay.session.ReplaySession;
import net.client.replay.util.ReplayPaths;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;

/**
 * Sequential, offline frame renderer. It never sleeps to match wall-clock time:
 * every output frame asks the replay timeline for an exact timestamp. That is
 * what makes 560 fps (or 10000 fps) possible.
 */
public final class RenderJob {
    private final net.minecraft.client.MinecraftClient client;
    private final File replayFile;
    private final double fps;
    private final int width;
    private final int height;
    private final String encoder;
    private final CameraController sourceCamera;
    private final boolean background;
    private final int renderPerspective;
    private final long startTimeNanos;
    private final long endTimeNanos;
    private final long totalFrames;
    private final double durationSeconds;

    private ReplayReader reader;
    private ReplaySession session;
    private FrameCapture capture;
    private VideoWriter writer;
    private long frameIndex;
    private boolean finished;
    private boolean cancelled;
    private long lastProgressNanos;
    private static final int FRAME_WINDOW = 40;
    private final long[] frameTimes = new long[FRAME_WINDOW];
    private int frameTimeIndex;
    private int frameTimeCount;
    private long lastFrameNanos;
    private long startedNanos;
    private long finishedNanos;
    private String outputDescription = "";
    private String lastError = "";
    private boolean disposed;

    public RenderJob(net.minecraft.client.MinecraftClient client, File replayFile, double fps, int width, int height, String encoder) throws IOException {
        this(client, replayFile, fps, width, height, encoder, -1L, -1L, null);
    }

    /** trimStart/trimEnd are absolute replay timestamps; pass -1 for default. */
    public RenderJob(net.minecraft.client.MinecraftClient client, File replayFile, double fps, int width, int height, String encoder,
                     long trimStartNanos, long trimEndNanos) throws IOException {
        this(client, replayFile, fps, width, height, encoder, trimStartNanos, trimEndNanos, null);
    }

    public RenderJob(net.minecraft.client.MinecraftClient client, File replayFile, double fps, int width, int height, String encoder,
                     long trimStartNanos, long trimEndNanos, CameraController sourceCamera) throws IOException {
        this(client, replayFile, fps, width, height, encoder, trimStartNanos, trimEndNanos, sourceCamera, false);
    }

    public RenderJob(net.minecraft.client.MinecraftClient client, File replayFile, double fps, int width, int height, String encoder,
                     long trimStartNanos, long trimEndNanos, CameraController sourceCamera, boolean background) throws IOException {
        this.client = client;
        this.replayFile = replayFile;
        // 1.0.2：fps 直接来自界面输入。0 / NaN / 负数会让 frameTime 里 1e9/fps 变成
        // Infinity、totalFrames 退化，最后写出一个只有 1 帧或长度为 0 的文件，所以在这里收敛。
        if (Double.isNaN(fps) || Double.isInfinite(fps) || fps <= 0.0D) fps = 60.0D;
        this.fps = Math.min(1000.0D, Math.max(1.0D, fps));
        fps = this.fps; // 后续 totalFrames / 文件名都按收敛后的值算
        int capWidth = width;
        int capHeight = height;
        if (!com.mojang.blaze3d.platform.GLX.supportsFbo()) {
            // 没有 FBO 时只能抓窗口画面，尺寸不能超过窗口后备缓冲。
            // 注意：不能用 new Window(client).getWidth()，那返回的是 GUI 缩放后的逻辑尺寸（2800/4=700）。
            try {
                net.minecraft.client.gl.Framebuffer main = client.getFramebuffer();
                int ww = main == null ? 0 : main.textureWidth;
                int wh = main == null ? 0 : main.textureHeight;
                if (ww > 1 && wh > 1) {
                    capWidth = Math.min(width, ww);
                    capHeight = Math.min(height, wh);
                }
            } catch (Throwable ignored) {
                // 拿不到窗口尺寸就按请求值走，FrameCapture 失败时再报错。
            }
        }
        // H.264 要求偶数宽高。
        this.width = Math.max(2, capWidth - (capWidth & 1));
        this.height = Math.max(2, capHeight - (capHeight & 1));
        this.encoder = encoder;
        this.sourceCamera = sourceCamera;
        this.background = background;
        // Only the player camera has a meaningful F5 perspective.  Free/target
        // cameras are already detached, so force true first person there.
        this.renderPerspective = sourceCamera != null && sourceCamera.getMode() == CameraController.MODE_PLAYER
                ? sourceCamera.getPerspective() : 0;
        this.reader = ReplayReader.open(replayFile);
        if (!reader.hasGameJoin()) {
            // 1.0.2_18：构造器里抛异常时把刚打开的文件句柄关掉，原来这里会泄漏。
            try {
                this.reader.close();
            } catch (Throwable ignored) {
            }
            this.reader = null;
            throw new IOException("这个 .lcr 没有 GameJoin 记录，无法载入回放世界；请用新版重新录制。");
        }
        long contentStart = reader.contentStartNanos();
        double skipSeconds = ReplayClient.getConfig() == null ? 1.0D : ReplayClient.getConfig().skipStartSeconds;
        if (skipSeconds < 0.0D || Double.isNaN(skipSeconds) || Double.isInfinite(skipSeconds)) skipSeconds = 0.0D;
        long skipNanos = (long) (skipSeconds * 1.0e9D);
        long endLimit = Math.max(reader.meta.durationNanos, reader.lastPacketTime());
        // 微调：任何入口（列表页渲染 / F9 / 控制台命令）如果没传片段范围，
        // 就回退到轨道文件里存的范围，避免「剪辑台裁好了却导出整段」。
        if (trimStartNanos < 0L || trimEndNanos <= 0L) {
            try {
                net.client.replay.session.CameraPath saved =
                        net.client.replay.session.CameraPath.load(ReplayPaths.cameraFileFor(replayFile));
                if (saved != null) {
                    if (trimStartNanos < 0L && saved.trimStartSeconds > 0.0D) {
                        trimStartNanos = (long) (saved.trimStartSeconds * 1.0e9D);
                    }
                    if (trimEndNanos <= 0L && saved.trimEndSeconds > 0.0D) {
                        trimEndNanos = (long) (saved.trimEndSeconds * 1.0e9D);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        long proposedStart = contentStart + skipNanos;
        if (proposedStart > endLimit - 50000000L) proposedStart = Math.max(contentStart, endLimit - 50000000L);
        if (trimStartNanos >= 0L && trimStartNanos < endLimit) proposedStart = trimStartNanos;
        long proposedEnd = endLimit;
        if (trimEndNanos > proposedStart && trimEndNanos <= endLimit) proposedEnd = trimEndNanos;
        this.startTimeNanos = Math.max(0L, proposedStart);
        this.endTimeNanos = Math.max(this.startTimeNanos + 50000000L, proposedEnd);
        long duration = Math.max(100000000L, this.endTimeNanos - this.startTimeNanos + 100000000L);
        this.durationSeconds = duration / 1.0e9D;
        this.totalFrames = Math.max(1L, (long) Math.ceil(durationSeconds * fps) + 1L);
        ReplayClient.LOGGER.info("[replay] range {}s -> {}s ({}, {} frames @ {} fps)",
                String.format(Locale.ROOT, "%.2f", this.startTimeNanos / 1.0e9D),
                String.format(Locale.ROOT, "%.2f", this.endTimeNanos / 1.0e9D),
                String.format(Locale.ROOT, "%.2f s", this.durationSeconds), this.totalFrames,
                String.format(Locale.ROOT, "%.1f", fps));
    }

    public boolean isFinished() {
        return finished || cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public long getFrameIndex() {
        return frameIndex;
    }

    public long getTotalFrames() {
        return totalFrames;
    }

    public int getPercent() {
        if (finished && !cancelled && lastError.isEmpty() && frameIndex >= totalFrames) return 100;
        return (int) Math.max(0L, Math.min(100L, frameIndex * 100L / totalFrames));
    }

    public long getElapsedMillis() {
        return startedNanos <= 0L ? 0L : (System.nanoTime() - startedNanos) / 1000000L;
    }

    public double getActualFps() {
        long elapsed = System.nanoTime() - startedNanos;
        return elapsed <= 0L || startedNanos <= 0L ? 0.0D : frameIndex / (elapsed / 1.0e9D);
    }

    /** 最近 40 帧的平均帧率；刚开始不够样本时返回 -1。 */
    public double getRecentFps() {
        int n = Math.min(this.frameTimeCount, FRAME_WINDOW);
        if (n < 4) return -1.0D;
        long sum = 0L;
        for (int i = 0; i < n; i++) {
            sum += this.frameTimes[(this.frameTimeIndex - 1 - i + FRAME_WINDOW * 2) % FRAME_WINDOW];
        }
        return sum <= 0L ? -1.0D : n / (sum / 1.0e9D);
    }

    /**
     * 预计剩余时间。旧实现只用「总平均帧率」，前几秒渲染很快时会把 ETA 算得过短，
     * 后面碰上回放世界重建 / 编码器 flush 就会一路被打脸。
     * 现在取「总平均」和「最近 40 帧平均」里更慢的那个，并且前几秒不给数。
     */
    public double getEtaSeconds() {
        if (this.frameIndex < 8 || getElapsedMillis() < 3000L) return -1.0D;
        double overall = getActualFps();
        double recent = getRecentFps();
        double rate = overall;
        if (recent > 0.01D) rate = overall > 0.01D ? Math.min(overall, recent) : recent;
        return rate <= 0.01D ? -1.0D : (totalFrames - frameIndex) / rate;
    }

    public String getOutputDescription() {
        return outputDescription;
    }

    public String getLastError() {
        return lastError;
    }

    public String getEncoder() {
        return encoder;
    }

    public boolean isBackground() {
        return background;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public long getEndTimeNanos() {
        return endTimeNanos;
    }

    public double getFps() {
        return fps;
    }

    public File getReplayFile() {
        return replayFile;
    }

    public void tick(int budgetMillis) {
        if (finished || cancelled) return;
        try {
            if (session == null) initialize();
            if (background && !session.isLiveStateCurrent()) {
                throw new IOException("后台渲染期间玩家已切换世界；任务已停止，避免破坏当前游戏。");
            }
            long budget = Math.max(1L, budgetMillis) * 1000000L;
            long begin = System.nanoTime();
            if (background) {
                // One export frame per client frame keeps input/tick responsive
                // while the user is playing.  A 4K MJPEG frame can still take
                // longer than a frame; that cost is in the encoder, not in an
                // unbounded loop.
                renderOneFrame();
                if (finished || cancelled) return;
            } else {
                do {
                    renderOneFrame();
                    if (finished || cancelled) return;
                } while (System.nanoTime() - begin < budget);
            }
            reportProgress(false);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void initialize() throws IOException {
        startedNanos = System.nanoTime();
        session = new ReplaySession(client, reader);
        if (background) {
            session.startBackground(startTimeNanos);
        } else {
            session.start(startTimeNanos, true);
        }
        if (sourceCamera != null && session.getCameraController() != null) {
            session.getCameraController().copyStateFrom(sourceCamera);
        }
        capture = new FrameCapture(client, width, height);
        if (capture.isWindowMode()) {
            ReplayClient.chat("当前环境没有 FBO，导出改为窗口捕获：" + width + "x" + height);
        }
        String base = stripExtension(replayFile.getName()) + "-" + formatFps(fps) + "fps-" + ReplayPaths.timestampName();
        File audio = reader.getAudioFile();
        if (audio == null && reader.hasSoundEvents()) {
            try {
                File mixed = new File(ReplayPaths.cacheDir(), stripExtension(replayFile.getName()) + "-gameaudio.wav");
                net.client.replay.record.GameAudioMixer mixer = new net.client.replay.record.GameAudioMixer(client);
                if (mixer.mix(reader.readSoundEvents(), reader.meta.durationNanos, mixed)) {
                    audio = mixed;
                    ReplayClient.chat("已从回放中的声音事件重建游戏内音轨。");
                }
            } catch (Throwable t) {
                ReplayClient.LOGGER.warn("Could not rebuild game audio for export", t);
            }
        }
        java.util.List<String> binaries = FfmpegLocator.findAllCached();
        ReplayClient.LOGGER.info("[replay] ffmpeg candidates: {}", binaries);
        if ("mjpeg".equals(encoder) || "avi".equals(encoder)) {
            File output = new File(ReplayPaths.exportsDir(), base + ".avi");
            writer = new MjpegAviWriter(output, width, height, fps, (int) totalFrames, audio, startTimeNanos / 1.0e9D);
            outputDescription = output.getAbsolutePath();
            ReplayClient.chat("MJPEG AVI -> " + output.getName() + "（纯 Java，无需 ffmpeg）");
        } else if ("ffmpeg".equals(encoder)) {
            File output = new File(ReplayPaths.exportsDir(), base + ".mp4");
            for (String binary : binaries) {
                try {
                    writer = new FfmpegWriter(output, width, height, fps, binary,
                            ReplayClient.getConfig().ffmpegArgs, audio, startTimeNanos / 1.0e9D);
                    outputDescription = output.getAbsolutePath();
                    ReplayClient.chat("Rendering " + totalFrames + " frames at " + formatFps(fps) + " fps -> " + output.getName());
                    break;
                } catch (Throwable t) {
                    ReplayClient.LOGGER.warn("ffmpeg candidate failed: {}", binary, t);
                    writer = null;
                }
            }
            if (writer == null) {
                // 没有 ffmpeg 就别再吐一堆 PNG 了：直接用纯 Java 写成 MJPEG AVI，是能直接播放的视频。
                File aviFallback = new File(ReplayPaths.exportsDir(), base + ".avi");
                writer = new MjpegAviWriter(aviFallback, width, height, fps, (int) totalFrames, audio, startTimeNanos / 1.0e9D);
                outputDescription = aviFallback.getAbsolutePath();
                ReplayClient.chat("未找到可用的 ffmpeg，改为纯 Java MJPEG AVI 输出：" + aviFallback.getName());
                ReplayClient.LOGGER.info("[replay] ffmpeg unavailable, falling back to MJPEG AVI");
            }
        } else {
            File dir = new File(ReplayPaths.exportsDir(), base + "-frames");
            // 1.0.2：PNG 链路原来没把片段起点传给 ffmpeg，裁剪后导出的画面从片段起点开始，
            // 音轨却仍从回放 0 秒开始 —— 画面和声音对不上。这里补上和另外两条链路一致的偏移。
            writer = new PngSequenceWriter(dir, fps, audio,
                    new File(ReplayPaths.exportsDir(), base + ".mp4"),
                    binaries.isEmpty() ? null : binaries.get(0),
                    ReplayClient.getConfig().keepFrames,
                    startTimeNanos / 1.0e9D);
            outputDescription = dir.getAbsolutePath();
            ReplayClient.chat("Rendering " + totalFrames + " frames at " + formatFps(fps) + " fps -> "
                    + base + ".mp4 (PNG frames are temporary)");
        }
        ReplayClient.chat("Camera: " + (session.getCameraController() != null && session.getCameraController().hasPath()
                ? "keyframe track" : "player view") + " | " + width + "x" + height);
    }

    private void renderOneFrame() throws IOException {
        long frameTime = startTimeNanos + (long) (frameIndex * 1000000000.0D / fps);
        boolean reachedEnd = false;
        boolean bg = background && session.isBackground();
        if (bg) session.beginBackgroundFrame();
        int oldPerspective = client.options.perspective;
        try {
            // Set the requested F5 view before advanceTo: CameraController and
            // PlayerEntityRenderer both read it while choosing what to hide.
            // A keyframe track can switch between player/free/other-player
            // views, so the perspective must be sampled per frame too.
            int framePerspective = renderPerspective;
            CameraController camera = session.getCameraController();
            if (camera != null) framePerspective = camera.getPerspectiveAt(frameTime / 1.0e9D);
            client.options.perspective = framePerspective;
            session.advanceTo(frameTime);
            long chunkBudget = ReplayClient.getConfig() == null ? 2000000L : ReplayClient.getConfig().chunkBuildBudgetMs * 1000000L;
            long chunkDeadline = System.nanoTime() + (frameIndex < 30L ? Math.max(chunkBudget, 30000000L) : chunkBudget);
            capture.capture(1.0F, chunkDeadline);
            ByteBuffer buffer = capture.getBuffer();
            writer.writeFrame(buffer, width, height);
            frameIndex++;
            long frameNow = System.nanoTime();
            if (this.lastFrameNanos > 0L) {
                this.frameTimes[this.frameTimeIndex] = frameNow - this.lastFrameNanos;
                this.frameTimeIndex = (this.frameTimeIndex + 1) % FRAME_WINDOW;
                if (this.frameTimeCount < FRAME_WINDOW) this.frameTimeCount++;
            }
            this.lastFrameNanos = frameNow;
            reachedEnd = frameIndex >= totalFrames;
        } finally {
            client.options.perspective = oldPerspective;
            if (bg) session.endBackgroundFrame();
        }
        if (reachedEnd) finish();
    }

    private void reportProgress(boolean force) {
        long now = System.nanoTime();
        if (!force && now - lastProgressNanos < 5000000000L) return;
        lastProgressNanos = now;
        double percent = frameIndex * 100.0D / totalFrames;
        long elapsed = now - startedNanos;
        double fpsActual = elapsed <= 0L ? 0.0D : frameIndex / (elapsed / 1.0e9D);
        ReplayClient.chat(String.format(Locale.US, "Render %.1f%% (%d/%d), %.1f frames/s",
                percent, frameIndex, totalFrames, fpsActual));
    }

    private void finish() throws IOException {
        finished = true;
        finishedNanos = System.nanoTime();
        if (writer != null) {
            if (writer instanceof PngSequenceWriter) {
                ReplayClient.chat("PNG 帧已写完，正在调用 ffmpeg 合成 MP4（可能需要几十秒）...");
            }
            writer.close();
            String description = writer.describe();
            if (description != null && !description.isEmpty()) outputDescription = description;
            ReplayClient.chat("Render finished: " + outputDescription);
        }
        closeCapture();
        if (ReplayClient.getRenderManager() != null) {
            ReplayClient.getRenderManager().onJobFinished(this);
        }
        if (background) dispose();
    }

    /**
     * Cancel keeps the encoder's file and finalizes it.  The replay world is
     * deliberately not torn down here: the progress screen is still open and
     * Minecraft's render loop would touch a null player on the next frame.
     * dispose() runs only after that screen has been replaced.
     */
    public void cancel() {
        if (finished || cancelled) return;
        cancelled = true;
        finishedNanos = System.nanoTime();
        try {
            if (writer != null) writer.close();
            if (writer != null) outputDescription = writer.describe();
        } catch (Throwable t) {
            ReplayClient.LOGGER.warn("Could not finalize partial export", t);
        }
        closeCapture();
        String kept = outputDescription == null || outputDescription.isEmpty()
                ? "（没有开始写文件）" : outputDescription;
        ReplayClient.chat("Render cancelled at frame " + frameIndex + "/" + totalFrames + "; kept: " + kept);
        if (ReplayClient.getRenderManager() != null) {
            ReplayClient.getRenderManager().onJobFinished(this);
        }
        if (background) dispose();
    }

    private void fail(Throwable t) {
        finished = true;
        finishedNanos = System.nanoTime();
        lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
        ReplayClient.LOGGER.error("Render failed", t);
        ReplayClient.chat("Render failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        try {
            if (writer != null) writer.close();
        } catch (Throwable ignored) {
        }
        closeCapture();
        if (ReplayClient.getRenderManager() != null) {
            ReplayClient.getRenderManager().onJobFinished(this);
        }
        if (background) dispose();
    }

    /** Close the replay world only after a different screen is active. */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        closeCapture();
        try {
            if (session != null) session.close();
        } catch (Throwable ignored) {
        }
        session = null;
        try {
            if (reader != null) reader.close();
        } catch (Throwable ignored) {
        }
        reader = null;
        writer = null;
    }

    private void closeCapture() {
        try {
            if (capture != null) capture.close();
        } catch (Throwable ignored) {
        }
        capture = null;
    }

    public long getFinishedNanos() {
        return finishedNanos;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String formatFps(double fps) {
        if (Math.abs(fps - Math.round(fps)) < 0.0001D) return Long.toString(Math.round(fps));
        return String.format(Locale.US, "%.2f", fps);
    }
}
