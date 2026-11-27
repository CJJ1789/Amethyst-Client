package net.client.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public final class EnvCheck {
   /**
    * 运行时要求（1.1.0 起与实际字节码一致）：jar 内全部类都是 Java 21 字节码，
    * 因此最低 Java 21；Java 25 LTS 为首选/推荐运行时（在 25 上验证）。
    * 低于 21 的 JVM 会在类加载阶段抛 UnsupportedClassVersionError，这里的提示只是
    * 让用户明白原因，无法阻止崩溃。
    */
   /** 1.0.1：Legacy Fabric 加载器目标 0.20.1，最低 0.12.0。 */
   public static final String RECOMMENDED_LOADER = "0.20.1";
   public static final String MIN_LOADER = "0.12.0";
   private static volatile EnvCheck.Status loaderStatus = new EnvCheck.Status("Legacy Fabric 检查中…", "checking Legacy Fabric…", 0);
   private static volatile boolean started;
   private static final Pattern LDR_VER = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

   private EnvCheck() {
   }

   public static EnvCheck.Status loader() {
      ensureStarted();
      return loaderStatus;
   }

   public static synchronized void ensureStarted() {
      if (!started) {
         started = true;
         Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
               try {
                  EnvCheck.checkLoader();
               } catch (Exception var2) {
                  EnvCheck.loaderStatus = new EnvCheck.Status("Legacy Fabric 检查失败", "Legacy Fabric check failed", 0);
               }
            }
         }, "Client-EnvCheck");
         t.setDaemon(true);
         t.start();
      }
   }

   private static void checkLoader() {
      String local = localLoaderVersion();
      String json = fetch("https://meta.legacyfabric.net/v2/versions/loader/1.8.9", 8000);
      String latest = null;
      if (json != null) {
         Matcher m = LDR_VER.matcher(json);
         if (m.find()) {
            latest = m.group(1);
         }
      }

      if (latest == null) {
         loaderStatus = new EnvCheck.Status("Legacy Fabric 加载器 " + local + " · 无法获取最新版本", "Legacy Fabric loader " + local + " · latest unknown", 0);
      } else {
         int cmp = compareVersion(local, latest);
         int minCmp = compareVersion(local, MIN_LOADER);
         if (minCmp < 0) {
            loaderStatus = new EnvCheck.Status(
               "Legacy Fabric 加载器 " + local + " · 过旧（最低 " + MIN_LOADER + "，推荐 " + RECOMMENDED_LOADER + "）",
               "Legacy Fabric loader " + local + " · too old (min " + MIN_LOADER + ", " + RECOMMENDED_LOADER + " recommended)",
               2
            );
         } else if (cmp < 0) {
            loaderStatus = new EnvCheck.Status(
               "Legacy Fabric 加载器 " + local + " · 有新版 " + latest + "（推荐 " + RECOMMENDED_LOADER + "）",
               "Legacy Fabric loader " + local + " · newer " + latest + " available (" + RECOMMENDED_LOADER + " recommended)",
               0
            );
         } else {
            loaderStatus = new EnvCheck.Status("Legacy Fabric 加载器 " + local + " · 已是 1.8.9 可用的最新版本", "Legacy Fabric loader " + local + " · latest for 1.8.9", 1);
         }
      }
   }

   private static String localLoaderVersion() {
      try {
         FabricLoader loader = FabricLoader.getInstance();
         if (loader.getModContainer("fabricloader").isPresent()) {
            return ((ModContainer)loader.getModContainer("fabricloader").get()).getMetadata().getVersion().getFriendlyString();
         }
      } catch (Exception var1) {
         ClientLog.warn("读取 Fabric Loader 版本失败", var1);
      }

      return "?";
   }

   static int compareVersion(String a, String b) {
      if (a != null && b != null) {
         String[] pa = a.split("\\.");
         String[] pb = b.split("\\.");
         int n = Math.max(pa.length, pb.length);

         for (int i = 0; i < n; i++) {
            int va = i < pa.length ? num(pa[i]) : 0;
            int vb = i < pb.length ? num(pb[i]) : 0;
            if (va != vb) {
               return va < vb ? -1 : 1;
            }
         }

         return 0;
      } else {
         return 0;
      }
   }

   private static int num(String s) {
      try {
         StringBuilder sb = new StringBuilder();

         for (char c : s.toCharArray()) {
            if (c < '0' || c > '9') {
               break;
            }

            sb.append(c);
         }

         return sb.length() == 0 ? 0 : Integer.parseInt(sb.toString());
      } catch (Exception var6) {
         return 0;
      }
   }

   static String fetch(String url, int timeoutMs) {
      HttpURLConnection conn = null;

      Object e;
      try {
         conn = (HttpURLConnection)new URL(url).openConnection();
         conn.setConnectTimeout(timeoutMs);
         conn.setReadTimeout(timeoutMs);
         conn.setRequestProperty("User-Agent", ModVersion.userAgent());
         conn.setRequestProperty("Accept", "application/json");
         if (conn.getResponseCode() == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
               sb.append(line);
            }

            reader.close();
            return sb.toString();
         }

         e = null;
      } catch (Exception var17) {
         return null;
      } finally {
         if (conn != null) {
            try {
               conn.disconnect();
            } catch (Exception var16) {
            }
         }
      }

      return (String)e;
   }

   public static final class Status {
      public final String zh;
      public final String en;
      public final int tone;

      Status(String zh, String en, int tone) {
         this.zh = zh;
         this.en = en;
         this.tone = tone;
      }
   }
}
