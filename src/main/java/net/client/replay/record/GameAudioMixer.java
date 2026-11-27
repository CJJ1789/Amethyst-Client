package net.client.replay.record;

import net.client.replay.ReplayClient;
import net.client.replay.util.ReplayPaths;
import paulscode.sound.SoundBuffer;
import paulscode.sound.codecs.CodecJOrbis;

import javax.sound.sampled.AudioFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Offline mixer for sounds that were actually played while recording.  This is
 * not a system-audio recorder: it reconstructs the original player's game mix
 * from the sound files and the positions/volumes captured at play time.
 */
public final class GameAudioMixer {
    private static final int SAMPLE_RATE = 44100;

    private final net.minecraft.client.MinecraftClient client;
    private final Map<String, DecodedSound> cache = new HashMap<String, DecodedSound>();

    public GameAudioMixer(net.minecraft.client.MinecraftClient client) {
        this.client = client;
    }

    /**
     * Streaming mix: only one second of PCM is held in RAM at a time, so a
     * multi-hour recording does not allocate two float arrays the length of
     * the whole replay.  Events are sorted; only sounds that overlap the
     * current one-second window are visited.
     */
    public boolean mix(List<SoundEventRecord> events, long durationNanos, File out) {
        if (events == null || events.isEmpty()) return false;
        long samplesLong = (long) ((durationNanos / 1.0e9D) * SAMPLE_RATE) + 1L;
        if (samplesLong <= 0L || samplesLong > (Integer.MAX_VALUE - 44) / 4L) return false;
        final int samples = (int) samplesLong;
        final int chunkFrames = SAMPLE_RATE; // one second
        List<SoundEventRecord> sorted = new ArrayList<SoundEventRecord>(events);
        Collections.sort(sorted, new Comparator<SoundEventRecord>() {
            @Override
            public int compare(SoundEventRecord a, SoundEventRecord b) {
                return Long.compare(a.timeNanos, b.timeNanos);
            }
        });
        try (RandomAccessFile raf = new RandomAccessFile(out, "rw")) {
            raf.setLength(0L);
            int dataBytes = samples * 4;
            raf.writeBytes("RIFF");
            raf.writeInt(Integer.reverseBytes(36 + dataBytes));
            raf.writeBytes("WAVEfmt ");
            raf.writeInt(Integer.reverseBytes(16));
            raf.writeShort(Short.reverseBytes((short) 1));
            raf.writeShort(Short.reverseBytes((short) 2));
            raf.writeInt(Integer.reverseBytes(SAMPLE_RATE));
            raf.writeInt(Integer.reverseBytes(SAMPLE_RATE * 2 * 2));
            raf.writeShort(Short.reverseBytes((short) 4));
            raf.writeShort(Short.reverseBytes((short) 16));
            raf.writeBytes("data");
            raf.writeInt(Integer.reverseBytes(dataBytes));

            float[] left = new float[chunkFrames];
            float[] right = new float[chunkFrames];
            List<ActiveSound> active = new ArrayList<ActiveSound>();
            int next = 0;
            int written = 0;
            while (written < samples) {
                int end = Math.min(samples, written + chunkFrames);
                Arrays.fill(left, 0.0F);
                Arrays.fill(right, 0.0F);
                while (next < sorted.size() && startFrame(sorted.get(next)) < end) {
                    SoundEventRecord event = sorted.get(next++);
                    DecodedSound sound = decode(event.soundPath);
                    if (sound == null || sound.samples.length == 0) continue;
                    int frames = sound.samples.length / sound.channels;
                    if (frames <= 0) continue;
                    ActiveSound a = new ActiveSound();
                    a.event = event;
                    a.sound = sound;
                    a.start = startFrame(event);
                    a.frames = frames;
                    if (a.start + frames > written) active.add(a);
                }
                Iterator<ActiveSound> it = active.iterator();
                while (it.hasNext()) {
                    ActiveSound a = it.next();
                    long soundEnd = a.start + a.frames;
                    if (!a.event.repeatable && soundEnd <= written) {
                        it.remove();
                        continue;
                    }
                    long from = Math.max((long) written, a.start);
                    long to = a.event.repeatable ? end : Math.min((long) end, soundEnd);
                    if (from >= to) continue;
                    float gain = Math.max(0.0F, Math.min(1.0F, a.event.volume));
                    for (long global = from; global < to; global++) {
                        long sourceFrame = (global - a.start) % a.frames;
                        int src = (int) sourceFrame * a.sound.channels;
                        float l = a.sound.samples[src];
                        float r = a.sound.channels > 1 ? a.sound.samples[src + 1] : l;
                        int dst = (int) (global - written);
                        left[dst] += l * gain;
                        right[dst] += r * gain;
                    }
                }
                int count = end - written;
                ByteBuffer pcm = ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < count; i++) {
                    short l = (short) Math.max(-32768, Math.min(32767, (int) (left[i] * 32767.0F)));
                    short r = (short) Math.max(-32768, Math.min(32767, (int) (right[i] * 32767.0F)));
                    pcm.putShort(l);
                    pcm.putShort(r);
                }
                raf.write(pcm.array());
                written = end;
            }
            // Header was written with the final sizes; nothing to patch.
            return out.isFile() && out.length() > 44L;
        } catch (Throwable t) {
            ReplayClient.LOGGER.warn("Game audio mix failed", t);
            return false;
        }
    }

    private static int startFrame(SoundEventRecord event) {
        long start = (long) ((event.timeNanos / 1.0e9D) * SAMPLE_RATE);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, start));
    }

    private static final class ActiveSound {
        SoundEventRecord event;
        DecodedSound sound;
        int start;
        int frames;
    }

    private DecodedSound decode(String path) {
        if (path == null || path.isEmpty()) return null;
        DecodedSound cached = cache.get(path);
        if (cached != null) return cached;
        File ogg = null;
        try {
            net.minecraft.util.Identifier id = new net.minecraft.util.Identifier(path);
            File cacheDir = new File(ReplayPaths.cacheDir(), "sounds");
            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) return null;
            ogg = new File(cacheDir, Integer.toHexString(path.hashCode()) + ".ogg");
            try (InputStream in = client.getResourceManager().getResource(id).getInputStream();
                 OutputStream out = new FileOutputStream(ogg)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            }
            CodecJOrbis codec = new CodecJOrbis();
            URL url = ogg.toURI().toURL();
            SoundBuffer buffer;
            if (!codec.initialize(url)) {
                codec.cleanup();
                return null;
            }
            // 1.0.2：readAll() 抛异常时原来不会 cleanup()，JOrbis 的流和
            // 临时缓冲会留到 GC；改成 finally 里收尾。
            try {
                buffer = codec.readAll();
            } finally {
                codec.cleanup();
            }
            if (buffer == null || buffer.audioData == null || buffer.audioFormat == null) return null;
            DecodedSound decoded = convert(buffer, SAMPLE_RATE);
            cache.put(path, decoded);
            return decoded;
        } catch (Throwable t) {
            ReplayClient.LOGGER.debug("Could not decode sound {}", path, t);
            return null;
        } finally {
            if (ogg != null) ogg.delete();
        }
    }

    private static DecodedSound convert(SoundBuffer buffer, int targetRate) {
        AudioFormat format = buffer.audioFormat;
        int channels = Math.max(1, format.getChannels());
        int sourceRate = Math.round(format.getSampleRate());
        if (sourceRate <= 0) sourceRate = targetRate;
        int frameSize = Math.max(1, format.getFrameSize());
        int sampleBytes = Math.max(1, format.getSampleSizeInBits() / 8);
        int frames = Math.max(0, buffer.audioData.length / frameSize);
        float[] source = new float[frames * channels];
        for (int i = 0; i < frames; i++) {
            int base = i * frameSize;
            for (int c = 0; c < channels; c++) {
                int off = base + c * sampleBytes;
                int value;
                if (sampleBytes == 2) {
                    value = format.isBigEndian()
                            ? ((buffer.audioData[off] << 8) | (buffer.audioData[off + 1] & 0xFF))
                            : ((buffer.audioData[off + 1] << 8) | (buffer.audioData[off] & 0xFF));
                } else if (sampleBytes == 1) {
                    // 8-bit WAV 是无符号的；先减 128 再放大到 16-bit 量级。
                    value = ((buffer.audioData[off] & 0xFF) - 128) << 8;
                } else if (sampleBytes == 3) {
                    // 1.0.2：24-bit 原来只取首个字节 << 8，等于丢掉了低 16 位，
                    // 声音会变成极小的量化台阶 / 几乎听不见。按端序拼完整 24 位再取高 16 位。
                    byte b0 = buffer.audioData[off];
                    byte b1 = buffer.audioData[off + 1];
                    byte b2 = buffer.audioData[off + 2];
                    int v24 = format.isBigEndian()
                            ? ((b0 << 16) | ((b1 & 0xFF) << 8) | (b2 & 0xFF))
                            : ((b2 << 16) | ((b1 & 0xFF) << 8) | (b0 & 0xFF));
                    value = v24 >> 8;
                } else {
                    value = buffer.audioData[off] << 8;
                }
                source[i * channels + c] = value / 32768.0F;
            }
        }
        int targetFrames = (int) Math.max(1L, (long) frames * targetRate / sourceRate);
        float[] out = new float[targetFrames * channels];
        for (int i = 0; i < targetFrames; i++) {
            double srcPos = (double) i * sourceRate / targetRate;
            int i0 = (int) Math.floor(srcPos);
            int i1 = Math.min(frames - 1, i0 + 1);
            float frac = (float) (srcPos - i0);
            if (i0 >= frames) i0 = frames - 1;
            if (i0 < 0) i0 = 0;
            for (int c = 0; c < channels; c++) {
                float a = source[i0 * channels + c];
                float b = source[i1 * channels + c];
                out[i * channels + c] = a + (b - a) * frac;
            }
        }
        return new DecodedSound(out, channels);
    }

    private static final class DecodedSound {
        final float[] samples;
        final int channels;

        DecodedSound(float[] samples, int channels) {
            this.samples = samples;
            this.channels = channels;
        }
    }
}
