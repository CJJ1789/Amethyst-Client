package net.client.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Entity#getFlag 是 protected，回放里的姿态重建需要读到它。 */
@Mixin(Entity.class)
public interface EntityFlagInvoker {
    @Invoker("getFlag")
    boolean client$getFlag(int index);
}
