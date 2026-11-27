package com.amethyst.blur.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.DrawableHelper;

@Mixin(DrawableHelper.class)
public interface DrawableHelperInvoker {

    @Invoker("fillGradient")
    void blur$fillGradient(int x1, int y1, int x2, int y2, int color1, int color2);
}
