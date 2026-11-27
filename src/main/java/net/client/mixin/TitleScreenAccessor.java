package net.client.mixin;

import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({TitleScreen.class})
public interface TitleScreenAccessor {
   @Accessor("realmsButton")
   ButtonWidget client$realmsButton();
}
