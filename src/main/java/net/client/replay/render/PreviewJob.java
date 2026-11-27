package net.client.replay.render;

import net.client.replay.ReplayClient;
import net.client.replay.session.ReplayReader;
import net.client.replay.session.ReplaySession;
import java.io.File;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;

/** Real-time visual preview of a replay (no encoding, no frame capture). */
public final class PreviewJob {
    private final net.minecraft.client.MinecraftClient client;
    private final ReplayReader reader;
    private final File file;
    private final ReplaySession session;
    private final long startTimeNanos;
    private final long endTimeNanos;
    /** 剪辑片段范围：循环/重新播放时从 clipStart 起，播到 clipEnd 回到起点。 */
    private long clipStartNanos;
    private long clipEndNanos;
    private long currentTimeNanos;
    private long lastWallNanos;
    private long lastChatNanos;
    private boolean paused;
    private boolean finished;
    private boolean loop;

    public PreviewJob(net.minecraft.client.MinecraftClient client, File file) throws IOException {
        this.client = client;
        this.file = file;
        this.reader = ReplayReader.open(file);
        long start = reader.contentStartNanos();
        long end = Math.max(reader.meta.durationNanos, reader.lastPacketTime());
        this.startTimeNanos = start;
        this.endTimeNanos = Math.max(start + 100000000L, end);
        this.currentTimeNanos = start;
        this.clipStartNanos = start;
        this.clipEndNanos = this.endTimeNanos;
        // 1.0.2：回放世界加载失败时构造器会抛出，但上面已经打开了 reader，
        // 原来这条路径（坏 GameJoin / 缺区块）会泄漏 packets.bin / snapshots.bin 句柄。
        ReplaySession session = new ReplaySession(client, this.reader);
        try {
            session.start(start, true);
        } catch (IOException | RuntimeException e) {
            try {
                this.reader.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
        this.session = session;
        this.lastWallNanos = System.nanoTime();
        this.lastChatNanos = lastWallNanos;
        ReplayClient.chat("Preview started: " + file.getName());
    }

    public void tick() {
        if (finished) return;
        if (!paused) {
            long now = System.nanoTime();
            currentTimeNanos += Math.max(0L, now - lastWallNanos);
            lastWallNanos = now;
            long loopEnd = Math.max(clipStartNanos + 100000L, clipEndNanos);
            if (currentTimeNanos >= loopEnd) {
                if (loop) {
                    currentTimeNanos = clipStartNanos;
                    session.seekTo(currentTimeNanos);
                    return;
                } else {
                    currentTimeNanos = loopEnd;
                    session.advanceTo(currentTimeNanos);
                    ReplayClient.chat("Preview finished.");
                    finish();
                    return;
                }
            }
        }
        session.advanceTo(currentTimeNanos);
        long now = System.nanoTime();
        if (now - lastChatNanos > 30000000000L) {
            lastChatNanos = now;
            ReplayClient.chat(String.format("Preview %.1fs / %.1fs",
                    getCurrentSeconds(), getDurationSeconds()));
        }
    }

    public void stop() {
        if (!finished) {
            ReplayClient.chat("Preview stopped.");
        }
        finish();
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        this.lastWallNanos = System.nanoTime();
    }

    public boolean isPaused() {
        return paused;
    }

    public void seekTo(long absoluteNanos) {
        this.currentTimeNanos = Math.max(0L, Math.min(endTimeNanos, absoluteNanos));
        this.lastWallNanos = System.nanoTime();
        if (!finished) {
            session.seekTo(currentTimeNanos);
        }
    }

    public void seekToSeconds(double absoluteSeconds) {
        seekTo((long) (absoluteSeconds * 1.0e9D));
    }

    public long getCurrentTimeNanos() {
        return currentTimeNanos;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public long getEndTimeNanos() {
        return endTimeNanos;
    }

    public double getCurrentSeconds() {
        return currentTimeNanos / 1.0e9D;
    }

    public double getDurationSeconds() {
        return (endTimeNanos - startTimeNanos) / 1.0e9D;
    }

    public double getClipSeconds() {
        return (currentTimeNanos - startTimeNanos) / 1.0e9D;
    }

    public double getTotalSeconds() {
        return endTimeNanos / 1.0e9D;
    }

    public boolean isFinished() {
        return finished;
    }

    public File getFile() {
        return file;
    }

    public ReplayReader getReader() {
        return reader;
    }

    public ReplaySession getSession() {
        return session;
    }

    /** 剪辑台设定的片段范围（纳秒，绝对回放时间）；循环时从起点回到片段起点。 */
    public void setClipRange(long startNanos, long endNanos) {
        this.clipStartNanos = Math.max(startTimeNanos, Math.min(startNanos, endTimeNanos));
        this.clipEndNanos = Math.max(this.clipStartNanos + 100000L, Math.min(endNanos, endTimeNanos));
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    private void finish() {
        finished = true;
        session.close();
        if (ReplayClient.getRenderManager() != null) {
            ReplayClient.getRenderManager().onPreviewFinished(this);
        }
    }
}
