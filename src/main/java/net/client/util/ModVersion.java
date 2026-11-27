package net.client.util;

/**
 * 1.0.2_18：版本号的唯一来源。之前 User-Agent、回放元数据里各自写死 "1.0.1"，
 * 发新版忘了改就一直在对外宣称旧版本。统一从 fabric.mod.json 读。
 */
public final class ModVersion {
   private static String cached;

   private ModVersion() {
   }

   public static synchronized String get() {
      if (cached == null) {
         String value = "unknown";
         try {
            value = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("client")
                  .map(container -> container.getMetadata().getVersion().getFriendlyString())
                  .orElse("unknown");
         } catch (Throwable ignored) {
            // 没在 Fabric 环境里跑（单元测试 / 反编译查看），保持 unknown
         }
         cached = value;
      }
      return cached;
   }

   public static String userAgent() {
      return "Client/" + get() + " (Minecraft 1.8.9)";
   }
}
