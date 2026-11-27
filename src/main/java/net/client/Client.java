package net.client;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.client.settings.SettingsSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Client implements ClientModInitializer {
   public static final String MOD_ID = "client";
   public static final Logger LOGGER = LogManager.getLogger("Client");

   public void onInitializeClient() {
      ClientConfig.ensureLoaded();
      List<String> specProblems = SettingsSpec.validate();
      if (!specProblems.isEmpty()) {
         LOGGER.error("[Client] 设置表自检失败: {}", new Object[]{specProblems});
      }

      LOGGER.info("[Client] initialised, press Right Shift to open the menu");
   }
}
