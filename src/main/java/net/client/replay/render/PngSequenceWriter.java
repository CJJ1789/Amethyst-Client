package net.client.replay.render;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Minimal PNG writer (RGBA, no filtering, zlib via java.util.zip). Deliberately
 * avoids java.awt/ImageIO because PojavLauncher's Android runtime does not
 * reliably ship AWT.
 */
public final class PngSequenceWriter implements VideoWriter {
    private final File directory;
    private final File output;
    private final double fps;
    private final double audioOffsetSeconds;
    private final File audioFile;
    private final String ffmpegBinary;
    private final boolean keepFrames;
    /** 1.0.2：每帧重新分配 width*height*4+height 的原始缓冲在 4K 下是 33 MB/帧的垃圾，
     *  改为按需扩容复用。 */
    private byte[] rawBuffer;
    private int rawWidth = -1;
    private int rawHeight = -1;
    private int index;
    private boolean closed;
    private String description;

    public PngSequenceWriter(File directory) throws IOException {
        this(directory, 60.0D, null, new File(directory.getAbsolutePath() + ".mp4"), null, true, 0.0D);
    }

    public PngSequenceWriter(File directory, double fps, File audioFile, File output,
                             String ffmpegBinary, boolean keepFrames) throws IOException {
        this(directory, fps, audioFile, output, ffmpegBinary, keepFrames, 0.0D);
    }

    public PngSequenceWriter(File directory, double fps, File audioFile, File output,
                             String ffmpegBinary, boolean keepFrames, double audioOffsetSeconds) throws IOException {
        this.directory = directory;
        this.fps = fps;
        this.audioOffsetSeconds = audioOffsetSeconds;
        this.audioFile = audioFile;
        this.output = output;
        this.ffmpegBinary = ffmpegBinary;
        this.keepFrames = keepFrames;
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create " + directory);
        }
        this.description = "PNG sequence in " + directory.getAbsolutePath();
    }

    @Override
    public void writeFrame(ByteBuffer rgba, int width, int height) throws IOException {
        if (width <= 0 || height <= 0) throw new IOException("Invalid frame size " + width + "x" + height);
        int needed = (width * 4 + 1) * height;
        if (rawBuffer == null || rawBuffer.length < needed || width != rawWidth || height != rawHeight) {
            rawBuffer = new byte[needed];
            rawWidth = width;
            rawHeight = height;
        }
        byte[] raw = rawBuffer;
        int out = 0;
        for (int y = height - 1; y >= 0; y--) {
            raw[out++] = 0; // PNG filter: none
            int rowStart = y * width * 4;
            for (int x = 0; x < width * 4; x++) {
                raw[out++] = rgba.get(rowStart + x);
            }
        }
        File file = new File(directory, String.format("frame_%06d.png", index));
        writePng(file, width, height, raw, needed);
        index++;
    }

    private static void writePng(File file, int width, int height, byte[] raw, int rawLength) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(raw, 0, rawLength);
            deflater.finish();
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                compressed.write(buffer, 0, count);
            }
        } finally {
            deflater.end();
        }
        byte[] idat = compressed.toByteArray();

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream header = new DataOutputStream(ihdr);
            header.writeInt(width);
            header.writeInt(height);
            header.writeByte(8); // bit depth
            header.writeByte(6); // RGBA
            header.writeByte(0);
            header.writeByte(0);
            header.writeByte(0);
            header.flush();
            writeChunk(out, "IHDR", ihdr.toByteArray());
            writeChunk(out, "IDAT", idat);
            writeChunk(out, "IEND", new byte[0]);
        }
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        out.writeInt(data.length);
        byte[] typeBytes = type.getBytes("US-ASCII");
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if (index <= 0) return;
        if (output == null || ffmpegBinary == null || ffmpegBinary.isEmpty()) {
            throw new IOException("未找到 ffmpeg：PNG 帧已保留在 " + directory.getAbsolutePath()
                    + "。安装 ffmpeg 或填写 ffmpegPath 后即可自动合成 MP4。");
        }
        encodeVideo();
    }

    private void encodeVideo() throws IOException {
        List<String> command = new ArrayList<String>();
        command.add(ffmpegBinary);
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-framerate");
        command.add(String.format(Locale.US, "%.6f", fps));
        command.add("-start_number");
        command.add("0");
        command.add("-i");
        command.add(new File(directory, "frame_%06d.png").getAbsolutePath());
        boolean withAudio = audioFile != null && audioFile.isFile() && audioFile.length() > 44L;
        if (withAudio) {
            if (audioOffsetSeconds > 0.0D) {
                command.add("-ss");
                command.add(String.format(Locale.US, "%.6f", audioOffsetSeconds));
            }
            command.add("-i");
            command.add(audioFile.getAbsolutePath());
        }
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-crf");
        command.add("18");
        command.add("-pix_fmt");
        command.add("yuv420p");
        if (withAudio) {
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
        command.add(output.getAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        File binary = new File(ffmpegBinary);
        String parent = binary.getParentFile() == null ? null : binary.getParentFile().getAbsolutePath();
        if (parent != null) {
            String old = builder.environment().get("LD_LIBRARY_PATH");
            builder.environment().put("LD_LIBRARY_PATH", old == null || old.isEmpty() ? parent : parent + ":" + old);
            String oldPath = builder.environment().get("PATH");
            builder.environment().put("PATH", oldPath == null || oldPath.isEmpty() ? parent : parent + ":" + oldPath);
        }
        Process process = builder.start();
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (log.length() < 8192) log.append(line).append('\n');
            }
        } catch (Throwable ignored) {
        }
        try {
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("PNG 合成 MP4 失败（ffmpeg exit " + exit + "）：" + log.toString().trim()
                        + "；PNG 帧保留在 " + directory.getAbsolutePath());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待 ffmpeg 合成时被中断", e);
        }
        this.description = "MP4 (" + (withAudio ? "含麦克风音轨" : "无声") + ") -> " + output.getAbsolutePath();
        if (!keepFrames) deleteTree(directory);
    }

    private static void deleteTree(File file) {
        if (file == null) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    @Override
    public String describe() {
        return description;
    }

    public int getIndex() {
        return index;
    }
}
