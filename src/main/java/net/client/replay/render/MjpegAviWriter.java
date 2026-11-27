package net.client.replay.render;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java Motion-JPEG AVI writer.  No ffmpeg, no AWT and no Android Bitmap:
 * frames are encoded by JpegEncoder and muxed as '00dc' chunks, with optional
 * PCM audio as '01wb' chunks.
 */
public final class MjpegAviWriter implements VideoWriter {
    /**
     * 1.0.2_18：AVI 1.0 的 RIFF 长度和 idx1 偏移都是 32 位，文件一旦超过 4 GB
     * 就会静默溢出、索引错乱（播放器只能播到一半）。这里在逼近上限时主动失败，
     * 交给调用方提示改用 ffmpeg，而不是悄悄写一个坏文件。
     */
    private static final long MAX_AVI_BYTES = 4L * 1024L * 1024L * 1024L - 1024L * 1024L;

    private final RandomAccessFile raf;
    private final File output;
    private final int width;
    private final int height;
    private final double fps;
    private final int totalFrames;
    private final WavAudio audio;
    private final int audioOffsetFrames;
    private final long riffSizePos;
    private final long avihTotalFramesPos;
    private final long moviSizePos;
    private final long moviDataStart;
    private final List<IndexEntry> index = new ArrayList<IndexEntry>();
    private final int maxAudioBytes;
    private final int audioStreams;
    private int framesWritten;
    private boolean closed;

    public MjpegAviWriter(File output, int width, int height, double fps, int totalFrames,
                          File audioFile, double audioOffsetSeconds) throws IOException {
        this.output = output;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.totalFrames = Math.max(1, totalFrames);
        this.audio = WavAudio.read(audioFile);
        this.audioOffsetFrames = audio == null ? 0 : Math.max(0, (int) Math.round(audioOffsetSeconds * audio.sampleRate));
        this.audioStreams = audio == null ? 0 : 1;
        this.maxAudioBytes = audio == null ? 0 : Math.min(1024 * 1024, audio.blockAlign * Math.max(1, audio.sampleRate / 2));
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
        this.raf = new RandomAccessFile(output, "rw");
        this.raf.setLength(0L);
        writeFourCC("RIFF");
        this.riffSizePos = raf.getFilePointer();
        writeInt(0);
        writeFourCC("AVI ");
        writeFourCC("LIST");
        long hdrlSizePos = raf.getFilePointer();
        writeInt(0);
        writeFourCC("hdrl");
        writeFourCC("avih");
        writeInt(56);
        writeInt((int) Math.round(1_000_000.0D / fps));
        writeInt(0);
        writeInt(0);
        writeInt(audio == null ? 0x10 : 0x110);
        this.avihTotalFramesPos = raf.getFilePointer();
        writeInt(totalFrames);
        writeInt(0);
        writeInt(1 + audioStreams);
        writeInt(Math.max(width * height * 3, 1024));
        writeInt(width);
        writeInt(height);
        for (int i = 0; i < 4; i++) writeInt(0);

        writeFourCC("LIST");
        long strlSizePos = raf.getFilePointer();
        writeInt(0);
        writeFourCC("strl");
        writeFourCC("strh");
        writeInt(56);
        writeFourCC("vids");
        writeFourCC("MJPG");
        writeInt(0);
        writeShort(0);
        writeShort(0);
        writeInt(0);
        writeInt(1000);
        writeInt((int) Math.round(fps * 1000.0D));
        writeInt(0);
        writeInt(totalFrames);
        writeInt(Math.max(width * height * 3, 1024));
        writeInt(0);
        writeInt(0);
        writeShort(0); writeShort(0); writeShort(width); writeShort(height);
        writeFourCC("strf");
        writeInt(40);
        writeInt(40); // BITMAPINFOHEADER biSize
        writeInt(width);
        writeInt(height);
        writeShort(1);
        writeShort(24);
        writeFourCC("MJPG");
        writeInt(width * height * 3);
        writeInt(0); writeInt(0); writeInt(0); writeInt(0);
        patchListSize(strlSizePos);

        if (audio != null) {
            writeFourCC("LIST");
            long astrlSizePos = raf.getFilePointer();
            writeInt(0);
            writeFourCC("strl");
            writeFourCC("strh");
            writeInt(56);
            writeFourCC("auds");
            writeInt(0);
            writeInt(0);
            writeShort(0); writeShort(0);
            writeInt(0);
            writeInt(1);
            writeInt(audio.sampleRate);
            writeInt(0);
            writeInt(audio.frames);
            writeInt(maxAudioBytes);
            writeInt(0);
            writeInt(audio.blockAlign);
            writeShort(0); writeShort(0); writeShort(audio.channels); writeShort(audio.bits);
            writeFourCC("strf");
            writeInt(16);
            writeShort(1);
            writeShort(audio.channels);
            writeInt(audio.sampleRate);
            writeInt(audio.sampleRate * audio.blockAlign);
            writeShort(audio.blockAlign);
            writeShort(audio.bits);
            patchListSize(astrlSizePos);
        }
        patchListSize(hdrlSizePos);

        writeFourCC("LIST");
        this.moviSizePos = raf.getFilePointer();
        writeInt(0);
        writeFourCC("movi");
        this.moviDataStart = raf.getFilePointer();
        this.closed = false;
    }

    @Override
    public void writeFrame(ByteBuffer rgba, int width, int height) throws IOException {
        if (closed) throw new IOException("AVI already closed");
        if (width != this.width || height != this.height) throw new IOException("AVI frame size changed");
        if (raf.getFilePointer() >= MAX_AVI_BYTES) {
            throw new IOException("MJPEG AVI 已到 4 GB 上限（AVI 1.0 的 32 位索引限制），"
                    + "继续写会得到无法完整播放的文件。请改用 ffmpeg 编码器，或降低分辨率 / 缩短片段。");
        }
        byte[] jpeg = JpegEncoder.encode(rgba, width, height, 82);
        long offset = raf.getFilePointer() - moviDataStart;
        writeChunk("00dc", jpeg);
        index.add(new IndexEntry("00dc", 0x10, offset, jpeg.length));
        if (audio != null) {
            int start = audioOffsetFrames + (int) ((long) framesWritten * audio.sampleRate / fps);
            int end = audioOffsetFrames + (int) ((long) (framesWritten + 1) * audio.sampleRate / fps);
            start = Math.max(0, Math.min(audio.frames, start));
            end = Math.max(start, Math.min(audio.frames, end));
            int bytes = (end - start) * audio.blockAlign;
            if (bytes > 0) {
                byte[] pcm = new byte[bytes];
                System.arraycopy(audio.pcm, start * audio.blockAlign, pcm, 0, bytes);
                long audioOffset = raf.getFilePointer() - moviDataStart;
                writeChunk("01wb", pcm);
                index.add(new IndexEntry("01wb", 0x0, audioOffset, pcm.length));
            }
        }
        framesWritten++;
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        long moviEnd = raf.getFilePointer();
        // 1.0.2：'movi' LIST 的长度必须只覆盖它自己的 chunk。原实现把 patch 放在写
        // idx1 之后，patchListSize 取的是「当前文件指针」，于是 idx1 那一段（8 + 16×条目）
        // 被算进 movi —— 索引块被当成 movi 的子 chunk，严格的解析器会多读一段垃圾。
        // moviEnd 原来就算好了却没用上，这里改为写 idx1 之前按 moviEnd 回填。
        patchMoviSize(moviEnd);
        writeFourCC("idx1");
        writeInt(index.size() * 16);
        for (IndexEntry entry : index) {
            writeFourCC(entry.id);
            writeInt(entry.flags);
            writeInt((int) entry.offset);
            writeInt(entry.size);
        }
        // RIFF size covers everything after the RIFF size field.
        long end = raf.getFilePointer();
        raf.seek(riffSizePos);
        writeInt((int) (end - 8L));
        raf.seek(avihTotalFramesPos);
        writeInt(framesWritten);
        raf.getFD().sync();
        raf.close();
    }

    @Override
    public String describe() {
        String source = audio == null ? "无声" : "PCM 游戏音轨";
        return "MJPEG AVI (" + source + ") -> " + output.getAbsolutePath();
    }

    private void writeChunk(String id, byte[] data) throws IOException {
        writeFourCC(id);
        writeInt(data.length);
        raf.write(data);
        if ((data.length & 1) != 0) raf.write(0);
    }

    private void patchListSize(long sizePos) throws IOException {
        long end = raf.getFilePointer();
        raf.seek(sizePos);
        writeInt((int) (end - sizePos - 4L));
        raf.seek(end);
    }

    /**
     * 1.0.2：LIST 长度的结束点由调用方给出，不再依赖「当前文件指针」。
     * movi 之后还要写 idx1，若用当前指针会把 idx1 算进 movi。
     */
    private void patchMoviSize(long moviEnd) throws IOException {
        long end = raf.getFilePointer();
        raf.seek(moviSizePos);
        writeInt((int) (moviEnd - moviSizePos - 4L));
        raf.seek(end);
    }

    private void writeFourCC(String s) throws IOException {
        raf.write(s.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeInt(int value) throws IOException {
        raf.write(value & 0xFF);
        raf.write((value >> 8) & 0xFF);
        raf.write((value >> 16) & 0xFF);
        raf.write((value >> 24) & 0xFF);
    }

    private void writeShort(int value) throws IOException {
        raf.write(value & 0xFF);
        raf.write((value >> 8) & 0xFF);
    }

    private static final class IndexEntry {
        final String id;
        final int flags;
        final long offset;
        final int size;

        IndexEntry(String id, int flags, long offset, int size) {
            this.id = id;
            this.flags = flags;
            this.offset = offset;
            this.size = size;
        }
    }
}
