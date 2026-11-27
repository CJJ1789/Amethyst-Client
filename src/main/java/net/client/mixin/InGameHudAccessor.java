package net.client.mixin;

import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the F3 overlay so it can be repainted above the blurred menu backdrop. */
@Mixin(InGameHud.class)
public interface InGameHudAccessor {
   @Accessor("debugHud")
   DebugHud client$debugHud();
}
