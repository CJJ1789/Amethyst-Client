package net.client.replay.record;

import net.client.replay.util.ReplayPaths;
import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.network.Packet;

public final class RecordingWriter {
    private final File tempDir;
    private final File packetFile;
    private final File snapshotFile;
    private final File gameAudioFile;
    private final File soundFile;
    private final DataOutputStream packetOut;
    private final DataOutputStream snapshotOut;
    private final DataOutputStream soundOut;
    private int packetCount;
    private int snapshotCount;
    private int soundCount;
    private boolean finished;

    public RecordingWriter() throws IOException {
        tempDir = new File(ReplayPaths.replaysDir(), ".tmp-" + UUID.randomUUID().toString().substring(0, 8));
        if (!tempDir.isDirectory() && !tempDir.mkdirs()) {
            throw new IOException("Could not create " + tempDir);
        }
        packetFile = new File(tempDir, "packets.bin");
        snapshotFile = new File(tempDir, "snapshots.bin");
        gameAudioFile = new File(tempDir, "gameaudio.wav");
        soundFile = new File(tempDir, "soundevents.bin");
        packetOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(packetFile)));
        snapshotOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(snapshotFile)));
        soundOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(soundFile)));
        // File signature so the reader can tell this is a sound table.
        soundOut.writeInt(SoundEventRecord.MAGIC);
    }

    public synchronized boolean writePacket(long timeNanos, net.minecraft.network.Packet<?> packet) {
        if (finished) return false;
        try {
            byte[] data = PacketCodec.encode(packet);
            packetOut.writeLong(timeNanos);
            packetOut.writeUTF(packet.getClass().getName());
            packetOut.writeInt(data.length);
            packetOut.write(data);
            packetCount++;
            return true;
        } catch (Throwable t) {
            net.client.replay.ReplayClient.LOGGER.warn("Could not record packet {}", packet == null ? "null" : packet.getClass().getName(), t);
            return false;
        }
    }

    public synchronized void writeSnapshot(SnapshotSample sample) {
        if (finished) return;
        try {
            sample.write((DataOutput) snapshotOut);
            snapshotCount++;
        } catch (Throwable ignored) {
        }
    }

    public File getGameAudioFile() {
        return gameAudioFile;
    }

    public synchronized void writeSoundEvent(SoundEventRecord event) {
        if (finished || event == null) return;
        try {
            event.writeTable(soundOut);
            soundCount++;
        } catch (Throwable ignored) {
        }
    }

    public int getPacketCount() {
        return packetCount;
    }

    public int getSnapshotCount() {
        return snapshotCount;
    }

    public synchronized File finish(ReplayMeta meta) throws IOException {
        if (finished) {
            throw new IOException("Already finished");
        }
        finished = true;
        // 1.0.2：先把三条流关干净；任何一步失败都要把临时目录一起清掉，
        // 否则 .tmp-xxxx 会在 replays/ 里永久残留（原来只有 zip 那一段有 finally）。
        IOException closeFailure = null;
        closeFailure = closeStream(packetOut, closeFailure);
        closeFailure = closeStream(snapshotOut, closeFailure);
        closeFailure = closeStream(soundOut, closeFailure);
        if (closeFailure != null) {
            deleteTree(tempDir);
            throw closeFailure;
        }

        File out = new File(ReplayPaths.replaysDir(), ReplayPaths.sanitizeName(meta.worldName) + "-" + ReplayPaths.timestampName() + ".lcr");
        int suffix = 1;
        while (out.exists()) {
            out = new File(ReplayPaths.replaysDir(), ReplayPaths.sanitizeName(meta.worldName) + "-" + ReplayPaths.timestampName() + "-" + suffix + ".lcr");
            suffix++;
        }
        meta.packetCount = packetCount;
        meta.snapshotCount = snapshotCount;
        meta.hasAudio = false;
        meta.hasGameAudio = gameAudioFile.isFile() && gameAudioFile.length() > 44L;
        meta.soundEventCount = soundCount;

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            // Replays are huge but the save must not stall the game.  STORED
            // entries avoid the multi-second Deflater pass; the extra disk
            // space is intentional and can be traded back later.
            zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION);
            writeEntry(zip, "meta.json", meta.toJson().getBytes("UTF-8"));
            copyEntry(zip, "packets.bin", packetFile);
            copyEntry(zip, "snapshots.bin", snapshotFile);
            if (meta.hasGameAudio) {
                copyEntry(zip, "gameaudio.wav", gameAudioFile);
            }
            if (soundCount > 0 && soundFile.isFile()) {
                copyEntry(zip, "soundevents.bin", soundFile);
            }
        } catch (IOException e) {
            // 写到一半失败会留下一个结构不完整的 .lcr（元数据里的计数却是完整的），
            // 下次打开时表现为"回放只有一半"。删掉，让失败是可见的。
            if (out.isFile() && !out.delete()) {
                net.client.replay.ReplayClient.LOGGER.warn("Could not delete partial replay {}", out);
            }
            throw e;
        } finally {
            deleteTree(tempDir);
        }
        return out;
    }

    /** 关一条流；已经失败过就继续关剩下的，只保留第一个异常。 */
    private static IOException closeStream(DataOutputStream stream, IOException previous) {
        try {
            stream.flush();
            stream.close();
        } catch (IOException e) {
            return previous == null ? e : previous;
        }
        return previous;
    }

    public synchronized void abort() {
        if (finished) return;
        finished = true;
        try { packetOut.close(); } catch (Throwable ignored) {}
        try { snapshotOut.close(); } catch (Throwable ignored) {}
        try { soundOut.close(); } catch (Throwable ignored) {}
        deleteTree(tempDir);
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static void copyEntry(ZipOutputStream zip, String name, File file) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
    }

    private static void deleteTree(File file) {
        if (file == null) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
