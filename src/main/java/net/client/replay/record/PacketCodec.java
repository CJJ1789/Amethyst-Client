package net.client.replay.record;

import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.util.List;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.util.PacketByteBuf;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.MobSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;

/**
 * Serializes any 1.8.9 packet with its own read/write implementation.
 * We do not depend on ReplayMod's packet registry or file format.
 */
public final class PacketCodec {
    private PacketCodec() {
    }

    public static byte[] encode(net.minecraft.network.Packet<?> packet) throws IOException {
        net.minecraft.util.PacketByteBuf buffer = new net.minecraft.util.PacketByteBuf(Unpooled.buffer());
        try {
            // 客户端从网络解出来的出生包只填了 entries，tracker 是 null，
            // 它们自己的 write() 却去调 tracker.write() -> NPE -> 包被丢掉 ->
            // 回放里"对面玩家/怪物"根本没出生。这里按 1.8.9 的字段顺序自己写一遍。
            if (!writeSpawnPacket(packet, buffer)) {
                packet.write(buffer);
            }
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            buffer.release();
        }
    }

    private static boolean writeSpawnPacket(net.minecraft.network.Packet<?> packet, PacketByteBuf buffer)
            throws IOException {
        if (packet instanceof PlayerSpawnS2CPacket) {
            PlayerSpawnS2CPacket spawn = (PlayerSpawnS2CPacket) packet;
            List<DataTracker.DataEntry> entries;
            try {
                // 客户端解出来的包如果本来就没有条目，字段是 null（deserializePacket 空表返回 null），
                // 而 getter 会转去读 tracker -> NPE。null 在这里等价于"没有条目"。
                entries = spawn.getDataTrackerEntries();
            } catch (Throwable t) {
                entries = java.util.Collections.emptyList();
            }
            if (entries == null) entries = java.util.Collections.emptyList();
            buffer.writeVarInt(spawn.getId());
            buffer.writeUUID(spawn.getPlayerUuid());
            buffer.writeInt(spawn.getX());
            buffer.writeInt(spawn.getY());
            buffer.writeInt(spawn.getZ());
            buffer.writeByte(spawn.getYaw());
            buffer.writeByte(spawn.getPitch());
            buffer.writeShort(spawn.getHandStackId());
            DataTracker.writeData(entries, buffer);
            return true;
        }
        if (packet instanceof MobSpawnS2CPacket) {
            MobSpawnS2CPacket spawn = (MobSpawnS2CPacket) packet;
            List<DataTracker.DataEntry> entries;
            try {
                entries = spawn.getEntries();
            } catch (Throwable t) {
                entries = java.util.Collections.emptyList();
            }
            if (entries == null) entries = java.util.Collections.emptyList();
            buffer.writeVarInt(spawn.getId());
            buffer.writeByte(spawn.getEntityTypeId() & 0xFF);
            buffer.writeInt(spawn.getX());
            buffer.writeInt(spawn.getY());
            buffer.writeInt(spawn.getZ());
            buffer.writeByte(spawn.getYaw());
            buffer.writeByte(spawn.getPitch());
            buffer.writeByte(spawn.getHeadYaw());
            buffer.writeShort(spawn.getVelocityX());
            buffer.writeShort(spawn.getVelocityY());
            buffer.writeShort(spawn.getVelocityZ());
            DataTracker.writeData(entries, buffer);
            return true;
        }
        return false;
    }

    /** 1.0.2_18：单个回放包的上限，避免被构造出长度字段直接吃光内存。
     *  1.0.2：改成 public，让 ReplayReader 建索引时用同一把尺子校验。 */
    public static final int MAX_PACKET_BYTES = 8 * 1024 * 1024;

    private static Class<?> loadPacketClass(String className) throws ClassNotFoundException {
        // 1.0.2_18：一律用 initialize=false 加载。原来第一句 `Class.forName(className)`
        // 会在校验「是不是 Packet」之前就执行任意类的静态初始化 —— 打开一个别人给的
        // .lcr 就能触发任意可加载类的 <clinit>。类初始化改为推迟到 newInstance。
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        ClassNotFoundException first = null;
        for (ClassLoader loader : new ClassLoader[]{
                context,
                net.minecraft.network.Packet.class.getClassLoader(),
                net.minecraft.network.packet.s2c.play.GameJoinS2CPacket.class.getClassLoader()}) {
            if (loader == null) continue;
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException ignored) {
                if (first == null) first = ignored;
            }
        }
        if (className.endsWith("GameJoinS2CPacket")) {
            return net.minecraft.network.packet.s2c.play.GameJoinS2CPacket.class;
        }
        throw first != null ? first : new ClassNotFoundException(className);
    }

    /** 只解析类、不初始化：供读取端判断「这条记录是不是 GameJoin」时复用。 */
    public static Class<?> resolvePacketClass(String className) throws ClassNotFoundException {
        return loadPacketClass(className);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static net.minecraft.network.Packet<?> decode(String className, byte[] data) throws Exception {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("empty packet class name");
        }
        if (data == null || data.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("packet payload too large: "
                    + (data == null ? "null" : data.length + " bytes"));
        }
        Class<?> raw = loadPacketClass(className);
        if (!net.minecraft.network.Packet.class.isAssignableFrom(raw)) {
            throw new IllegalArgumentException(className + " is not a Packet");
        }
        java.lang.reflect.Constructor<?> constructor = raw.getDeclaredConstructor();
        if (!constructor.isAccessible()) {
            constructor.setAccessible(true);
        }
        net.minecraft.network.Packet packet = (net.minecraft.network.Packet) constructor.newInstance();
        net.minecraft.util.PacketByteBuf buffer = new net.minecraft.util.PacketByteBuf(Unpooled.wrappedBuffer(data));
        try {
            packet.read(buffer);
        } finally {
            buffer.release();
        }
        return packet;
    }
}
