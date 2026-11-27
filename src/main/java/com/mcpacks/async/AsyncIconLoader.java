// Upstream: FastPacks / McPacks 1.0 (Forge 1.8.9), MIT
//   https://modrinth.com/mod/fastpacks (author: McPacks Team; Modrinth owner: Dulandor)
// Ported and modified for Legacy Fabric by this project; upstream MIT notice retained
// (see licenses/FASTPACKS-MIT.txt). The port was never released standalone.

package com.mcpacks.async;

import com.mcpacks.core.PackEntry;
import com.mcpacks.util.FolderUtils;
import com.mcpacks.util.IconUtils;
import com.mcpacks.util.JsonUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.client.util.ClientLog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

public class AsyncIconLoader {
   private static final AsyncIconLoader INSTANCE = new AsyncIconLoader();
   private final Map<String, Identifier> textureCache = new ConcurrentHashMap<>();
   private final Map<String, JsonUtils.PackMeta> metaCache = new ConcurrentHashMap<>();
   private Identifier folderTextureLoc = null;
   private Identifier parentNavTextureLoc = null;
   private Identifier defaultPackTextureLoc = null;

   private AsyncIconLoader() {
   }

   public static AsyncIconLoader getInstance() {
      return INSTANCE;
   }

   public void initDefaultTextures() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null) {
         TextureManager tm = mc.getTextureManager();
         if (this.folderTextureLoc == null) {
            this.folderTextureLoc = tm.registerDynamicTexture("mcpacks_folder_icon", new NativeImageBackedTexture(IconUtils.getFolderIcon()));
         }

         if (this.parentNavTextureLoc == null) {
            this.parentNavTextureLoc = tm.registerDynamicTexture("mcpacks_nav_icon", new NativeImageBackedTexture(IconUtils.getParentNavIcon()));
         }

         if (this.defaultPackTextureLoc == null) {
            this.defaultPackTextureLoc = tm.registerDynamicTexture("mcpacks_default_pack_icon", new NativeImageBackedTexture(IconUtils.getDefaultPackIcon()));
         }
      }
   }

   public Identifier getFolderTextureLoc() {
      if (this.folderTextureLoc == null) {
         this.initDefaultTextures();
      }

      return this.folderTextureLoc;
   }

   public Identifier getParentNavTextureLoc() {
      if (this.parentNavTextureLoc == null) {
         this.initDefaultTextures();
      }

      return this.parentNavTextureLoc;
   }

   public Identifier getDefaultPackTextureLoc() {
      if (this.defaultPackTextureLoc == null) {
         this.initDefaultTextures();
      }

      return this.defaultPackTextureLoc;
   }

   public void requestIconAndMeta(PackEntry entry) {
      if (entry != null) {
         if (entry.isParentNav()) {
            entry.setIconLocation(this.getParentNavTextureLoc());
            entry.setIconLoaded(true);
         } else if (entry.isFolder()) {
            entry.setIconLocation(this.getFolderTextureLoc());
            entry.setIconLoaded(true);
         } else {
            final File file = entry.getFile();
            if (file != null && file.exists()) {
               final String pathKey = file.getAbsolutePath();
               Identifier cached = this.textureCache.get(pathKey);
               if (cached != null) {
                  entry.setIconLocation(cached);
                  entry.setIconLoaded(true);
                  JsonUtils.PackMeta cachedMeta = this.metaCache.get(pathKey);
                  if (cachedMeta != null) {
                     entry.setPackFormat(cachedMeta.packFormat);
                     entry.setDescription(cachedMeta.description);
                     entry.setMetadataLoaded(true);
                  }
               } else {
                  entry.setIconLocation(this.getDefaultPackTextureLoc());
                  if (!entry.isIconLoadingStarted()) {
                     entry.setIconLoadingStarted(true);
                     ThreadPoolHelper.submitTask(new Runnable() {
                        @Override
                        public void run() {
                           AsyncIconLoader.this.loadPackAsync(entry, file, pathKey);
                        }
                     });
                  }
               }
            }
         }
      }
   }

   private void loadPackAsync(PackEntry entry, File file, String pathKey) {
      BufferedImage loadedImage = null;
      JsonUtils.PackMeta meta = null;
      if (FolderUtils.isResourcePackArchive(file)) {
         ZipFile zipFile = null;

         try {
            zipFile = new ZipFile(file);
            if (!entry.isMetadataLoaded()) {
               ZipEntry metaEntry = zipFile.getEntry("pack.mcmeta");
               if (metaEntry != null) {
                  InputStream is = zipFile.getInputStream(metaEntry);

                  try {
                     meta = JsonUtils.parsePackMeta(is);
                  } catch (Throwable var32) {
                     if (is != null) {
                        try {
                           is.close();
                        } catch (Throwable var28) {
                           var32.addSuppressed(var28);
                        }
                     }

                     throw var32;
                  }

                  if (is != null) {
                     is.close();
                  }
               }
            }

            ZipEntry iconEntry = zipFile.getEntry("pack.png");
            if (iconEntry != null) {
               InputStream is = zipFile.getInputStream(iconEntry);

               try {
                  loadedImage = ImageIO.read(is);
               } catch (Throwable var31) {
                  if (is != null) {
                     try {
                        is.close();
                     } catch (Throwable var27) {
                        var31.addSuppressed(var27);
                     }
                  }

                  throw var31;
               }

               if (is != null) {
                  is.close();
               }
            }
         } catch (Exception var33) {
            ClientLog.warn("读取资源包内置图标失败，保留默认图标", var33);
         } finally {
            if (zipFile != null) {
               try {
                  zipFile.close();
               } catch (Exception var25) {
                  ClientLog.warn("关闭图标 ZipFile 失败", var25);
               }
            }
         }
      } else if (FolderUtils.isResourcePackDirectory(file)) {
         try {
            File metaFile = new File(file, "pack.mcmeta");
            if (!entry.isMetadataLoaded() && metaFile.exists()) {
               InputStream is = new FileInputStream(metaFile);

               try {
                  meta = JsonUtils.parsePackMeta(is);
               } catch (Throwable var29) {
                  try {
                     is.close();
                  } catch (Throwable var26) {
                     var29.addSuppressed(var26);
                  }

                  throw var29;
               }

               is.close();
            }

            File iconFile = new File(file, "pack.png");
            if (iconFile.exists()) {
               loadedImage = ImageIO.read(iconFile);
            }
         } catch (Exception var30) {
            ClientLog.warn("读取文件夹资源包图标失败，保留默认图标", var30);
         }
      }

      if (meta != null) {
         entry.setPackFormat(meta.packFormat);
         entry.setDescription(meta.description);
         entry.setMetadataLoaded(true);
         this.metaCache.put(pathKey, meta);
      }

      final BufferedImage finalImage = loadedImage;
      MinecraftClient.getInstance().execute(new Callable<Void>() {
         public Void call() {
            try {
               TextureManager tm = MinecraftClient.getInstance().getTextureManager();
               Identifier finalLoc;
               if (finalImage != null) {
                  NativeImageBackedTexture texture = new NativeImageBackedTexture(finalImage);
                  String safeName = "pack_" + Math.abs(pathKey.hashCode());
                  finalLoc = tm.registerDynamicTexture(safeName, texture);
                  AsyncIconLoader.this.textureCache.put(pathKey, finalLoc);
               } else {
                  finalLoc = AsyncIconLoader.this.getDefaultPackTextureLoc();
               }

               if (finalImage != null) {
                  entry.setIconTextureSize(finalImage.getWidth(), finalImage.getHeight());
               }

               entry.setIconLocation(finalLoc);
               entry.setIconLoaded(true);
            } catch (Exception var5) {
               entry.setIconLocation(AsyncIconLoader.this.getDefaultPackTextureLoc());
               entry.setIconLoaded(true);
            }

            return null;
         }
      });
   }

   public void clearCache() {
      this.textureCache.clear();
      this.metaCache.clear();
   }
}
