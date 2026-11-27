package net.client.mixin;

import net.client.ClientConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 按设置跳过玻璃方块模型，只影响世界渲染，不影响手持/物品栏图标。 */
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
   @Inject(
      method = "render(Lnet/minecraft/world/BlockView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/BufferBuilder;Z)Z",
      at = @At("HEAD"),
      cancellable = true
   )
   private void client$hideGlass(BlockView world, BakedModel model, BlockState state,
                                        BlockPos pos, BufferBuilder buffer, boolean cull,
                                        CallbackInfoReturnable<Boolean> cir) {
      int mode = ClientConfig.glassMode;
      if (mode <= 0) {
         return;
      }
      Block block = state.getBlock();
      boolean clear = block == Blocks.GLASS || block == Blocks.GLASS_PANE;
      boolean stained = block == Blocks.STAINED_GLASS || block == Blocks.STAINED_GLASS_PANE;
      if (clear || (mode == 1 && stained)) {
         cir.setReturnValue(false);
      }
   }
}
