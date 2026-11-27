// Upstream: FastPacks / McPacks 1.0 (Forge 1.8.9), MIT
//   https://modrinth.com/mod/fastpacks (author: McPacks Team; Modrinth owner: Dulandor)
// Ported and modified for Legacy Fabric by this project; upstream MIT notice retained
// (see licenses/FASTPACKS-MIT.txt). The port was never released standalone.

package com.mcpacks;

import com.mcpacks.core.PackManager;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

public class McPacksMod implements ClientModInitializer {
   public static final String MODID = "mcpacks";
   public static final String NAME = "McPacks";
   public static final String VERSION = "1.0.0";

   public void onInitializeClient() {
      PackManager.init(MinecraftClient.getInstance());
   }
}
