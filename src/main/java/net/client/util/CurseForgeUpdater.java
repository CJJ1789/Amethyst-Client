package net.client.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class CurseForgeUpdater {
   private static final CurseForgeUpdater.Info UNKNOWN = new CurseForgeUpdater.Info(CurseForgeUpdater.State.UNKNOWN, null, null);
   private static final CurseForgeUpdater.Info CHECKING = new CurseForgeUpdater.Info(CurseForgeUpdater.State.CHECKING, null, null);
   private static final Map<String, CurseForgeUpdater.Info> CACHE = new ConcurrentHashMap<>();
   private static volatile boolean started;

   private CurseForgeUpdater() {
   }

   public static CurseForgeUpdater.Info get(String modId) {
      if (modId == null) {
         return UNKNOWN;
      } else {
         CurseForgeUpdater.Info info = CACHE.get(modId);
         return info == null ? UNKNOWN : info;
      }
   }

   public static void checkAll(List<String> modIds, Map<String, String> names, Map<String, String> versions) {
      if (!started) {
         started = true;
         final ArrayList<String> ids = new ArrayList<>(modIds);
         final HashMap<String, String> nameCopy = new HashMap<>(names);
         final HashMap<String, String> verCopy = new HashMap<>(versions);
         Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
               for (String id : ids) {
                  if (!CurseForgeUpdater.isSelf(id)) {
                     CurseForgeUpdater.CACHE.put(id, CurseForgeUpdater.CHECKING);

                     try {
                        CurseForgeUpdater.CACHE.put(id, CurseForgeUpdater.query(id, nameCopy.get(id), verCopy.get(id)));
                     } catch (Exception var4) {
                        CurseForgeUpdater.CACHE.put(id, new CurseForgeUpdater.Info(CurseForgeUpdater.State.ERROR, null, null));
                     }
                  }
               }
            }
         }, "Client-CurseForgeCheck");
         t.setDaemon(true);
         t.start();
      }
   }

   private static boolean isSelf(String id) {
      return "client".equals(id) || "minecraft".equals(id) || "java".equals(id) || "fabricloader".equals(id);
   }

   private static CurseForgeUpdater.Info query(String id, String name, String installed) {
      String page = null;
      String display = null;

      for (String slug : slugCandidates(id, name)) {
         String json = EnvCheck.fetch("https://api.cfwidget.com/minecraft/mc-mods/" + slug, 7000);
         if (json != null) {
            page = "https://www.curseforge.com/minecraft/mc-mods/" + slug;
            display = pick189File(json);
            if (display != null) {
               break;
            }
         }
      }

      if (display == null) {
         return new CurseForgeUpdater.Info(CurseForgeUpdater.State.NO_PROJECT, null, page);
      } else {
         return installed != null && !installed.trim().isEmpty() && containsVersion(display, installed)
            ? new CurseForgeUpdater.Info(CurseForgeUpdater.State.UP_TO_DATE, display, page)
            : new CurseForgeUpdater.Info(CurseForgeUpdater.State.OUTDATED, display, page);
      }
   }

   /**
    * 1.0.2_1：原来是用正则把 JSON 硬切成片段（先找 "files"，再回截 900 字符去捞 display），
    * 稍微变个格式就抓不到，而且 900 字符这个魔数完全看运气。cfwidget 返回的是标准 JSON，
    * 项目里本来就有 Gson，直接按结构取 files[].versions 里含 "1.8.9" 的那条。
    */
   private static String pick189File(String json) {
      try {
         JsonObject root = new JsonParser().parse(json).getAsJsonObject();
         JsonArray files = root.has("files") && root.get("files").isJsonArray()
               ? root.getAsJsonArray("files") : null;
         if (files == null) {
            return null;
         }
         String best = null;
         for (JsonElement el : files) {
            if (!el.isJsonObject()) {
               continue;
            }
            JsonObject file = el.getAsJsonObject();
            if (!lists189(file)) {
               continue;
            }
            String display = str(file, "display");
            if (display == null || display.trim().isEmpty()) {
               display = str(file, "name");
            }
            if (display != null && !display.trim().isEmpty()) {
               best = display;
               break;
            }
         }
         return best;
      } catch (RuntimeException ex) {
         return null;
      }
   }

   /** file.versions 里是否包含 1.8.9（也接受 1.8.9-1.12.2 这种区间写法）。 */
   private static boolean lists189(JsonObject file) {
      JsonArray versions = file.has("versions") && file.get("versions").isJsonArray()
            ? file.getAsJsonArray("versions") : null;
      if (versions == null) {
         return false;
      }
      for (JsonElement v : versions) {
         if (v.isJsonPrimitive() && v.getAsString().contains("1.8.9")) {
            return true;
         }
      }
      return false;
   }

   private static String str(JsonObject obj, String key) {
      JsonElement el = obj.get(key);
      return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
   }

   private static boolean containsVersion(String display, String installed) {
      if (display == null || installed == null || installed.trim().isEmpty()) {
         return false;
      }
      // 1.0.1：先做规范化版本比较（摆平 0.1.5 与 0.1.5+1.8.9 这类 build metadata 差异）
      if (VersionUtil.isUpToDate(installed, display.trim())) {
         return true;
      }
      String normDisplay = VersionUtil.normalize(display);
      String normInstalled = VersionUtil.normalize(installed);
      if (!normInstalled.isEmpty() && normDisplay.contains(normInstalled)) {
         return true;
      }
      return compareLike(display, installed);
   }

   private static boolean compareLike(String haystack, String needle) {
      String a = haystack == null ? "" : haystack;
      String b = needle.trim();
      if (b.isEmpty()) {
         return false;
      } else if (a.contains(b)) {
         return true;
      } else {
         String normA = a.replace("-", "").replace("_", "");
         String normB = b.replace("-", "").replace("_", "");
         return !normB.isEmpty() && normA.contains(normB);
      }
   }

   private static List<String> slugCandidates(String id, String name) {
      List<String> out = new ArrayList<>();
      add(out, id);
      if (name != null) {
         add(out, name.toLowerCase().replace(' ', '-'));
         String base = name.replaceAll("(?i)\\s*v?\\d+(\\.\\d+)*.*$", "");
         add(out, base.toLowerCase().trim().replace(' ', '-'));
      }

      return out;
   }

   private static void add(List<String> out, String s) {
      if (s != null) {
         String slug = s.trim().toLowerCase().replaceAll("[^a-z0-9\\-]", "");

         while (slug.startsWith("-")) {
            slug = slug.substring(1);
         }

         while (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
         }

         if (!slug.isEmpty() && !out.contains(slug)) {
            out.add(slug);
         }
      }
   }

   public static final class Info {
      public final CurseForgeUpdater.State state;
      public final String latest;
      public final String page;

      Info(CurseForgeUpdater.State state, String latest, String page) {
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
