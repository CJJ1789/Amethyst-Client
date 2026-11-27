package net.client.mixin;

import java.util.Set;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientWorld.class})
public interface ClientWorldAccessor {
   @Accessor("entitiesForSpawn")
   Set<Entity> client$entities();

   @Accessor("world")
   Set<Entity> client$worldEntities();
}
