package net.client.replay.record;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotSample {
    public long timeNanos;
    public List<EntitySnapshot> entities = new ArrayList<EntitySnapshot>();

    public void write(DataOutput out) throws IOException {
        out.writeLong(timeNanos);
        out.writeInt(entities.size());
        for (EntitySnapshot entity : entities) {
            entity.write(out);
        }
    }

    public static SnapshotSample read(DataInput in, int formatVersion) throws IOException {
        SnapshotSample sample = new SnapshotSample();
        sample.timeNanos = in.readLong();
        int count = in.readInt();
        // 1.0.2：损坏/伪造的计数会直接变成 ArrayList 的容量，先挡掉。
        if (count < 0 || count > 1_000_000) {
            throw new IOException("快照实体数非法：" + count);
        }
        sample.entities = new ArrayList<EntitySnapshot>(count);
        for (int i = 0; i < count; i++) {
            sample.entities.add(EntitySnapshot.read(in, formatVersion));
        }
        return sample;
    }
}
