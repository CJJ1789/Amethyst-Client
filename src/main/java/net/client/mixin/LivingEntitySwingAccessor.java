package net.client.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({LivingEntity.class})
public interface LivingEntitySwingAccessor {
   @Invoker("getMiningSpeedMultiplier")
   int client$swingDuration();
}
