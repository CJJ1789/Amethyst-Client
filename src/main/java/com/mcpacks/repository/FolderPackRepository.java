// Upstream: FastPacks / McPacks 1.0 (Forge 1.8.9), MIT
//   https://modrinth.com/mod/fastpacks (author: McPacks Team; Modrinth owner: Dulandor)
// Ported and modified for Legacy Fabric by this project; upstream MIT notice retained
// (see licenses/FASTPACKS-MIT.txt). The port was never released standalone.

package com.mcpacks.repository;

import com.mcpacks.core.PackEntry;
import com.mcpacks.core.PackManager;
import com.mcpacks.util.FolderUtils;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.client.util.ClientLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.ResourcePackLoader;
import net.minecraft.client.resource.ResourcePackLoader.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FolderPackRepository {
   private static final Logger LOGGER = LogManager.getLogger("mcpacks");
   private final MinecraftClient mc;
   private final ResourcePackLoader loader;
   private static Constructor<?> entryConstructor;
   private static Field resourcePackFileField;
   private static boolean reflected;

   public FolderPackRepository(MinecraftClient mc) {
      this.mc = mc;
      this.loader = mc.getResourcePackLoader();
   }

   private static synchronized void reflectEntryClass() {
      if (!reflected) {
         if (!reflected) {
            reflected = true;

            try {
               Class<?> entryClass = Entry.class;

               for (Constructor<?> c : entryClass.getDeclaredConstructors()) {
                  Class<?>[] params = c.getParameterTypes();
                  if (params.length == 1 && File.class.isAssignableFrom(params[0])) {
                     c.setAccessible(true);
                     entryConstructor = c;
                     break;
                  }

                  if (params.length == 2 && File.class.isAssignableFrom(params[1])) {
                     c.setAccessible(true);
                     entryConstructor = c;
                     break;
                  }
               }

               for (Field f : entryClass.getDeclaredFields()) {
                  if (File.class.isAssignableFrom(f.getType())) {
                     f.setAccessible(true);
                     resourcePackFileField = f;
                     break;
                  }
               }
            } catch (Exception var6) {
               var6.printStackTrace();
            }
         }
      }
   }

   public Entry createEntryForFile(File packFile) {
      if (packFile != null && packFile.exists()) {
         reflectEntryClass();
         if (entryConstructor == null) {
            return null;
         } else {
            try {
               Entry entry;
               if (entryConstructor.getParameterTypes().length == 2) {
                  entry = (Entry)entryConstructor.newInstance(this.loader, packFile);
               } else {
                  entry = (Entry)entryConstructor.newInstance(packFile);
               }

               initializeEntry(entry);
               return entry;
            } catch (Exception var3) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   /**
    * 1.0.2_18：这段按「调完之后 getResourcePack() 不为空」来猜初始化方法，会把 Entry
    * 上所有无参 void 方法挨个 invoke 一遍。这里至少把带明显破坏性的名字排除掉，
    * 避免顺手调到 close / delete / dispose 之类的清理方法。
    */
   private static boolean isDestructive(Method method) {
      String name = method.getName().toLowerCase(java.util.Locale.ROOT);
      return name.contains("close") || name.contains("delete") || name.contains("dispose")
            || name.contains("clear") || name.contains("shutdown") || name.contains("remove");
   }

   private static void initializeEntry(Entry entry) {
      if (entry != null && entry.getResourcePack() == null) {
         for (Method method : Entry.class.getDeclaredMethods()) {
            if (!isDestructive(method)
                  && method.getParameterTypes().length == 0 && method.getReturnType() == void.class
                  && !Modifier.isStatic(method.getModifiers())) {
               try {
                  method.setAccessible(true);
                  method.invoke(entry);
               } catch (Exception var6) {
                  ClientLog.warn("反射调用资源包条目方法失败", var6);
               }

               if (entry.getResourcePack() != null) {
                  return;
               }
            }
         }
      }
   }

   public static File getFileFromEntry(Entry entry) {
      if (entry == null) {
         return null;
      } else {
         reflectEntryClass();

         try {
            if (resourcePackFileField != null) {
               return (File)resourcePackFileField.get(entry);
            }
         } catch (Exception var2) {
            ClientLog.warn("反射读取资源包条目文件字段失败", var2);
         }

         return null;
      }
   }

   public File findPackFile(String rawPath) {
      if (rawPath != null && !rawPath.trim().isEmpty()) {
         File rootDir = PackManager.getInstance().getRootDir();
         String normalized = rawPath.replace('\\', '/').trim();
         File direct = new File(rootDir, normalized);
         if (direct.exists()) {
            return direct;
         } else {
            File rootFile = new File(rootDir, new File(normalized).getName());
            if (rootFile.exists()) {
               return rootFile;
            } else {
               for (PackEntry indexed : PackManager.getInstance().getIndexer().getAllIndexedPacks()) {
                  if (indexed.getRelativePath().equalsIgnoreCase(normalized) || indexed.getFile().getName().equalsIgnoreCase(normalized)) {
                     return indexed.getFile();
                  }
               }

               return null;
            }
         }
      } else {
         return null;
      }
   }

   public List<Entry> getSelectedRepositoryEntries() {
      List<Entry> selected = new ArrayList<>();
      List<Entry> current = this.loader.getSelectedResourcePacks();
      if (current != null) {
         selected.addAll(current);
      }

      return selected;
   }

   public void applySelectedPacks(List<PackEntry> selectedEntries) {
      List<Entry> repoEntries = new ArrayList<>();
      List<String> settingNames = new ArrayList<>();
      File rootDir = PackManager.getInstance().getRootDir();

      for (PackEntry entry : selectedEntries) {
         if (entry.isPack() && entry.getFile() != null) {
            Entry repoEntry = null;
            Object cached = entry.getRepositoryEntry();
            if (cached instanceof Entry) {
               repoEntry = (Entry)cached;
            }

            if (repoEntry == null) {
               repoEntry = this.createEntryForFile(entry.getFile());
               entry.setRepositoryEntry(repoEntry);
            }

            if (repoEntry != null) {
               repoEntries.add(repoEntry);
            }

            settingNames.add(FolderUtils.getRelativePath(rootDir, entry.getFile()));
         }
      }

      this.loader.setSelectedResourcePacks(repoEntries);
      LOGGER.info("Applying {} pack(s): {}", new Object[]{repoEntries.size(), settingNames});
      List<String> currentSettings = this.mc.options.resourcePacks;
      boolean changed = currentSettings == null || !settingNames.equals(currentSettings);
      if (currentSettings != null) {
         currentSettings.clear();
         currentSettings.addAll(settingNames);
      }

      this.mc.options.save();
      if (changed) {
         this.mc.reloadResources();
      }
   }
}
