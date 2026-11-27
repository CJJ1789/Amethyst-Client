package net.client.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.render.GameRenderer.class)
public interface ReplayGameRendererAccessor {
    @Accessor("renderHand")
    boolean client$getRenderHand();

    @Accessor("renderHand")
    void client$setRenderHand(boolean value);
}
