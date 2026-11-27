package net.client.mixin;

import net.client.ClientConfig;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
   value = {ItemRenderer.class},
   priority = 1200
)
public abstract class ItemRendererMixin {
   @Shadow
   private void renderBakedItemModel(BakedModel model, int color) {
      throw new AssertionError("shadow");
   }

   @Redirect(
      method = {"renderGlint"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;I)V"
      )
   )
   private void client$glintColor(ItemRenderer instance, BakedModel model, int color) {
      this.renderBakedItemModel(model, ClientConfig.customGlint ? ClientConfig.glintColor : color);
   }
}
