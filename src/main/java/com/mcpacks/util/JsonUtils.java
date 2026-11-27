// Upstream: FastPacks / McPacks 1.0 (Forge 1.8.9), MIT
//   https://modrinth.com/mod/fastpacks (author: McPacks Team; Modrinth owner: Dulandor)
// Ported and modified for Legacy Fabric by this project; upstream MIT notice retained
// (see licenses/FASTPACKS-MIT.txt). The port was never released standalone.

package com.mcpacks.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.client.util.ClientLog;

public final class JsonUtils {
   private JsonUtils() {
   }

   public static JsonUtils.PackMeta parsePackMeta(InputStream is) {
      if (is == null) {
         return new JsonUtils.PackMeta(1, "");
      } else {
         try {
            InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);

            JsonUtils.PackMeta var11;
            label66: {
               JsonUtils.PackMeta var12;
               label65: {
                  JsonUtils.PackMeta var13;
                  try {
                     JsonParser parser = new JsonParser();
                     JsonElement root = parser.parse(reader);
                     if (root == null || !root.isJsonObject()) {
                        var11 = new JsonUtils.PackMeta(1, "");
                        break label66;
                     }

                     JsonObject packObj = root.getAsJsonObject().getAsJsonObject("pack");
                     if (packObj == null) {
                        var12 = new JsonUtils.PackMeta(1, "");
                        break label65;
                     }

                     int format = packObj.has("pack_format") ? packObj.get("pack_format").getAsInt() : 1;
                     String desc = "";
                     if (packObj.has("description")) {
                        JsonElement descElem = packObj.get("description");
                        desc = descElem.isJsonPrimitive() ? descElem.getAsString() : descElem.toString();
                     }

                     var13 = new JsonUtils.PackMeta(format, desc);
                  } catch (Throwable var9) {
                     try {
                        reader.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }

                     throw var9;
                  }

                  reader.close();
                  return var13;
               }

               reader.close();
               return var12;
            }

            reader.close();
            return var11;
         } catch (Exception var10) {
            ClientLog.warn("解析 pack.mcmeta 失败，使用默认描述", var10);
            return new JsonUtils.PackMeta(1, "");
         }
      }
   }

   public static class PackMeta {
      public final int packFormat;
      public final String description;

      public PackMeta(int packFormat, String description) {
         this.packFormat = packFormat;
         this.description = description != null ? description : "";
      }
   }
}
