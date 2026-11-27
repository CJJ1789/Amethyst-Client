package net.client.replay.record;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * One entity at one sampling instant. 92 bytes on disk; keep in sync with
 * {@link #BYTES}.
 */
public final class EntitySnapshot {
    /**
     * 1.0.2_18：调试开关提到 static final。原来每个实体每次采样都做一次
     * Boolean.getBoolean（内部是 System.getProperty，全局锁），录制 20 Hz × 几十个实体
     * 时是稳定的热点。
     */
    private static final boolean DEBUG_SNEAK = Boolean.getBoolean("client.replay.soundDebug");
    public static final int BYTES = 92;
    /** v2 在 92 字节后面追加：9 格物品栏 + 选中格 + 副手（2+2+1 各一格）。 */
    public static final int EQUIP_BYTES = 9 * 5 + 1 + 4;
    public static final int BYTES_V2 = BYTES + EQUIP_BYTES;

    public static final int FLAG_SNEAKING = 1;
    public static final int FLAG_SPRINTING = 2;
    public static final int FLAG_INVISIBLE = 4;
    public static final int FLAG_USING_ITEM = 8;
    public static final int FLAG_SWINGING = 16;
    public static final int FLAG_DEAD = 32;

    public int entityId;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public float headYaw;
    public float bodyYaw;
    public float velocityX;
    public float velocityY;
    public float velocityZ;
    public float limbA;
    public float limbB;
    public float limbC;
    public float limbD;
    public float swingProgress;
    public int flags;
    public int hurtTime;
    public float width;
    public float height;

    /** v2：物品栏 9 格，id 0 表示空格；damage 用于工具/耐久外观。 */
    public short[] hotbarId = new short[9];
    public short[] hotbarDamage = new short[9];
    public byte[] hotbarCount = new byte[9];
    public byte selectedSlot;

    public static EntitySnapshot capture(net.minecraft.entity.Entity entity, float tickDelta) {
        EntitySnapshot s = new EntitySnapshot();
        s.entityId = entity.getEntityId();
        s.x = entity.prevX + (entity.x - entity.prevX) * tickDelta;
        s.y = entity.prevY + (entity.y - entity.prevY) * tickDelta;
        s.z = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
        s.yaw = interpolateAngle(entity.prevYaw, entity.yaw, tickDelta);
        s.pitch = entity.prevPitch + (entity.pitch - entity.prevPitch) * tickDelta;
        s.velocityX = (float) entity.velocityX;
        s.velocityY = (float) entity.velocityY;
        s.velocityZ = (float) entity.velocityZ;
        s.width = entity.width;
        s.height = entity.height;

        if (entity instanceof net.minecraft.entity.LivingEntity) {
            net.minecraft.entity.LivingEntity living = (net.minecraft.entity.LivingEntity) entity;
            s.headYaw = interpolateAngle(living.prevHeadYaw, living.headYaw, tickDelta);
            s.bodyYaw = interpolateAngle(living.prevBodyYaw, living.bodyYaw, tickDelta);
            s.limbA = living.field_6748;
            s.limbB = living.field_6749;
            s.limbC = living.field_6750;
            s.limbD = living.field_6752;
            s.swingProgress = living.getHandSwingProgress(tickDelta);
            s.hurtTime = living.hurtTime;
            if (living instanceof net.minecraft.entity.player.PlayerEntity) {
                net.minecraft.entity.player.PlayerEntity player = (net.minecraft.entity.player.PlayerEntity) living;
                if (player.isUsingItem()) s.flags |= FLAG_USING_ITEM;
                s.captureInventory(player);
            }
            if (living.handSwinging) s.flags |= FLAG_SWINGING;
            if (living.getHealth() <= 0.0F) s.flags |= FLAG_DEAD;
        }

        if (DEBUG_SNEAK && entity instanceof net.minecraft.entity.player.ClientPlayerEntity) {
            System.out.println("[SneakCap] live=" + entity.isSneaking()
                    + " id=" + entity.getEntityId() + " samePlayer=" + (entity == net.minecraft.client.MinecraftClient.getInstance().player));
        }
        if (entity.isSneaking()) s.flags |= FLAG_SNEAKING;
        if (entity.isSprinting()) s.flags |= FLAG_SPRINTING;
        if (entity.isInvisible()) s.flags |= FLAG_INVISIBLE;
        return s;
    }

    private void captureInventory(net.minecraft.entity.player.PlayerEntity player) {
        net.minecraft.entity.player.PlayerInventory inv = player.inventory;
        if (inv == null) return;
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = inv.main[i];
            if (stack == null || stack.getItem() == null) continue;
            this.hotbarId[i] = (short) net.minecraft.item.Item.getRawId(stack.getItem());
            this.hotbarDamage[i] = (short) stack.getDamage();
            this.hotbarCount[i] = (byte) Math.min(127, stack.count);
        }
        this.selectedSlot = (byte) inv.selectedSlot;
    }

    private void applyInventory(net.minecraft.entity.player.PlayerEntity player) {
        net.minecraft.entity.player.PlayerInventory inv = player.inventory;
        if (inv == null) return;
        for (int i = 0; i < 9; i++) {
            int id = this.hotbarId[i];
            if (id == 0) {
                inv.main[i] = null;
                continue;
            }
            net.minecraft.item.Item item = net.minecraft.item.Item.byRawId(id);
            if (item == null) {
                inv.main[i] = null;
                continue;
            }
            int count = this.hotbarCount[i] <= 0 ? 1 : this.hotbarCount[i];
            inv.main[i] = new net.minecraft.item.ItemStack(item, count, this.hotbarDamage[i]);
        }
        if (this.selectedSlot >= 0 && this.selectedSlot < 9) inv.selectedSlot = this.selectedSlot;
    }

    public void applyTo(net.minecraft.entity.Entity entity) {
        entity.prevX = entity.x = x;
        entity.prevY = entity.y = y;
        entity.prevZ = entity.z = z;
        entity.prevTickX = entity.x;
        entity.prevTickY = entity.y;
        entity.prevTickZ = entity.z;
        entity.prevYaw = entity.yaw = yaw;
        entity.prevPitch = entity.pitch = pitch;
        entity.velocityX = velocityX;
        entity.velocityY = velocityY;
        entity.velocityZ = velocityZ;
        entity.width = width;
        entity.height = height;
        entity.setSneaking((flags & FLAG_SNEAKING) != 0);
        // 本地玩家（ClientPlayerEntity）的 isSneaking() 不看 DataTracker 的 flag，
        // 它读的是 this.input.sneaking（实时键盘）。回放里没键盘，所以只写 flag
        // 永远不会蹲下——这就是"按住 Shift 录不进去"的原因，必须同时写 input。
        if (entity instanceof net.minecraft.entity.player.ClientPlayerEntity) {
            net.minecraft.entity.player.ClientPlayerEntity clientPlayer =
                    (net.minecraft.entity.player.ClientPlayerEntity) entity;
            if (clientPlayer.input != null) {
                clientPlayer.input.sneaking = (flags & FLAG_SNEAKING) != 0;
            }
        }
        entity.setSprinting((flags & FLAG_SPRINTING) != 0);
        entity.setInvisible((flags & FLAG_INVISIBLE) != 0);

        if (entity instanceof net.minecraft.entity.LivingEntity) {
            net.minecraft.entity.LivingEntity living = (net.minecraft.entity.LivingEntity) entity;
            living.prevHeadYaw = living.headYaw = headYaw;
            living.prevBodyYaw = living.bodyYaw = bodyYaw;
            living.field_6748 = limbA;
            living.field_6749 = limbB;
            living.field_6750 = limbC;
            living.field_6752 = limbD;
            living.lastHandSwingProgress = living.handSwingProgress = swingProgress;
            living.hurtTime = hurtTime;
            living.handSwinging = (flags & FLAG_SWINGING) != 0;
            if ((flags & FLAG_DEAD) != 0) {
                // No safe setter; the death animation is packet driven anyway.
            }
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) {
                applyInventory((net.minecraft.entity.player.PlayerEntity) living);
            }
        }
    }

    public void write(DataOutput out) throws IOException {
        out.writeInt(entityId);
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
        out.writeFloat(headYaw);
        out.writeFloat(bodyYaw);
        out.writeFloat(velocityX);
        out.writeFloat(velocityY);
        out.writeFloat(velocityZ);
        out.writeFloat(limbA);
        out.writeFloat(limbB);
        out.writeFloat(limbC);
        out.writeFloat(limbD);
        out.writeFloat(swingProgress);
        out.writeInt(flags);
        out.writeInt(hurtTime);
        out.writeFloat(width);
        out.writeFloat(height);
        for (int i = 0; i < 9; i++) {
            out.writeShort(hotbarId[i]);
            out.writeShort(hotbarDamage[i]);
            out.writeByte(hotbarCount[i]);
        }
        out.writeByte(selectedSlot);
        out.writeInt(0);
    }

    /**
     * 1.0.2：版本由调用方传入（属于文件），不再读全局静态字段。
     * 这样同时打开两个不同版本的回放时，各自的快照不会互相解析错位。
     */
    public static EntitySnapshot read(DataInput in, int formatVersion) throws IOException {
        EntitySnapshot s = new EntitySnapshot();
        s.entityId = in.readInt();
        s.x = in.readDouble();
        s.y = in.readDouble();
        s.z = in.readDouble();
        s.yaw = in.readFloat();
        s.pitch = in.readFloat();
        s.headYaw = in.readFloat();
        s.bodyYaw = in.readFloat();
        s.velocityX = in.readFloat();
        s.velocityY = in.readFloat();
        s.velocityZ = in.readFloat();
        s.limbA = in.readFloat();
        s.limbB = in.readFloat();
        s.limbC = in.readFloat();
        s.limbD = in.readFloat();
        s.swingProgress = in.readFloat();
        s.flags = in.readInt();
        s.hurtTime = in.readInt();
        s.width = in.readFloat();
        s.height = in.readFloat();
        if (formatVersion >= 2) {
            for (int i = 0; i < 9; i++) {
                s.hotbarId[i] = in.readShort();
                s.hotbarDamage[i] = in.readShort();
                s.hotbarCount[i] = in.readByte();
            }
            s.selectedSlot = in.readByte();
            in.readInt();
        }
        return s;
    }

    public static float interpolateAngle(float previous, float current, float delta) {
        float difference = current - previous;
        while (difference < -180.0F) difference += 360.0F;
        while (difference >= 180.0F) difference -= 360.0F;
        return previous + difference * delta;
    }

    public static float lerpAngle(float from, float to, float progress) {
        float difference = to - from;
        while (difference < -180.0F) difference += 360.0F;
        while (difference >= 180.0F) difference -= 360.0F;
        return from + difference * progress;
    }
}
