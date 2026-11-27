package net.client.mixin;

import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({Screen.class})
public interface ScreenAccessor {
   @Accessor("width")
   int client$width();

   @Accessor("height")
   int client$height();

   @Accessor("buttons")
   List<ButtonWidget> client$buttons();
}
