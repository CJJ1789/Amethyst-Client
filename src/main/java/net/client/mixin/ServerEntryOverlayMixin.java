package net.client.mixin;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.widget.ServerEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.8 的服务器条目在悬停时会画一个 32x32 的黑色半透明块，再叠三组排序/连接
 * 箭头。箭头来自 server_selection.png 的 32x32 区域，实际会互相重叠，
 * 在缩放后看起来像错位的三角形。客户端已经支持整行拖动排序，这里直接去掉
 * 整个悬停覆盖层，只保留服务器图标和文字。
 */
@Mixin(ServerEntry.class)
public class ServerEntryOverlayMixin {
   @Redirect(
           method = {"render"},
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/gui/DrawableHelper;fill(IIIII)V"
           )
   )
   private void client$skipHoverBackdrop(int x1, int y1, int x2, int y2, int color) {
      // no-op
   }

   /**
    * 只拦截 server_selection.png 的三组 32x32 箭头贴图（连接/上移/下移），
    * 右侧 10x8 的延迟图标仍按原样绘制。
    */
   @Redirect(
           method = {"render"},
           at = @At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/gui/DrawableHelper;drawTexture(IIFFIIFF)V"
           )
   )
   private static void client$skipSortArrows(int x, int y, float u, float v,
                                                     int width, int height,
                                                     float texWidth, float texHeight) {
      if (width == 32 && height == 32) {
         return;
      }
      DrawableHelper.drawTexture(x, y, u, v, width, height, texWidth, texHeight);
   }
}
