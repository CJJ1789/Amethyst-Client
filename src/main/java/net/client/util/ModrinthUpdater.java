package net.client.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmChatLinkScreen;
import net.minecraft.client.gui.widget.IdentifiableBooleanConsumer;
import net.minecraft.text.LiteralText;

public final class ModrinthUpdater {
   private static final ModrinthUpdater.Info UNKNOWN = new ModrinthUpdater.Info(ModrinthUpdater.State.UNKNOWN, null, null);
   private static final ModrinthUpdater.Info CHECKING = new ModrinthUpdater.Info(ModrinthUpdater.State.CHECKING, null, null);
   private static final Map<String, ModrinthUpdater.Info> CACHE = new ConcurrentHashMap<>();
   private static volatile boolean started;

   private ModrinthUpdater() {
   }

   public static ModrinthUpdater.Info get(String modId) {
      if (modId == null) {
         return UNKNOWN;
      } else {
         ModrinthUpdater.Info info = CACHE.get(modId);
         return info == null ? UNKNOWN : info;
      }
   }

   public static void checkAll(List<String> modIds, Map<String, String> installedVersions) {
      // 1.0.2_1：原来的写法是 `!started && A || !started`，布尔化简后等于只看 !started，
      // 传进来 null 列表会直接 new ArrayList<>(null) 抛 NPE。这里补回真正的空判断。
      if (!started && modIds != null && !modIds.isEmpty()) {
         started = true;
         final ArrayList<String> ids = new ArrayList<>(modIds);
         final HashMap<String, String> versions = installedVersions == null
               ? new HashMap<String, String>() : new HashMap<>(installedVersions);
         Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
               for (String id : ids) {
                  if (!ModrinthUpdater.isSelf(id)) {
                     String installed = versions.get(id);

                     try {
                        ModrinthUpdater.CACHE.put(id, ModrinthUpdater.query(id, installed));
                     } catch (Exception var5) {
                        ModrinthUpdater.CACHE.put(id, new ModrinthUpdater.Info(ModrinthUpdater.State.ERROR, null, null));
                     }
                  }
               }
            }
         }, "Client-ModrinthCheck");
         thread.setDaemon(true);
         thread.start();
      }
   }

   private static boolean isSelf(String id) {
      return "client".equals(id) || "minecraft".equals(id) || "java".equals(id);
   }

   /**
    * 1.0.2_1：原来用两条正则去切 Modrinth 的 JSON（"slug" / "version_number"），
    * 一旦接口字段顺序变化或多了同名字段就会取错。改成用 Gson 按结构读。
    */
   private static ModrinthUpdater.Info query(String modId, String installed) throws Exception {
      CACHE.put(modId, CHECKING);
      String projectJson = fetch("https://api.modrinth.com/v2/project/" + modId, 6000);
      if (projectJson == null) {
         return new ModrinthUpdater.Info(ModrinthUpdater.State.NO_PROJECT, null, null);
      }
      String slug = jsonString(readJson(projectJson), "slug");
      if (slug == null) {
         return new ModrinthUpdater.Info(ModrinthUpdater.State.NO_PROJECT, null, null);
      }
      String page = "https://modrinth.com/mod/" + slug;
      // 1.0.1：Legacy Fabric 的模组在 Modrinth 上可能只打 legacy-fabric 这个 loader 标签，
      // 只查 fabric 会漏掉，这里把两个 loader 都列上（Modrinth 的过滤是「任一匹配」）。
      String filter = "?game_versions=" + URLEncoder.encode("[\"1.8.9\"]", "UTF-8")
            + "&loaders=" + URLEncoder.encode("[\"fabric\",\"legacy-fabric\"]", "UTF-8");
      String versionsJson = fetch("https://api.modrinth.com/v2/project/" + slug + "/version" + filter, 8000);
      if (versionsJson == null || !versionsJson.trim().startsWith("[")) {
         return new ModrinthUpdater.Info(ModrinthUpdater.State.NO_PROJECT, null, page);
      }
      String latest = latestVersionNumber(readJson(versionsJson));
      if (latest == null) {
         return new ModrinthUpdater.Info(ModrinthUpdater.State.NO_PROJECT, null, page);
      }
      // 取全部候选里版本号最大的一个，避免接口排序变化导致取到旧版本
      boolean current = installed != null && !installed.trim().isEmpty()
            && VersionUtil.isUpToDate(installed, latest);
      return current
            ? new ModrinthUpdater.Info(ModrinthUpdater.State.UP_TO_DATE, latest, page)
            : new ModrinthUpdater.Info(ModrinthUpdater.State.OUTDATED, latest, page);
   }

   private static JsonElement readJson(String text) {
      try {
         return new JsonParser().parse(text);
      } catch (RuntimeException ex) {
         return null;
      }
   }

   private static String jsonString(JsonElement el, String key) {
      if (el == null || !el.isJsonObject()) {
         return null;
      }
      JsonElement v = el.getAsJsonObject().get(key);
      return v != null && v.isJsonPrimitive() ? v.getAsString() : null;
   }

   /** versions 数组里 version_number 最大的一项。 */
   private static String latestVersionNumber(JsonElement el) {
      if (el == null || !el.isJsonArray()) {
         return null;
      }
      JsonArray arr = el.getAsJsonArray();
      String latest = null;
      for (JsonElement item : arr) {
         String candidate = jsonString(item, "version_number");
         if (candidate == null) {
            continue;
         }
         if (latest == null || VersionUtil.compare(candidate, latest) > 0) {
            latest = candidate;
         }
      }
      return latest;
   }

   private static String fetch(String url, int timeoutMs) {
      HttpURLConnection conn = null;

      Object var20;
      try {
         conn = (HttpURLConnection)new URL(url).openConnection();
         conn.setConnectTimeout(timeoutMs);
         conn.setReadTimeout(timeoutMs);
         conn.setRequestProperty("User-Agent", ModVersion.userAgent());
         conn.setRequestProperty("Accept", "application/json");
         int code = conn.getResponseCode();
         if (code == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
               sb.append(line);
            }

            reader.close();
            return sb.toString();
         }

         var20 = null;
      } catch (Exception var18) {
         return null;
      } finally {
         if (conn != null) {
            try {
               conn.disconnect();
            } catch (Exception var17) {
               ClientLog.warn("断开 HTTP 连接失败", var17);
            }
         }
      }

      return (String)var20;
   }

   public static void openPage(String page) {
      if (page != null) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc != null) {
            mc.setScreen(new ConfirmChatLinkScreen(new IdentifiableBooleanConsumer() {
               public void confirmResult(boolean confirmed, int id) {
                  MinecraftClient.getInstance().setScreen(null);
                  if (confirmed) {
                     ModrinthUpdater.browse(page);
                  }
               }
            }, page, -1, true));
         } else {
            browse(page);
         }
      }
   }

   private static void browse(String page) {
      try {
         Class<?> desktop = Class.forName("java.awt.Desktop");
         Object instance = desktop.getMethod("getDesktop").invoke(null);
         desktop.getMethod("browse", URI.class).invoke(instance, URI.create(page));
      } catch (Exception var3) {
         ClientLog.warn("调用系统浏览器失败，退化为聊天框地址", var3);
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null) {
            client.player.sendMessage(new LiteralText("§7Modrinth: §f" + page));
         }
      }
   }

   public static final class Info {
      public final ModrinthUpdater.State state;
      public final String latest;
      public final String page;

      Info(ModrinthUpdater.State state, String latest, String page) {
         this.state = state;
         this.latest = latest;
         this.page = page;
      }
   }

   public static enum State {
      UNKNOWN,
      CHECKING,
      UP_TO_DATE,
      OUTDATED,
      NO_PROJECT,
      ERROR;
   }
}
