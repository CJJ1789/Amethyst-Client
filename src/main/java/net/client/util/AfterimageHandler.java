package net.client.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.client.ClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

public final class AfterimageHandler {
   private static final List<Afterimage> LIST = new CopyOnWriteArrayList<>();
   private static final Map<Integer, Long> LAST_ADD = new HashMap<>();

   private AfterimageHandler() {
   }

   public static int size() {
      return LIST.size();
   }

   /** 回放/导出任务开始时清掉上一段世界残留的残影，避免旧世界的残影叠到回放里。 */
   public static void clearAll() {
      LIST.clear();
      LAST_ADD.clear();
   }

   public static void addAfterimage(LivingEntity entity) {
      if (entity != null && !entity.removed) {
         int id = entity.getEntityId();
         long now = System.currentTimeMillis();
         Long previous = LAST_ADD.get(id);
         if (previous == null || now - previous >= 50L) {
            LAST_ADD.put(id, now);

            while (LIST.size() >= 24) {
               LIST.remove(0);
            }

            LIST.add(new Afterimage(entity, (long)Math.max(200, ClientConfig.afterimageLife)));
         }
      }
   }

   public static void tick() {
      if (!ClientConfig.afterimage) {
         if (!LIST.isEmpty()) {
            LIST.clear();
         }

         if (!LAST_ADD.isEmpty()) {
            LAST_ADD.clear();
         }
      } else {
         pruneExpired();
         if (LAST_ADD.size() > 128) {
            LAST_ADD.clear();
         }
      }
   }

   public static void onRenderWorld(float partialTicks) {
      if (ClientConfig.afterimage && !LIST.isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc != null && mc.world != null && mc.getCameraEntity() != null) {
            try {
               AfterimageRenderer.renderAll(LIST, partialTicks);
            } catch (Exception var3) {
               ClientLog.error("残影渲染异常", var3);
            }

            pruneExpired();
         }
      }
   }

   private static void pruneExpired() {
      boolean changed = false;

      for (Afterimage afterimage : LIST) {
         if (afterimage.isExpired() || afterimage.entity == null || afterimage.entity.removed) {
            LIST.remove(afterimage);
            changed = true;
         }
      }

      if (changed && LIST.isEmpty()) {
         LAST_ADD.clear();
      }
   }
}
