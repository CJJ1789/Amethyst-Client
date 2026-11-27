package net.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.client.mixin.SessionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;

/**
 * Account storage plus the immutable "launcher" account.
 *
 * <p>The session the game starts with is always kept as a synthetic, non-deletable
 * entry.  Deleting the last saved account therefore falls back to that session
 * instead of leaving the game on the account that was just removed.</p>
 */
public final class Accounts {
   private static final List<Accounts.Account> LIST = new ArrayList<>();
   private static int current = -1;
   private static boolean loaded;
   private static boolean usingLauncher;
   private static Accounts.Account launcher;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private Accounts() {
   }

   /** Apply the saved selection (or the launcher session) once the title screen exists. */
   public static void bootstrap() {
      load();
      if (current >= 0 && current < LIST.size()) {
         apply(LIST.get(current));
      } else {
         current = -1;
         applyLauncher();
      }
   }

   public static List<Accounts.Account> list() {
      load();
      return LIST;
   }

   public static int currentIndex() {
      load();
      return current;
   }

   public static Accounts.Account current() {
      load();
      return current >= 0 && current < LIST.size() ? LIST.get(current) : null;
   }

   /** The session the launcher started the game with; never stored on disk. */
   public static Accounts.Account launcher() {
      load();
      captureLauncher();
      return launcher;
   }

   public static boolean usingLauncher() {
      load();
      return usingLauncher;
   }

   public static void remove(int index) {
      load();
      if (index < 0 || index >= LIST.size()) {
         return;
      }

      LIST.remove(index);
      if (current > index) {
         current--;
      } else if (current == index) {
         if (LIST.isEmpty()) {
            current = -1;
            applyLauncher();
         } else {
            current = Math.min(index, LIST.size() - 1);
            apply(LIST.get(current));
         }
      } else if (current < 0) {
         applyLauncher();
      }
      save();
   }

   public static void add(Accounts.Account account) {
      load();

      for (int i = 0; i < LIST.size(); i++) {
         if (LIST.get(i).name.equalsIgnoreCase(account.name) && LIST.get(i).type.equals(account.type)) {
            LIST.set(i, account);
            current = i;
            save();
            apply(account);
            return;
         }
      }

      LIST.add(account);
      current = LIST.size() - 1;
      save();
      apply(account);
   }

   public static void select(int index) {
      load();
      if (index >= 0 && index < LIST.size()) {
         current = index;
         save();
         apply(LIST.get(index));
      }
   }

   public static void selectLauncher() {
      load();
      current = -1;
      save();
      applyLauncher();
   }

   public static boolean apply(Accounts.Account account) {
      if (account == null) {
         return false;
      }
      usingLauncher = "launcher".equals(account.type);
      String userType = account.userType;
      if (userType == null || userType.isEmpty()) {
         userType = !"microsoft".equals(account.type) && !"token".equals(account.type) ? "legacy" : "mojang";
      }
      Session session = new Session(account.name, dashed(account.uuid), account.token, userType);

      try {
         ((SessionAccessor)MinecraftClient.getInstance()).client$setSession(session);
         return true;
      } catch (Exception var5) {
         ClientLog.warn("SessionAccessor 写会话失败，转反射兜底", var5);

         try {
            MinecraftClient client = MinecraftClient.getInstance();
            // 1.0.2_1：运行时类/字段都被重映射成 intermediary，写死 "session" 必然
            // NoSuchFieldException；改成按字段类型（Session）扫，名字变了也能命中。
            Field field = findSessionField(MinecraftClient.class);
            if (field == null) {
               ClientLog.warn("找不到 Session 字段，无法切换账号");
               return false;
            }
            field.setAccessible(true);
            field.set(client, session);
            return true;
         } catch (Exception var4) {
            return false;
         }
      }
   }

   /** 在类及其父类里找类型为 Session 的字段（只用类型，不依赖字段名）。 */
   private static Field findSessionField(Class<?> type) {
      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
         for (Field f : c.getDeclaredFields()) {
            if (f.getType() == Session.class) {
               return f;
            }
         }
      }
      return null;
   }

   private static void applyLauncher() {
      captureLauncher();
      if (launcher != null) {
         apply(launcher);
      } else {
         usingLauncher = true;
      }
   }

   private static void captureLauncher() {
      if (launcher != null) {
         return;
      }
      try {
         MinecraftClient client = MinecraftClient.getInstance();
         Session session = client == null ? null : client.getSession();
         if (session == null) {
            return;
         }
         Accounts.Account account = new Accounts.Account();
         account.type = "launcher";
         account.name = session.getUsername() == null ? "" : session.getUsername();
         account.uuid = session.getUuid() == null ? "" : session.getUuid();
         account.token = session.getAccessToken() == null ? "" : session.getAccessToken();
         account.userType = session.getAccountType() == null ? "legacy"
                 : session.getAccountType().name().toLowerCase(java.util.Locale.ROOT);
         launcher = account;
      } catch (Exception e) {
         ClientLog.warn("记录启动器账号失败", e);
      }
   }

   public static String offlineUuid(String name) {
      return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
   }

   public static String dashed(String uuid) {
      if (uuid == null) {
         return "";
      } else {
         String s = uuid.replace("-", "").trim();
         return s.length() != 32
            ? s
            : s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20);
      }
   }

   private static File file() {
      File dir = new File(MinecraftClient.getInstance().runDirectory, "config");
      if (!dir.exists()) {
         dir.mkdirs();
      }

      return new File(dir, "client-accounts.json");
   }

   private static void load() {
      if (!loaded) {
         loaded = true;
         captureLauncher();

         try {
            File file = file();
            if (!file.isFile()) {
               return;
            }

            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Accounts.Saved saved = (Accounts.Saved)GSON.fromJson(json, Accounts.Saved.class);
            if (saved != null) {
               if (saved.accounts != null) {
                  LIST.addAll(saved.accounts);
               }

               current = saved.current;
               // 1.0.2：老版本写下的文件是 644，只在「下一次保存」时才被收紧。
               // 读到就顺手修一次，别让 token 在磁盘上继续对同机其他账户可读。
               tightenPermissions(file);
            }
         } catch (Exception var3) {
            ClientLog.warn("读取账号文件失败，使用空列表", var3);
         }
      }
   }

   private static void save() {
      try {
         Accounts.Saved saved = new Accounts.Saved();
         saved.accounts = LIST;
         saved.current = current;
         File target = file();
         Files.write(target.toPath(), GSON.toJson(saved).getBytes(StandardCharsets.UTF_8));
         // 1.0.2_18：文件里是 accessToken，默认 644 会让同机其它账户读到。
         // 尽量收紧成 600（Windows / 不支持 POSIX 的文件系统上静默跳过）。
         tightenPermissions(target);
      } catch (Exception var1) {
         ClientLog.warn("保存账号文件失败", var1);
      }
   }

   /** 1.0.2：把账号文件尽量收紧成 600；不支持 POSIX 时静默跳过。 */
   private static void tightenPermissions(File target) {
      try {
         Files.setPosixFilePermissions(target.toPath(),
               java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                     java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
      } catch (UnsupportedOperationException | java.io.IOException | SecurityException ignored) {
         // 非 POSIX 文件系统 / 无权限
      }
   }

   public static final class Account {
      public String type = "offline";
      public String name = "";
      public String uuid = "";
      public String token = "";
      public String site = "";
      public String email = "";
      public String userType = "";

      public String display() {
         String t;
         if ("offline".equals(this.type)) {
            t = "离线";
         } else if ("token".equals(this.type)) {
            t = "Token";
         } else if ("skin".equals(this.type)) {
            t = "皮肤站";
         } else if ("launcher".equals(this.type)) {
            t = "启动器";
         } else {
            t = "正版";
         }

         return this.name + "  ·  " + t;
      }
   }

   private static final class Saved {
      List<Accounts.Account> accounts;
      int current;

      private Saved() {
      }
   }
}
