package net.client.mixin;

import net.client.util.ServerListDrag;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Backdrop: MD3 surface for menu screens, blur + dim while in world. */
@Mixin({Screen.class})
public class ScreenMixin {
   // 1.0.2_18：删掉 here 原有的三段 renderBackground / renderDirtBackground 注入。
   // 它们自 1.0.2_17 起已经是空实现（方法体直接 return），却仍然 cancellable，
   // 与 com.amethyst.blur.mixin.ScreenMixin 抢同一个方法的 HEAD —— 谁先执行由加载顺序决定。
   // 原版页面背景交回原版 / Blur，这里不再插手。

   /** 标记 Screen 渲染区间，供 TextColorMixin 判断「这段文字是不是菜单里的」。 */
   @Inject(method = {"render(IIF)V"}, at = {@At("HEAD")})
   private void client$enterScreenRender(int mouseX, int mouseY, float delta, CallbackInfo ci) {
      net.client.ui.ScreenRenderFlag.begin();
   }

   @Inject(method = {"render(IIF)V"}, at = {@At("RETURN")})
   private void client$exitScreenRender(int mouseX, int mouseY, float delta, CallbackInfo ci) {
      net.client.ui.ScreenRenderFlag.end();
   }

   @Inject(method = {"mouseDragged(IIIJ)V"}, at = {@At("HEAD")}, cancellable = true)
   private void client$serverListDrag(int mouseX, int mouseY, int button, long time,
                                              CallbackInfo ci) {
      if ((Object) this instanceof MultiplayerScreen
              && ServerListDrag.onDrag((MultiplayerScreen)(Object)this, mouseX, mouseY, button)) {
         // While a row is being reordered, do not let EntryListWidget also
         // handle the same drag (which made the list scroll under the row).
         ci.cancel();
      }
   }

}
