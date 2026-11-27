package net.client.util;

import net.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

public final class FreelookHandler {
   private static float yawOffset;
   private static float pitchOffset;
   private static float bodyYaw;
   private static float bodyPitch;
   private static float seenYaw;
   private static float seenPitch;
   private static boolean holding;

   private FreelookHandler() {
   }

   public static boolean isHolding() {
      return holding;
   }

   public static void tick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      boolean wanted = mc != null
         && mc.player != null
         && mc.world != null
         && mc.currentScreen == null
         && ClientConfig.freelook
         && Keyboard.isKeyDown(ClientConfig.freelookKey);
      if (!wanted) {
         release();
      } else {
         if (!holding) {
            holding = true;
            AbstractClientPlayerEntity player = mc.player;
            bodyYaw = player.yaw;
            bodyPitch = player.pitch;
            yawOffset = 0.0F;
            pitchOffset = 0.0F;
            seenYaw = bodyYaw;
            seenPitch = bodyPitch;
         }
      }
   }

   private static void release() {
      if (holding) {
         holding = false;
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc != null && mc.player != null) {
            mc.player.yaw = bodyYaw;
            mc.player.prevYaw = bodyYaw;
            mc.player.pitch = bodyPitch;
            mc.player.prevPitch = bodyPitch;
         }

         yawOffset = 0.0F;
         pitchOffset = 0.0F;
      }
   }

   public static void applyCamera() {
      if (holding) {
         MinecraftClient mc = MinecraftClient.getInstance();
         Entity camera = mc == null ? null : mc.getCameraEntity();
         if (camera != null) {
            if (mc.player != null) {
               AbstractClientPlayerEntity player = mc.player;
               float gainedYaw = MathHelper.wrapDegrees(player.yaw - seenYaw);
               float gainedPitch = player.pitch - seenPitch;
               yawOffset = MathHelper.wrapDegrees(yawOffset + gainedYaw);
               pitchOffset = MathHelper.clamp(pitchOffset + gainedPitch, -90.0F, 90.0F);
               player.yaw = bodyYaw;
               player.pitch = bodyPitch;
               player.prevYaw = bodyYaw;
               player.prevPitch = bodyPitch;
               seenYaw = bodyYaw;
               seenPitch = bodyPitch;
            }

            float viewYaw = MathHelper.wrapDegrees(bodyYaw + yawOffset);
            float viewPitch = MathHelper.clamp(bodyPitch + pitchOffset, -90.0F, 90.0F);
            camera.yaw = viewYaw;
            camera.prevYaw = viewYaw;
            camera.pitch = viewPitch;
            camera.prevPitch = viewPitch;
         }
      }
   }

   public static void restoreCamera() {
      if (holding) {
         MinecraftClient mc = MinecraftClient.getInstance();
         Entity camera = mc == null ? null : mc.getCameraEntity();
         if (camera != null) {
            camera.yaw = bodyYaw;
            camera.prevYaw = bodyYaw;
            camera.pitch = bodyPitch;
            camera.prevPitch = bodyPitch;
         }
      }
   }
}
