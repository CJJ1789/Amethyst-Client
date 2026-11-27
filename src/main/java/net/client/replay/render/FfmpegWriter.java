package net.client.replay.render;

import net.client.replay.ReplayClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pipes raw BGRA/RGBA frames into an ffmpeg process. This is the same basic
 * mechanism every high-framerate Minecraft exporter uses; the code here is
 * written from scratch and has no ReplayMod dependency.
 */
public final class FfmpegWriter implements VideoWriter {
    private final Process process;
    private final OutputStream stdin;
    private final String description;
    private final Thread stderrThread;
    private final StringBuilder stderr = new StringBuilder();
    private final byte[] frameBuffer;
    private boolean closed;

    public FfmpegWriter(File output, int width, int height, double fps, String ffmpegBinary,
                        String extraArgs) throws IOException {
        this(output, width, height, fps, ffmpegBinary, extraArgs, null, 0.0D);
    }

    public FfmpegWriter(File output, int width, int height, double fps, String ffmpegBinary,
                        String extraArgs, File audioFile) throws IOException {
        this(output, width, height, fps, ffmpegBinary, extraArgs, audioFile, 0.0D);
    }

    public FfmpegWriter(File output, int width, int height, double fps, String ffmpegBinary,
                        String extraArgs, File audioFile, double audioOffsetSeconds) throws IOException {
        List<String> command = new ArrayList<String>();
        command.add(ffmpegBinary);
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-f");
        command.add("rawvideo");
        command.add("-pix_fmt");
        command.add("rgba");
        command.add("-s");
        command.add(width + "x" + height);
        command.add("-r");
        command.add(String.format(Locale.US, "%.6f", fps));
        command.add("-i");
        command.add("-");
        boolean withAudio = audioFile != null && audioFile.isFile() && audioFile.length() > 44L;
        if (withAudio) {
            if (audioOffsetSeconds > 0.0D) {
                command.add("-ss");
                command.add(String.format(Locale.US, "%.6f", audioOffsetSeconds));
            }
            command.add("-i");
            command.add(audioFile.getAbsolutePath());
        }
        command.add("-vf");
        command.add("vflip");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-crf");
        command.add("18");
        command.add("-pix_fmt");
        command.add("yuv420p");
        if (withAudio) {
            command.add("-map");
            command.add("0:v:0");
            command.add("-map");
            command.add("1:a:0");
            command.add("-c:a");
            command.add("aac");
            command.add("-b:a");
            command.add("128k");
            // Do not use -shortest: a game-audio track is often a little
            // shorter than the video. -shortest makes ffmpeg exit early and
            // the renderer then dies with "Broken pipe" on the last frames.
        } else {
            command.add("-an");
        }
        command.add("-movflags");
        command.add("+faststart");
        if (extraArgs != null && !extraArgs.trim().isEmpty()) {
            for (String arg : extraArgs.trim().split("\\s+")) {
                command.add(arg);
            }
        }
        command.add(output.getAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        // Pojav's FFmpeg plugin is shipped as libffmpeg.so.  Its dependent
        // libav*.so files live next to it; without this path the executable
        // exits before reading a single frame.
        File binaryFile = new File(ffmpegBinary);
        String parent = binaryFile.getParentFile() == null ? null : binaryFile.getParentFile().getAbsolutePath();
        if (parent != null) {
            String oldLibraryPath = builder.environment().get("LD_LIBRARY_PATH");
            if (oldLibraryPath == null || !oldLibraryPath.contains(parent)) {
                builder.environment().put("LD_LIBRARY_PATH",
                        oldLibraryPath == null || oldLibraryPath.isEmpty() ? parent : parent + ":" + oldLibraryPath);
            }
            String oldPath = builder.environment().get("PATH");
            if (oldPath == null || !oldPath.contains(parent)) {
                builder.environment().put("PATH",
                        oldPath == null || oldPath.isEmpty() ? parent : parent + ":" + oldPath);
            }
        }
        this.process = builder.start();
        this.stdin = process.getOutputStream();
        this.frameBuffer = new byte[width * height * 4];
        this.description = "ffmpeg " + width + "x" + height + " @ " + fps + " fps -> " + output.getAbsolutePath();
        this.stderrThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (stderr) {
                            if (stderr.length() > 8192) stderr.setLength(0);
                            stderr.append(line).append('\n');
                        }
                        if (ReplayClient.getConfig() != null && ReplayClient.getConfig().debug) {
                            ReplayClient.LOGGER.info("[ffmpeg] {}", line);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "ReplayClient-ffmpeg-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();
        // Some Android launchers return a process object whose stdin is a
        // NullOutputStream when the binary is missing or not executable.  Give
        // the process a moment to fail, then surface its actual stderr.
        try {
            Thread.sleep(220L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (!this.process.isAlive()) {
            try {
                Thread.sleep(80L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("ffmpeg 启动后立即退出: " + stderrText() + "（检查 ffmpegPath 和编码器参数）");
        }
    }

    private String stderrText() {
        synchronized (stderr) {
            String value = stderr.toString().trim();
            return value.isEmpty() ? "没有 stderr 输出" : value;
        }
    }

    @Override
    public void writeFrame(ByteBuffer rgba, int width, int height) throws IOException {
        if (closed) throw new IOException("Writer already closed");
        if (width * height * 4 != frameBuffer.length) throw new IOException("Frame size changed");
        rgba.rewind();
        rgba.get(frameBuffer);
        stdin.write(frameBuffer);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            stdin.close();
        } catch (Throwable ignored) {
        }
        try {
            int exit = process.waitFor();
            if (exit != 0) {
                String error;
                synchronized (stderr) {
                    error = stderr.toString();
                }
                throw new IOException("ffmpeg exited with " + exit + ": " + error);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for ffmpeg", e);
        }
    }

    @Override
    public String describe() {
        return description;
    }
}
