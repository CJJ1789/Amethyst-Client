package net.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.client.ClientConfig;
import net.client.util.BlockHighlightRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.BlockHitResult.Type;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {WorldRenderer.class},
   priority = 1200
)
public class WorldRendererMixin {
   @Inject(
      method = {"drawBlockOutline"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$blockHighlight(PlayerEntity player, BlockHitResult hit, int mode, float tickDelta, CallbackInfo ci) {
      if (ClientConfig.highlightBlock) {
         // 界面打开时不再画方块高亮：贴着箱子开界面时，近距离的整块高亮框会被
         // 背景模糊一起采进去，变成一大片亮色块，看起来就像闪了一下。
         if (MinecraftClient.getInstance().currentScreen != null) {
            ci.cancel();
            return;
         }
         if (hit != null && hit.type == Type.BLOCK) {
            ci.cancel();
            BlockHighlightRenderer.render(hit, tickDelta);
         }
      }
   }

   @Redirect(
      method = {"renderSky"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/world/ClientWorld;method_3631(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/math/Vec3d;"
      )
   )
   private Vec3d client$skyFollowFog(ClientWorld world, Entity entity, float delta) {
      Vec3d vanilla = world.method_3631(entity, delta);
      if (!ClientConfig.fogModify) {
         return vanilla;
      } else {
         int c = ClientConfig.fogColor;
         return new Vec3d((double)(c >> 16 & 0xFF) / 255.0, (double)(c >> 8 & 0xFF) / 255.0, (double)(c & 0xFF) / 255.0);
      }
   }

   @Inject(
      method = {"renderSky"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$skyOverlay(float tickDelta, int anaglyphFilter, CallbackInfo ci) {
      if (ClientConfig.fogModify) {
         int c = ClientConfig.fogColor;
         float r = (float)(c >> 16 & 0xFF) / 255.0F;
         float g = (float)(c >> 8 & 0xFF) / 255.0F;
         float b = (float)(c & 0xFF) / 255.0F;
         GL11.glDisable(3089);
         GL11.glColorMask(true, true, true, true);
         GlStateManager.clearColor(r, g, b, 1.0F);
         GL11.glClear(16384);
         ci.cancel();
      }
   }
}
