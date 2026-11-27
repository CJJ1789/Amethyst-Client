// Upstream: FastPacks / McPacks 1.0 (Forge 1.8.9), MIT
//   https://modrinth.com/mod/fastpacks (author: McPacks Team; Modrinth owner: Dulandor)
// Ported and modified for Legacy Fabric by this project; upstream MIT notice retained
// (see licenses/FASTPACKS-MIT.txt). The port was never released standalone.

package com.mcpacks.util;

import java.util.Locale;
import java.util.regex.Matcher;
import net.minecraft.client.MinecraftClient;

public final class Lang {
   private Lang() {
   }

   public static boolean isChinese() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.options != null) {
         try {
            String language = mc.options.language;
            return language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
         } catch (Exception var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static String s(String en, String zh) {
      return isChinese() ? zh : en;
   }

   public static String s(String en, String zh, Object... args) {
      String pattern = isChinese() ? zh : en;

      for (Object arg : args) {
         pattern = pattern.replaceFirst("%s", Matcher.quoteReplacement(String.valueOf(arg)));
      }

      return pattern;
   }
}
