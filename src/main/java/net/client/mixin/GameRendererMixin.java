package net.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.FloatBuffer;
import net.client.ClientConfig;
import net.client.util.AfterimageHandler;
import net.client.util.FovController;
import net.client.util.FreelookHandler;
import net.client.util.OverlayRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LayeredBiomeSource;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {GameRenderer.class},
   priority = 1200
)
public class GameRendererMixin {
   @Inject(
      method = {"setupCamera(FI)V"},
      at = {@At("HEAD")}
   )
   private void client$freelookApply(float tickDelta, int anaglyphFilter, CallbackInfo ci) {
      FreelookHandler.applyCamera();
   }

   @Inject(
      method = {"setupCamera(FI)V"},
      at = {@At("RETURN")}
   )
   private void client$freelookRestore(float tickDelta, int anaglyphFilter, CallbackInfo ci) {
      FreelookHandler.restoreCamera();
   }

   @Shadow
   private FloatBuffer updateFogColorBuffer(float red, float green, float blue, float alpha) {
      return null;
   }

   @Inject(
      method = {"renderWorld(IFJ)V"},
      at = {@At("HEAD")}
   )
   private void client$worldDepth(int anaglyphFilter, float tickDelta,
                                         long limitTime, CallbackInfo ci) {
      // A GUI/HUD text pass must not leave depth testing off for the next
      // world frame, otherwise signs and blocks draw through terrain.
      GlStateManager.enableDepthTest();
      GlStateManager.depthMask(true);
   }

   @Inject(
      method = {"renderWorld(IFJ)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FI)V",
         shift = Shift.BEFORE
      )}
   )
   private void client$afterimage(int anaglyphFilter, float tickDelta, long limitTime, CallbackInfo ci) {
      AfterimageHandler.onRenderWorld(tickDelta);
   }

   @Inject(
      method = {"renderWorld(IFJ)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I",
         ordinal = 0,
         shift = Shift.BEFORE
      )}
   )
   private void client$skyFill(int anaglyphFilter, float tickDelta, long limitTime, CallbackInfo ci) {
      if (ClientConfig.fogModify) {
         int c = ClientConfig.fogColor;
         float r = (float)(c >> 16 & 0xFF) / 255.0F;
         float g = (float)(c >> 8 & 0xFF) / 255.0F;
         float b = (float)(c & 0xFF) / 255.0F;
         GL11.glDisable(3089);
         GL11.glColorMask(true, true, true, true);
         GlStateManager.clearColor(r, g, b, 1.0F);
         GL11.glClear(16384);
      }
   }

   @Inject(
      method = {"bobViewWhenHurt"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$noHurtCam(float tickDelta, CallbackInfo ci) {
      if (ClientConfig.noHurtCam) {
         Entity camera = MinecraftClient.getInstance().getCameraEntity();
         if (!(camera instanceof LivingEntity) || !(((LivingEntity)camera).getHealth() <= 0.0F)) {
            ci.cancel();
         }
      }
   }

   @Redirect(
      method = {"updateLightmap"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/option/GameOptions;gamma:F",
         opcode = 180
      )
   )
   private float client$gamma(GameOptions options) {
      return ClientConfig.fullbright ? 100.0F : options.gamma;
   }

   @Inject(
      method = {"bobView"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$minimalBob(float tickDelta, CallbackInfo ci) {
      if (ClientConfig.minimalBobbing && MinecraftClient.getInstance().options.bobView) {
         ci.cancel();
      }
   }

   @Redirect(
      method = {"getFov"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/option/GameOptions;fov:F",
         opcode = 180
      )
   )
   private float client$fovBase(GameOptions options) {
      // 1.0.1_13：总开关关闭时把原版值原样交回去，OptiFine 的缩放照常生效；
      // 打开时由客户端提供默认视野（fovBase × fovGlobal），最终值在 RETURN 里统一接管。
      return ClientConfig.fovClient ? ClientConfig.fovBase * ClientConfig.fovGlobal : options.fov;
   }

   // 1.0.1_13：FOV 改成「客户端 FOV」模型（Badlion 式）：总开关 + 默认值 + 六个条件各自的目标值。
   // 打开时这里直接返回客户端算好的视野，原版的死亡/水下修正、OptiFine 的缩放都不再叠加；
   // 关闭时整个注入直接放行，返回原版结果，OptiFine 的 FOV 功能保持原样。
   @Inject(
      method = {"getFov"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void client$fovReturn(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
      if (!ClientConfig.fovClient) {
         FovController.reset();
         return;
      }

      if (!changingFov) {
         return;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world == null) {
         return;
      }

      cir.setReturnValue(FovController.apply(client, tickDelta));
   }

   @Redirect(
      method = {"renderFog"},
      at = @At(
         value = "INVOKE",
         target = "Lorg/lwjgl/opengl/GL11;glFog(ILjava/nio/FloatBuffer;)V"
      )
   )
   private void client$fogColor(int pname, FloatBuffer buffer) {
      if (ClientConfig.fogModify) {
         int color = ClientConfig.fogColor;
         buffer = this.updateFogColorBuffer((float)(color >> 16 & 0xFF) / 255.0F, (float)(color >> 8 & 0xFF) / 255.0F, (float)(color & 0xFF) / 255.0F, 1.0F);
      }

      GL11.glFog(pname, buffer);
   }

   @Redirect(
      method = {"renderFog"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/platform/GlStateManager;fogStart(F)V"
      )
   )
   private void client$fogStart(float value) {
      GlStateManager.fogStart(value / clampDensity());
   }

   @Redirect(
      method = {"renderFog"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/platform/GlStateManager;fogEnd(F)V"
      )
   )
   private void client$fogEnd(float value) {
      GlStateManager.fogEnd(value / clampDensity());
   }

   private static float clampDensity() {
      float d = ClientConfig.fogDensity;
      if (d < 0.1F) {
         d = 0.1F;
      }

      if (d > 30.0F) {
         d = 30.0F;
      }

      return d;
   }

   @Redirect(
      method = {"renderWeather"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/World;method_8562(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;"
      ),
      require = 0
   )
   private BlockPos client$precipHeight(World world, BlockPos pos) {
      BlockPos height = world.method_8562(pos);
      return ClientConfig.weatherMode > 0 && ClientConfig.weatherThroughBlocks ? new BlockPos(height.getX(), 0, height.getZ()) : height;
   }

   @Redirect(
      method = {"renderWeather"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/LayeredBiomeSource;method_3852(FI)F"
      ),
      require = 0
   )
   private float client$weatherTemperature(LayeredBiomeSource source, float temperature, int seaLevel) {
      int mode = ClientConfig.weatherMode;
      if (mode == 4) {
         return 0.0F;
      } else {
         return mode != 2 && mode != 3 ? source.method_3852(temperature, seaLevel) : 1.0F;
      }
   }

   /**
    * 1.0.1_4：下雪模式下不要再生成雨的落地水花。
    * GameRenderer.tickRainSplashing() 用「雨强度 > 0 且群系温度 >= 0.15」来决定是否在地面
    * 溅起 WATER_DROP 粒子；雪只改了 renderWeather 里的温度取样，这里用的还是真实群系温度，
    * 所以在温暖群系开雪会一边飘雪一边满地雨点。雪天直接不跑这套水花/雨声逻辑。
    */
   @Inject(
      method = {"tickRainSplashing"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$noRainSplashWhileSnow(CallbackInfo ci) {
      if (ClientConfig.weatherMode == 4) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderWorld"},
      at = {@At("TAIL")}
   )
   private void client$worldTail(float tickDelta, long nanoTime, CallbackInfo ci) {
      OverlayRender.render(tickDelta);
   }
}
