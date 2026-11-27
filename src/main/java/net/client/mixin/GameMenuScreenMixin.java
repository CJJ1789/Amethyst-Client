package net.client.mixin;

import net.client.ClientConfig;
import net.client.ClientScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在暂停菜单“成就/统计”和“选项/局域网”之间插入客户端设置入口。 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin {
   @Inject(method = "init", at = @At("TAIL"))
   private void client$addSettingsButton(CallbackInfo ci) {
      ScreenAccessor screen = (ScreenAccessor)(Object)this;
      int x = screen.client$width() / 2 - 100;
      int y = screen.client$height() / 4 + 56;
      screen.client$buttons().add(new ButtonWidget(8, x, y, 200, 20,
            ClientConfig.t("客户端设置", "Client settings")));
   }

   @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
   private void client$openSettings(ButtonWidget button, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (button.id == 8 && mc != null) {
         mc.setScreen(new ClientScreen());
         ci.cancel();
      }
   }
}
