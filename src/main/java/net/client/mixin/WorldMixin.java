package net.client.mixin;

import net.client.ClientConfig;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {World.class},
   priority = 1200
)
public class WorldMixin {
   @Inject(
      method = {"getRainGradient"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$rainGradient(float delta, CallbackInfoReturnable<Float> cir) {
      World self = (World)(Object)this;
      if (self.isClient) {
         int mode = ClientConfig.weatherMode;
         if (mode > 0) {
            cir.setReturnValue(mode >= 2 ? 1.0F : 0.0F);
         }
      }
   }

   @Inject(
      method = {"getThunderGradient"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$thunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
      World self = (World)(Object)this;
      if (self.isClient) {
         int mode = ClientConfig.weatherMode;
         if (mode > 0) {
            cir.setReturnValue(mode == 3 ? 1.0F : 0.0F);
         }
      }
   }

   @Inject(
      method = {"getTimeOfDay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$timeOfDay(CallbackInfoReturnable<Long> cir) {
      World self = (World)(Object)this;
      if (self.isClient && ClientConfig.timeOverride) {
         cir.setReturnValue((long)ClientConfig.timeTicks);
      }
   }

   @Inject(
      method = {"getSkyAngle"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$skyAngle(float tickDelta, CallbackInfoReturnable<Float> cir) {
      World self = (World)(Object)this;
      if (self.isClient && ClientConfig.timeOverride) {
         cir.setReturnValue(self.dimension.getSkyAngle((long)ClientConfig.timeTicks, 0.0F));
      }
   }

   @Inject(
      method = {"method_3631"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$skyColor(Entity entity, float delta, CallbackInfoReturnable<Vec3d> cir) {
      World self = (World)(Object)this;
      if (self.isClient && ClientConfig.fogModify) {
         int c = ClientConfig.fogColor;
         cir.setReturnValue(new Vec3d((double)(c >> 16 & 0xFF) / 255.0, (double)(c >> 8 & 0xFF) / 255.0, (double)(c & 0xFF) / 255.0));
      }
   }

   @Inject(
      method = {"getFogColor"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void client$fogColor(float delta, CallbackInfoReturnable<Vec3d> cir) {
      World self = (World)(Object)this;
      if (self.isClient && ClientConfig.fogModify) {
         int c = ClientConfig.fogColor;
         cir.setReturnValue(new Vec3d((double)(c >> 16 & 0xFF) / 255.0, (double)(c >> 8 & 0xFF) / 255.0, (double)(c & 0xFF) / 255.0));
      }
   }
}
