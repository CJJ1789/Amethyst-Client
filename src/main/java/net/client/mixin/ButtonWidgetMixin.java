package net.client.mixin;

import net.client.ui.md3.render.Md3Canvas;
import net.client.ui.md3.theme.Md3Colors;
import net.client.ui.md3.theme.Md3Theme;
import net.client.ui.md3.theme.Md3Type;
import net.client.ui.md3.widget.Md3Text;
import net.client.ClientConfig;
import net.client.ui.ClientTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When "UI override" is enabled every vanilla button is redrawn as an MD3
 * common button.  Each button gets its own short-lived canvas so the vanilla
 * projection / matrix stack is restored afterwards.
 */
@Mixin({ButtonWidget.class})
public class ButtonWidgetMixin {
   @Shadow
   protected int width;
   @Shadow
   protected int height;
   @Shadow
   public int x;
   @Shadow
   public int y;
   @Shadow
   public String message;
   @Shadow
   public boolean active;
   @Shadow
   public boolean visible;
   @Shadow
   protected boolean hovered;

   private final Md3Canvas lx$canvas = new Md3Canvas();

   @Inject(
      method = {"render(Lnet/minecraft/client/MinecraftClient;II)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void client$flatButton(MinecraftClient client, int mouseX, int mouseY, CallbackInfo ci) {
      if (!ClientConfig.uiOverride || !this.visible) {
         return;
      }
      this.hovered = mouseX >= this.x && mouseY >= this.y
              && mouseX < this.x + this.width && mouseY < this.y + this.height;

      Md3Theme t = ClientTheme.get();
      // MinecraftClient.width/height are framebuffer pixels; Screen.width/height
      // are the scaled GUI coordinates every widget uses.  Using the former here
      // shrinks the whole button into the top-left corner.
      int guiW = client.currentScreen != null ? client.currentScreen.width : client.width;
      int guiH = client.currentScreen != null ? client.currentScreen.height : client.height;
      this.lx$canvas.begin(guiW, guiH);

      // 1.0.2_7：接管开关打开后，所有原版按钮统一成「半透明玻璃白 + 自然圆角」。
      // 这个 mixin 只在 uiOverride 为真时才接管绘制，所以进到这里就是接管模式。
      boolean glass = true;
      int base;
      int content;
      float radius;
      if (glass) {
         // 不压饱和度、不描边，只用白色薄薄一层，背景能透出来。
         base = this.active
                 ? Md3Colors.alpha(0xFFFFFF, this.hovered ? 0.56f : 0.44f)
                 : Md3Colors.alpha(0xFFFFFF, 0.10f);
         content = this.active ? 0xFFFFFFFF : Md3Colors.alpha(0xFFFFFF, 0.45f);
         radius = Math.min(6f, this.height / 2f);
      } else {
         base = !this.active ? t.colors.surfaceContainerHighest
                 : (this.hovered ? Md3Colors.composite(
                         Md3Colors.alpha(t.colors.primary, 0.08f), t.colors.secondaryContainer)
                         : t.colors.secondaryContainer);
         content = this.active ? t.colors.onSecondaryContainer
                 : Md3Colors.alpha(t.colors.onSurface, 0.38f);
         radius = Math.min(10f, this.height / 2f);
      }
      this.lx$canvas.roundRect(this.x, this.y, this.width, this.height, radius, base);
      if (!this.active) {
         this.lx$canvas.strokeRoundRect(this.x, this.y, this.width, this.height,
                 radius, 1f, Md3Colors.alpha(t.colors.onSurface, 0.10f));
      }

      TextRenderer tr = client.textRenderer;
      String text = this.message == null ? "" : this.message;
      if (tr != null) {
         text = tr.trimToWidth(text, this.width - 28);
      }
      // 与“现代化字体”共用同一套 Md3Type.LABEL_SMALL（10px medium），
      // 不再单独缩到 0.8 倍，否则接管界面和现代字体看起来是两套字体。
      String fitted = fit(this.lx$canvas, text, this.width - 24f);
      float cxText = this.x + this.width / 2f;
      float cyText = this.y + this.height / 2f;
      if (glass) {
         // 白色玻璃按钮上是白字，压一层浅阴影，浅色背景也能读清。
         Md3Text.centered(this.lx$canvas, fitted, cxText + 1f, cyText + 1f,
                 Md3Type.LABEL_SMALL, Md3Colors.alpha(0x000000, 0.30f));
      }
      Md3Text.centered(this.lx$canvas, fitted, cxText, cyText,
              Md3Type.LABEL_SMALL, content);

      this.lx$canvas.end();
      ci.cancel();
   }

   private static String fit(Md3Canvas c, String text, float maxWidth) {
      if (c.textWidth(text, Md3Type.LABEL_SMALL) <= maxWidth) {
         return text;
      }
      int end = text.length();
      while (end > 0 && c.textWidth(text.substring(0, end) + "...",
              Md3Type.LABEL_SMALL) > maxWidth) {
         end--;
      }
      return text.substring(0, end) + "...";
   }
}
