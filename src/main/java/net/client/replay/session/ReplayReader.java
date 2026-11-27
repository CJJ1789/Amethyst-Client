package net.client.replay.session;

import net.client.replay.record.ReplayMeta;
import net.client.replay.record.PacketCodec;
import net.client.replay.record.SnapshotSample;
import net.client.replay.util.ReplayPaths;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads our own .lcr container: a plain ZIP with meta.json, packets.bin and
 * snapshots.bin. No ReplayMod format compatibility is involved.
 */
public final class ReplayReader implements AutoCloseable {
    public static final class PacketRecord {
        public final long timeNanos;
        public final String className;
        public final long payloadOffset;
        public final int payloadLength;

        PacketRecord(long timeNanos, String className, long payloadOffset, int payloadLength) {
            this.timeNanos = timeNanos;
            this.className = className;
            this.payloadOffset = payloadOffset;
            this.payloadLength = payloadLength;
        }
    }

    public static final class SnapshotRecord {
        public final long timeNanos;
        public final long offset;
        public final int entityCount;

        SnapshotRecord(long timeNanos, long offset, int entityCount) {
            this.timeNanos = timeNanos;
            this.offset = offset;
            this.entityCount = entityCount;
        }
    }

    public final File file;
    public final ReplayMeta meta;
    /** 1.0.2：格式版本属于「这一个文件」，不再写成 EntitySnapshot 的全局静态字段。
     *  原来打开 A 回放后再打开 B（不同版本），A 还没关掉的 reader 会按 B 的版本解析快照。 */
    public final int formatVersion;
    private final List<PacketRecord> packets = new ArrayList<PacketRecord>();
    private final List<SnapshotRecord> snapshots = new ArrayList<SnapshotRecord>();
    /** 1.0.2：建索引时顺手记下 GameJoin 的下标，避免每次 seek 全量扫。 */
    private final List<Integer> joinPackets = new ArrayList<Integer>();
    private final RandomAccessFile packetFile;
    private final RandomAccessFile snapshotFile;
    private final File audioFile;
    private final File gameAudioFile;
    private final File soundEventsFile;
    private final java.util.Map<Integer, SnapshotSample> sampleCache = new java.util.HashMap<Integer, SnapshotSample>();

    private ReplayReader(File file, ReplayMeta meta, File packetFile, File snapshotFile,
                         File audioFile, File gameAudioFile, File soundEventsFile) throws IOException {
        this.file = file;
        this.meta = meta;
        this.audioFile = audioFile;
        this.gameAudioFile = gameAudioFile;
        this.soundEventsFile = soundEventsFile;
        this.formatVersion = meta != null && meta.formatVersion >= 2 ? 2 : 1;
        RandomAccessFile packets = new RandomAccessFile(packetFile, "r");
        RandomAccessFile snapshots;
        try {
            snapshots = new RandomAccessFile(snapshotFile, "r");
        } catch (IOException e) {
            packets.close();
            throw e;
        }
        this.packetFile = packets;
        this.snapshotFile = snapshots;
        try {
            indexPackets();
            indexSnapshots();
        } catch (IOException e) {
            // 1.0.2：索引失败（文件被截断/伪造）时构造函数直接抛出，
            // 两个已经打开的句柄原来会泄漏到 GC 才知道。
            try {
                packets.close();
            } catch (IOException ignored) {
            }
            try {
                snapshots.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    public static ReplayReader open(File file) throws IOException {
        ZipFile zip = new ZipFile(file);
        try {
            ReplayMeta meta;
            ZipEntry metaEntry = zip.getEntry("meta.json");
            if (metaEntry == null) throw new IOException("不是有效的回放文件：缺少 meta.json");
            try (InputStream in = zip.getInputStream(metaEntry)) {
                meta = ReplayMeta.fromJson(readString(in));
            }
            File cache = new File(ReplayPaths.cacheDir(), file.getName() + "-" + file.length()
                    + "-v" + Math.max(1, meta.formatVersion));
            if (!cache.isDirectory() && !cache.mkdirs()) throw new IOException("Could not create cache " + cache);
            File packetCache = new File(cache, "packets.bin");
            File snapshotCache = new File(cache, "snapshots.bin");
            extract(zip, "packets.bin", packetCache);
            extract(zip, "snapshots.bin", snapshotCache);
            File audioCache = new File(cache, "audio.wav");
            if (zip.getEntry("audio.wav") != null) {
                try {
                    extract(zip, "audio.wav", audioCache);
                } catch (Throwable ignored) {
                    audioCache = null;
                }
            } else {
                audioCache = null;
            }
            File gameAudioCache = new File(cache, "gameaudio.wav");
            if (zip.getEntry("gameaudio.wav") != null) {
                try {
                    extract(zip, "gameaudio.wav", gameAudioCache);
                } catch (Throwable ignored) {
                    gameAudioCache = null;
                }
            } else {
                gameAudioCache = null;
            }
            File soundsCache = new File(cache, "soundevents.bin");
            if (zip.getEntry("soundevents.bin") != null) {
                try {
                    extract(zip, "soundevents.bin", soundsCache);
                } catch (Throwable ignored) {
                    soundsCache = null;
                }
            } else {
                soundsCache = null;
            }
            return new ReplayReader(file, meta, packetCache, snapshotCache, audioCache, gameAudioCache, soundsCache);
        } finally {
            zip.close();
        }
    }

    private static String readString(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
            // 1.0.2：meta.json 只可能是几百字节。原来是无限读，构造一个塞满
            // meta.json 的 zip 就能让客户端在读元数据时先把内存吃光。
            if (out.size() > 1024 * 1024) {
                throw new IOException("meta.json 过大（>1 MiB），不是有效的回放文件");
            }
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    private static void extract(ZipFile zip, String name, File out) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new IOException("Missing " + name);
        try (InputStream in = new BufferedInputStream(zip.getInputStream(entry));
             OutputStream os = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                os.write(buffer, 0, read);
            }
        }
    }

    private void indexPackets() throws IOException {
        long length = packetFile.length();
        long pos = 0L;
        while (pos < length) {
            // 1.0.2：索引原来完全信任文件里的长度字段。payloadLength 为负时
            // pos 会往回走，形成死循环；为超大值时下次 readPacketPayload 直接 OOM。
            if (length - pos < 8L + 2L + 4L) {
                throw new IOException("packets.bin 在偏移 " + pos + " 处被截断");
            }
            packetFile.seek(pos);
            long time = packetFile.readLong();
            String className;
            try {
                className = packetFile.readUTF();
            } catch (java.io.UTFDataFormatException e) {
                throw new IOException("packets.bin 在偏移 " + pos + " 处的类名损坏", e);
            }
            int payloadLength = packetFile.readInt();
            long payloadOffset = packetFile.getFilePointer();
            if (payloadLength < 0 || payloadLength > PacketCodec.MAX_PACKET_BYTES) {
                throw new IOException("packets.bin 在偏移 " + pos + " 处的长度非法：" + payloadLength);
            }
            if (payloadOffset + payloadLength > length) {
                throw new IOException("packets.bin 在偏移 " + payloadOffset + " 处被截断（需要 "
                        + payloadLength + " 字节，只剩 " + (length - payloadOffset) + "）");
            }
            if (isJoinClassName(className)) {
                joinPackets.add(packets.size());
            }
            packets.add(new PacketRecord(time, className, payloadOffset, payloadLength));
            pos = payloadOffset + payloadLength;
        }
    }

    private void indexSnapshots() throws IOException {
        long length = snapshotFile.length();
        int stride = formatVersion >= 2
                ? net.client.replay.record.EntitySnapshot.BYTES_V2
                : net.client.replay.record.EntitySnapshot.BYTES;
        long pos = 0L;
        while (pos < length) {
            if (length - pos < 8L + 4L) {
                throw new IOException("snapshots.bin 在偏移 " + pos + " 处被截断");
            }
            snapshotFile.seek(pos);
            long time = snapshotFile.readLong();
            int count = snapshotFile.readInt();
            long dataStart = snapshotFile.getFilePointer();
            if (count < 0) {
                throw new IOException("snapshots.bin 在偏移 " + pos + " 处的实体数非法：" + count);
            }
            long bytes = (long) count * (long) stride;
            if (bytes > length - dataStart) {
                throw new IOException("snapshots.bin 在偏移 " + dataStart + " 处被截断（声明 "
                        + count + " 个实体，需要 " + bytes + " 字节）");
            }
            snapshots.add(new SnapshotRecord(time, pos, count));
            pos = dataStart + bytes;
        }
    }

    public int getPacketCount() {
        return packets.size();
    }

    public int getSnapshotCount() {
        return snapshots.size();
    }

    public PacketRecord packet(int index) {
        return packets.get(index);
    }

    public byte[] readPacketPayload(PacketRecord record) throws IOException {
        byte[] data = new byte[record.payloadLength];
        packetFile.seek(record.payloadOffset);
        packetFile.readFully(data);
        return data;
    }

    public List<SnapshotRecord> snapshots() {
        return snapshots;
    }

    public long firstPacketTime() {
        return packets.isEmpty() ? 0L : packets.get(0).timeNanos;
    }

    public long lastPacketTime() {
        return packets.isEmpty() ? 0L : packets.get(packets.size() - 1).timeNanos;
    }

    /**
     * 1.0.2：GameJoin 的索引在建库时算一次，之后 hasGameJoin() 是 O(1)、
     * 每次回退 seek 也不用再全量扫描 packet 列表。原来每次 resetWorld() 都要
     * 把整条流（可能上百万个包）走一遍并对每个类名做一次类解析，长回放里拖动
     * 时间轴会明显卡顿。
     */
    public boolean hasGameJoin() {
        return !joinPackets.isEmpty();
    }

    /** GameJoin 记录在 packets 列表里的下标（已按文件顺序）。 */
    public List<Integer> joinPacketIndexes() {
        return java.util.Collections.unmodifiableList(joinPackets);
    }

    private final java.util.Map<String, Boolean> joinClassCache = new java.util.HashMap<String, Boolean>();

    private boolean isJoinClassName(String className) {
        if (className == null) return false;
        Boolean cached = joinClassCache.get(className);
        if (cached != null) return cached;
        boolean join = false;
        String runtimeName = net.minecraft.network.packet.s2c.play.GameJoinS2CPacket.class.getName();
        // Production Fabric uses intermediary class names (class_...), so a
        // simple suffix check would reject every real replay recorded on a
        // device. Compare the runtime name and, as a fallback, resolve it.
        if (className.equals(runtimeName) || className.endsWith("GameJoinS2CPacket")) {
            join = true;
        } else {
            try {
                // 1.0.2_18：只解析类、不初始化，避免容器里的类名触发静态初始化。
                join = net.minecraft.network.packet.s2c.play.GameJoinS2CPacket.class
                        .isAssignableFrom(PacketCodec.resolvePacketClass(className));
            } catch (Throwable ignored) {
                join = false;
            }
        }
        joinClassCache.put(className, join);
        return join;
    }

    public File getAudioFile() {
        if (gameAudioFile != null && gameAudioFile.isFile() && gameAudioFile.length() > 44L) return gameAudioFile;
        return audioFile != null && audioFile.isFile() && audioFile.length() > 44L ? audioFile : null;
    }

    public boolean hasSoundEvents() {
        return soundEventsFile != null && soundEventsFile.isFile() && soundEventsFile.length() > 0L;
    }

    public java.util.List<net.client.replay.record.SoundEventRecord> readSoundEvents() {
        java.util.List<net.client.replay.record.SoundEventRecord> list = new java.util.ArrayList<net.client.replay.record.SoundEventRecord>();
        if (!hasSoundEvents()) return list;
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(soundEventsFile))) ) {
            // soundevents.bin starts with a file signature; anything without it
            // is not a sound table and is treated as "no sound events".
            if (in.readInt() != net.client.replay.record.SoundEventRecord.MAGIC) {
                return list;
            }
            while (true) {
                list.add(net.client.replay.record.SoundEventRecord.readTable(in));
            }
        } catch (java.io.EOFException ignored) {
        } catch (Throwable ignored) {
        }
        return list;
    }

    public long contentStartNanos() {
        if (meta.contentStartNanos > 0L) return meta.contentStartNanos;
        return 0L;
    }

    public int findSnapshotIndex(long timeNanos) {
        int low = 0;
        int high = snapshots.size() - 1;
        int result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (snapshots.get(mid).timeNanos <= timeNanos) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    public SnapshotSample snapshot(int index) throws IOException {
        if (index < 0 || index >= snapshots.size()) return null;
        SnapshotSample sample = sampleCache.get(index);
        if (sample != null) return sample;
        SnapshotRecord record = snapshots.get(index);
        snapshotFile.seek(record.offset);
        sample = SnapshotSample.read(snapshotFile, formatVersion);
        if (sampleCache.size() > 24) sampleCache.clear();
        sampleCache.put(index, sample);
        return sample;
    }

    @Override
    public void close() throws IOException {
        packetFile.close();
        snapshotFile.close();
    }
}
