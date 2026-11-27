package net.client.replay.record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;

public final class SnapshotSampler {
    private long lastSampleNanos;
    private int sampleHz;

    public SnapshotSampler(int sampleHz) {
        this.sampleHz = sampleHz;
    }

    public boolean shouldSample(long nowNanos) {
        if (sampleHz <= 0) return false;
        long interval = 1000000000L / sampleHz;
        if (lastSampleNanos == 0L || nowNanos - lastSampleNanos >= interval) {
            lastSampleNanos = nowNanos;
            return true;
        }
        return false;
    }

    public SnapshotSample sample(long timeNanos, net.minecraft.world.World world, float tickDelta) {
        SnapshotSample sample = new SnapshotSample();
        sample.timeNanos = timeNanos;
        if (world == null) return sample;
        List<net.minecraft.entity.Entity> loaded = world.getLoadedEntities();
        if (loaded == null || loaded.isEmpty()) return sample;
        List<EntitySnapshot> entities = new ArrayList<EntitySnapshot>(loaded.size());
        for (net.minecraft.entity.Entity entity : loaded) {
            if (entity == null || entity.getEntityId() < 0) continue;
            entities.add(EntitySnapshot.capture(entity, tickDelta));
        }
        Collections.sort(entities, new Comparator<EntitySnapshot>() {
            @Override
            public int compare(EntitySnapshot a, EntitySnapshot b) {
                return Integer.compare(a.entityId, b.entityId);
            }
        });
        sample.entities = entities;
        return sample;
    }
}
