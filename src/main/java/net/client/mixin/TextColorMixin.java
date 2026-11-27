package net.client.mixin;

import net.client.ClientConfig;
import net.client.ui.ClientTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** In light mode, remap white / light-grey vanilla label colours to the MD3 on-surface role. */
@Mixin({TextRenderer.class})
public class TextColorMixin {
   @ModifyVariable(
      method = {"draw(Ljava/lang/String;III)I"},
      at = @At("HEAD"),
      argsOnly = true,
      index = 4,
      require = 0
   )
   private int client$recolor(int color) {
      return client$map(color);
   }

   @ModifyVariable(
      method = {"draw(Ljava/lang/String;FFIZ)I"},
      at = @At("HEAD"),
      argsOnly = true,
      index = 4,
      require = 0
   )
   private int client$recolorFloat(int color) {
      return client$map(color);
   }

   private static int client$map(int color) {
      if (!ClientConfig.lightTheme || !ClientConfig.uiOverride) {
         return color;
      }
      // 只在菜单壳渲染期间重映射；HUD / 聊天栏 / 世界里画在游戏画面上的文字保持原色，
      // 否则浅色主题下这些白字会被改成深色，压在暗背景上直接看不见。
      if (!net.client.ui.ScreenRenderFlag.active()) {
         return color;
      }
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.currentScreen == null) {
         return color;
      }
      // 只改本客户端自己的 MD3 界面里的文字。世界里的名牌、告示牌、聊天栏、
      // boss 条等仍然压在游戏画面上，浅色主题把白字改成深色会直接看不见。
      String screenName = mc.currentScreen.getClass().getName();
      if (!screenName.startsWith("net.client.") && !screenName.startsWith("com.mcpacks.")) {
         return color;
      }
      // F3 调试层属于 HUD，不属于菜单板面；暂停菜单里它会和游戏画面一起
      // 保留原版的白色字，否则浅色主题下会变成黑字直接看不见。
      if (mc.options != null && mc.options.debugEnabled
              && mc.currentScreen instanceof GameMenuScreen) {
         return color;
      }
      int rgb = color & 0xFFFFFF;
      if (rgb == 0xFFFFFF || rgb == 0xE0E0E0) {
         return ClientTheme.get().colors.onSurface;
      }
      return color;
   }
}
