package net.client.util;

import net.minecraft.entity.LivingEntity;

public final class Afterimage {
   public final LivingEntity entity;
   public final int entityId;
   public double x;
   public double y;
   public double z;
   public float yaw;
   public float pitch;
   public float headYaw;
   public float bodyYaw;
   public float limbSwing;
   public float limbSwingAmount;
   public float ticksAlive;
   public float swingProgress;
   public boolean sneaking;
   public boolean riding;
   public boolean child;
   public final long createTime;
   public final long durationMillis;
   /** 位置/姿态是否已从实体上快照。回放里受击包先于位置快照到达，立即取坐标会拿到
    * 出生点位置；延迟到第一次渲染帧再抓，此时实体已是当前回放时刻的姿态。 */
   public boolean captured;

   public Afterimage(LivingEntity entity, long durationMillis) {
      this.entity = entity;
      this.entityId = entity.getEntityId();
      this.createTime = System.currentTimeMillis();
      this.durationMillis = Math.max(100L, durationMillis);
      // 不在构造时抓位置：回放中受击包可能先于位置快照到达。
   }

   public void captureFrom(LivingEntity entity) {
      if (entity == null) return;
      this.x = entity.x;
      this.y = entity.y;
      this.z = entity.z;
      this.yaw = entity.yaw;
      this.pitch = entity.pitch;
      this.headYaw = entity.headYaw;
      this.bodyYaw = entity.bodyYaw;
      this.limbSwing = entity.field_6750;
      this.limbSwingAmount = entity.field_6749;
      this.ticksAlive = (float)entity.ticksAlive;
      this.swingProgress = entity.getHandSwingProgress(1.0F);
      this.sneaking = entity.isSneaking();
      this.riding = entity.hasVehicle();
      this.child = entity.isBaby();
      this.captured = true;
   }

   public float getAlpha() {
      long age = System.currentTimeMillis() - this.createTime;
      float a = 1.0F - (float)age / (float)this.durationMillis;
      if (a < 0.0F) {
         a = 0.0F;
      }

      if (a > 1.0F) {
         a = 1.0F;
      }

      return a;
   }

   public boolean isExpired() {
      return System.currentTimeMillis() - this.createTime > this.durationMillis;
   }
}
