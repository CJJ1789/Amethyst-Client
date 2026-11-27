package net.client.mixin;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Button;
import net.client.AccountScreen;
import net.client.Lang;
import net.client.ClientConfig;
import net.client.util.Accounts;
import net.client.ClientScreen;
import net.client.ui.ClientTheme;
import java.util.List;
import net.client.ui.ClientUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Title-screen integration: the Realms button is replaced by the client settings
 * entry.  接管开启时入口是 MD3 按钮；接管关闭时入口改回原版同尺寸按钮，避免两种
 * 按钮语言混在同一行。账号胶囊固定在左上角。
 */
@Mixin(value = {TitleScreen.class}, priority = 1200)
public class TitleScreenMixin {
   private static final float SETTINGS_W = 200f;
   private static final float SETTINGS_H = 20f;
   /** 1.0.2_9：账号 / 回放收成右上角两颗圆形图标按钮，横向排列。 */
   private static final float ICON = 24f;
   private static final float ICON_GAP = 6f;
   private static final float ICON_MARGIN = 10f;
   private static final float ICON_TOP = 8f;

   private final Md3Canvas lc$canvas = new Md3Canvas();
   private final Md3Button lc$account = new Md3Button(Md3Button.Variant.TONAL, "");
   private final Md3Button lc$settings = new Md3Button(Md3Button.Variant.FILLED, "");
   private final Md3Button lc$replay = new Md3Button(Md3Button.Variant.TONAL, "");
   private float lc$settingsX;
   private float lc$settingsY;
   private float lc$accountX;
   private float lc$replayX;

   private Screen self() {
      return (Screen) (Object) this;
   }


   @Inject(method = {"init"}, at = {@At("TAIL")})
   private void client$replaceRealms(CallbackInfo ci) {
      // The title screen is the first point where the launcher session and the
      // saved account file are both available; restore the last choice here.
      Accounts.bootstrap();
      TitleScreen self = (TitleScreen) (Object) this;
      ButtonWidget realms = ((TitleScreenAccessor) self).client$realmsButton();
      this.lc$settingsX = realms != null ? realms.x : self.width / 2f - SETTINGS_W / 2f;
      // Realms 是原版第 3 行；找不到时按 1.8.9 的默认布局（height/4 + 48）推算。
      this.lc$settingsY = realms != null ? realms.y : self.height / 4f + 48f + 48f;
      // 1.0.2_9：账号 / 回放两颗图标贴在右上角，从右往左排（账号最右）。
      this.lc$accountX = self.width - ICON_MARGIN - ICON;
      this.lc$replayX = this.lc$accountX - ICON_GAP - ICON;
      if (realms != null) {
         // 1.0.2_15：不再自己画一个圆角不一样的按钮，直接把原版 Realms 这颗改成
         // 「客户端设置」，圆角/纹理/悬停全跟原版按钮一致。
         client$setButtonText(realms, Lang.tr("menu.open"));
         realms.visible = true;
      }
      List<ButtonWidget> buttons = ((ScreenAccessor) self).client$buttons();
      // 1.0.2_16：Realms 按钮离线登录也一定存在，不再做「找不到就补一颗」的兜底，
      // 入口统一由原版那颗按钮承载。
      // 1.0.2_6：标题界面统一成参考图那种「半透明白 + 自然圆角」，连账号胶囊和回放入口。
      this.lc$account.glassStyle = true;
      this.lc$settings.glassStyle = true;
      this.lc$replay.glassStyle = true;
      this.lc$account.circle = true;
      this.lc$replay.circle = true;
      this.lc$account.variant = Md3Button.Variant.TONAL;
      this.lc$account.icon = "account_circle";
      this.lc$settings.variant = Md3Button.Variant.TONAL;
      this.lc$settings.icon = "tune";
      this.lc$settings.label = Lang.tr("menu.open");
      this.lc$replay.variant = Md3Button.Variant.TONAL;
      this.lc$replay.icon = "animation";
      this.lc$replay.label = "";
      this.lc$replay.labelType = Md3Type.LABEL_SMALL;
      this.lc$account.label = "";
      this.lc$account.labelType = Md3Type.LABEL_SMALL;
      this.lc$settings.labelType = Md3Type.LABEL_SMALL;
   }

   /** 反射改原版按钮文字：1.8.9 的字段名随映射不同，按候选名逐个试。 */
   private static void client$setButtonText(ButtonWidget b, String text) {
      for (String name : new String[]{"displayString", "text", "message", "field_146126_j"}) {
         try {
            java.lang.reflect.Field f = b.getClass().getField(name);
            if (f.getType() == String.class) {
               f.set(b, text);
               return;
            }
         } catch (Throwable ignored) {
         }
      }
      for (java.lang.reflect.Field f : b.getClass().getDeclaredFields()) {
         if (f.getType() == String.class && !java.lang.reflect.Modifier.isFinal(f.getModifiers())) {
            try {
               f.setAccessible(true);
               f.set(b, text);
               return;
            } catch (Throwable ignored) {
            }
         }
      }
   }

   private boolean client$hitRealms(int mouseX, int mouseY) {
      ButtonWidget realms = ((TitleScreenAccessor) (Object) this).client$realmsButton();
      return realms != null && realms.visible
              && ClientUi.hit(mouseX, mouseY, realms.x, realms.y, SETTINGS_W, SETTINGS_H);
   }

   @Inject(method = {"areRealmsNotificationsEnabled"}, at = {@At("HEAD")}, cancellable = true)
   private void client$noRealms(CallbackInfoReturnable<Boolean> cir) {
      cir.setReturnValue(false);
   }

   /**
    * 1.0.2_2：标题界面和客户端设置页共用同一层背景。
    * 原版这里画的是 TitleScreen 自己那套 renderPanorama（按 Screen.ticks 计时），
    * 设置页画的是 ClientPanorama（按系统时钟），两套实现两份相位，切过去看得见跳变。
    * 开启「接管原版界面」后直接换成 ClientPanorama：同一份代码、同一个时钟、同一张纹理。
    */
   @Inject(method = {"renderBackground(IIF)V"}, at = {@At("HEAD")}, cancellable = true)
   private void client$sharedPanorama(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.world != null || !ClientConfig.uiOverride) {
         return;
      }
      int w = ((ScreenAccessor) (Object) this).client$width();
      int h = ((ScreenAccessor) (Object) this).client$height();
      if (w <= 0 || h <= 0) {
         return;
      }
      // 1.0.2_15：不再 cancel 原版 renderBackground —— 原来这里直接换成 ClientPanorama，
      // 结果原版的全景图和 Blur 的后处理都被吃掉（标题页背景变成清晰实景）。
      // 现在交回原版：原版全景 + Blur 照常工作，设置页也用同一套（见 ClientUi.panoramaLayer）。
      return;
   }

   @Inject(method = {"render"}, at = {@At("TAIL")})
   private void client$renderButtons(int mouseX, int mouseY, float delta, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return;
      }
      Screen screen = this.self();

      Md3Theme t = ClientTheme.get();
      this.lc$canvas.begin(screen.width, screen.height);

      this.lc$account.surface = 0;
      this.lc$account.setBounds(this.lc$accountX, ICON_TOP, ICON, ICON);
      this.lc$account.render(this.lc$canvas, t, mouseX, mouseY, Mouse.isButtonDown(0), delta);

      // 接管开启时用 MD3 按钮覆盖 Realms；接管关闭时由 init 里的原版按钮负责显示。
      // 1.0.2_15：客户端设置入口改由原版 Realms 按钮承载，这里不再自绘。
      if (net.client.replay.ReplayClient.isReady()) {
         this.lc$replay.setBounds(this.lc$replayX, ICON_TOP, ICON, ICON);
         this.lc$replay.render(this.lc$canvas, t, mouseX, mouseY, Mouse.isButtonDown(0), delta);
      }

      this.lc$canvas.end();
   }


   @Inject(method = {"mouseClicked"}, at = {@At("HEAD")}, cancellable = true)
   private void client$clickButtons(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || mouseButton != 0) {
         return;
      }
      Screen screen = this.self();
      if (ClientUi.hit(mouseX, mouseY, this.lc$accountX, ICON_TOP, ICON, ICON)) {
         ClientUi.click();
         client.setScreen(new AccountScreen(screen));
         ci.cancel();
      } else if (client$hitRealms(mouseX, mouseY)) {
         ClientUi.click();
         client.setScreen(new ClientScreen());
         ci.cancel();
      } else if (net.client.replay.ReplayClient.isReady()
              && ClientUi.hit(mouseX, mouseY, this.lc$replayX, ICON_TOP, ICON, ICON)) {
         ClientUi.click();
         net.client.replay.ReplayClient.openConsole();
         ci.cancel();
      }
   }
}
