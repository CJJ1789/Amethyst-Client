package net.client.replay.session;

import net.minecraft.world.World;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.Entity;

/** Invisible, non-colliding camera anchor used by camera keyframe tracks. */
public class ReplayCameraEntity extends net.minecraft.entity.Entity {
    public ReplayCameraEntity(net.minecraft.world.World world) {
        super(world);
        this.noClip = true;
        this.width = 0.1F;
        this.height = 0.1F;
    }

    public void setTransform(CameraPath.Transform transform) {
        this.prevX = this.x = this.prevTickX = transform.x;
        this.prevY = this.y = this.prevTickY = transform.y;
        this.prevZ = this.z = this.prevTickZ = transform.z;
        this.prevYaw = this.yaw = transform.yaw;
        this.prevPitch = this.pitch = transform.pitch;
    }

    public void setTransform(double x, double y, double z, float yaw, float pitch) {
        this.prevX = this.x = this.prevTickX = x;
        this.prevY = this.y = this.prevTickY = y;
        this.prevZ = this.z = this.prevTickZ = z;
        this.prevYaw = this.yaw = yaw;
        this.prevPitch = this.pitch = pitch;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound tag) {
    }

    @Override
    protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound tag) {
    }
}
