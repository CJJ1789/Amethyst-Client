package net.client.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({LivingEntityRenderer.class})
public interface LivingEntityRendererScaleAccessor {
   @Invoker("scale")
   void client$scale(LivingEntity var1, float var2);
}
