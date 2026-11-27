package net.amethystesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Amethyst Client（模组 id 是 client）的拓展包入口。
 *
 * <p>没装 Amethyst Client 就不加载（fabric.mod.json 的 depends 已经拦住），这里再检查一次并直接退出，
 * 避免被强行加载时去碰客户端的类。
 */
public class AmethystEspAddon implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("AmethystESP");

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("client")) {
            LOGGER.error("[AmethystESP] Amethyst Client not found, addon disabled");
            return;
        }

        EspConfig.load(FabricLoader.getInstance().getGameDir().toFile());

        String clientVersion = "?";
        try {
            clientVersion = FabricLoader.getInstance().getModContainer("client")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("?");
        } catch (Throwable ignored) {
            // 只是日志，读不到就算了
        }

        LOGGER.info("[AmethystESP] addon loaded for client {} (entities={}, blocks={}, ores={}, "
                        + "beds={}, obsidian={}, antiInvis={})",
                clientVersion, EspConfig.entities, EspConfig.blocks, EspConfig.ores,
                EspConfig.beds, EspConfig.showObsidian, EspConfig.antiInvis);

        LOGGER.info("[AmethystESP] addon loaded (entities={}, blocks={}, ores={}, beds={}, antiInvis={})",
                EspConfig.entities, EspConfig.blocks, EspConfig.ores, EspConfig.beds, EspConfig.antiInvis);
    }
}
