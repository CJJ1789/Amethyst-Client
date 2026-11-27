package net.client.mixin;

import net.client.ime.ImeBridge;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 中文输入法桥的挂载点（1.0.1_20 重写）：
 * 渲染后同步组字串 / 钉候选窗 / 画内联组字；按键时按组字状态拦一道，避免字母二次落进输入框。
 */
@Mixin(TextFieldWidget.class)
public class TextFieldWidgetMixin {
   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void client$imeRender(CallbackInfo ci) {
      try {
         ImeBridge.onFieldRender((TextFieldWidget)(Object)this);
      } catch (Throwable ignored) {
      }
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$imeKey(char typedChar, int keyCode, CallbackInfoReturnable<Boolean> cir) {
      try {
         if (ImeBridge.handleKey(typedChar, keyCode)) {
            cir.setReturnValue(Boolean.TRUE);
         }
      } catch (Throwable ignored) {
      }
   }
}
