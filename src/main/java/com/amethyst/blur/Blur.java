package com.amethyst.blur;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amethyst.blur.mixin.GameRendererInvoker;
import com.amethyst.blur.mixin.MinecraftClientAccessor;
import com.amethyst.blur.util.ShaderResourcePack;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;

public class Blur implements ClientModInitializer {

    public static final String MOD_ID = "blur";
    public static final String MOD_NAME = "Blur";

    private static final Logger LOGGER = LogManager.getLogger(MOD_NAME);
    private static final Identifier SHADER = new Identifier("shaders/post/fade_in_blur.json");

    private static Blur instance;

    private BlurConfig config;
    private java.nio.file.Path configPath;
    private final ShaderResourcePack pack = new ShaderResourcePack();
    private boolean packAdded;
    private int colorFirst;
    private int colorSecond;
    private long startTime;

    @Override
    public void onInitializeClient() {
        bootstrap();
    }

    public static Blur getInstance() {
        return instance;
    }

    /**
     * Loads the config on first use. Called from the client entrypoint as well as from the
     * mixins, so ordering between Fabric's entrypoints and Minecraft's own init does not matter.
     */
    public static void bootstrap() {
        if (instance != null) {
            return;
        }
        Blur blur = new Blur();
        blur.configPath = FabricLoader.getInstance().getConfigDir().resolve("blur.json");
        blur.config = BlurConfig.load(blur.configPath);
        blur.colorFirst = BlurConfig.parseColor(blur.config.gradientStartColor, 0x75000000);
        blur.colorSecond = BlurConfig.parseColor(blur.config.gradientEndColor, 0x75000000);
        instance = blur;
        LOGGER.info("[Blur] Loaded config (fadeTime={}ms, radius={})", blur.config.fadeTime, blur.config.radius);
    }

    public static void onScreenChange(Screen screen) {
        bootstrap();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.gameRenderer == null) {
            return;
        }

        GameRenderer renderer = client.gameRenderer;
        // 1.0.2_1：半径 0 = 关闭模糊（同时也不叠加那层渐变遮罩），等同原版背景。
        boolean excluded = screen == null || instance.isExcluded(screen) || instance.config.radius <= 0;

        if (!renderer.areShadersSupported() && !excluded) {
            ((GameRendererInvoker) (Object) renderer).blur$loadShader(SHADER);
            instance.startTime = System.currentTimeMillis();
        } else if (renderer.areShadersSupported() && excluded) {
            renderer.disableShader();
        }
    }

    public static void ensureResourcePack(MinecraftClient client) {
        bootstrap();
        if (instance.packAdded) {
            return;
        }
        List<ResourcePack> packs = ((MinecraftClientAccessor) (Object) client).blur$getResourcePacks();
        if (!packs.contains(instance.pack)) {
            packs.add(instance.pack);
        }
        instance.packAdded = true;
    }

    private boolean isExcluded(Screen screen) {
        String runtimeName = screen.getClass().getName();
        MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
        for (String exclusion : config.guiExclusions) {
            if (exclusion == null || exclusion.isEmpty()) {
                continue;
            }
            String name = exclusion.trim();
            // In a production environment Minecraft classes are loaded under their
            // intermediary names (e.g. net.minecraft.class_359), while configs are written
            // with readable Yarn names. Accept whatever the config uses and resolve it to
            // the namespace this game is actually running in.
            if (runtimeName.equals(name)) {
                return true;
            }
            for (String namespace : resolver.getNamespaces()) {
                try {
                    if (runtimeName.equals(resolver.mapClassName(namespace, name))) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // The class does not exist in this namespace, try the next one.
                }
            }
        }
        return false;
    }

    public static float getProgress() {
        Blur blur = instance;
        if (blur == null) {
            return 1.0F;
        }
        int fadeTime = blur.config.fadeTime;
        if (fadeTime <= 0) {
            return 1.0F;
        }
        return Math.min((System.currentTimeMillis() - blur.startTime) / (float) fadeTime, 1.0F);
    }

    /**
     * Mirrors the original mod: the configured ARGB color is faded in by multiplying every
     * channel with the current progress.
     */
    public static int getBackgroundColor(boolean second) {
        Blur blur = instance;
        if (blur == null) {
            return 0;
        }
        int color = second ? blur.colorSecond : blur.colorFirst;
        int a = color >>> 24;
        int r = (color >> 16) & 0xFF;
        int b = (color >> 8) & 0xFF;
        int g = color & 0xFF;
        float prog = getProgress();
        a = (int) (a * prog);
        r = (int) (r * prog);
        g = (int) (g * prog);
        b = (int) (b * prog);
        return a << 24 | r << 16 | b << 8 | g;
    }

    public int getRadius() {
        return config.radius;
    }

    /** 供客户端设置界面用：把模糊半径写回本模组自己的 blur.json。 */
    public static void setRadiusValue(int radius) {
        bootstrap();
        Blur blur = instance;
        if (blur == null) {
            return;
        }
        int clamped = radius < 0 ? 0 : (radius > 100 ? 100 : radius);
        if (blur.config.radius == clamped) {
            return;
        }
        blur.config.radius = clamped;
        blur.saveConfig();
        // 立即按新半径重新判定：0 要当场把后处理关掉，>0 要当场挂回来。
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            onScreenChange(client.currentScreen);
        }
    }

    /** 当前半径，读不到时回落到默认 12。 */
    public static int radiusValue() {
        bootstrap();
        Blur blur = instance;
        return blur == null ? 12 : blur.config.radius;
    }

    /* ------------------------------------------------------------------ */
    /* 1.0.2_1：把原本只能改 json 的几项参数接到客户端设置界面            */
    /* ------------------------------------------------------------------ */

    /** 淡入时长（毫秒）。 */
    public static int fadeTimeValue() {
        bootstrap();
        Blur blur = instance;
        return blur == null ? 200 : blur.config.fadeTime;
    }

    public static void setFadeTimeValue(int millis) {
        bootstrap();
        Blur blur = instance;
        if (blur == null) {
            return;
        }
        int clamped = millis < 0 ? 0 : (millis > 800 ? 800 : millis);
        if (blur.config.fadeTime == clamped) {
            return;
        }
        blur.config.fadeTime = clamped;
        // 改完立刻重算起始时间，否则这次淡入进度会按旧时长算完。
        blur.startTime = System.currentTimeMillis();
        blur.saveConfig();
    }

    /** 渐变遮罩的起始色（ARGB）。 */
    public static int gradientStartValue(boolean second) {
        bootstrap();
        Blur blur = instance;
        if (blur == null) {
            return 0x75000000;
        }
        return second ? blur.colorSecond : blur.colorFirst;
    }

    public static void setGradientColor(boolean second, int argb) {
        bootstrap();
        Blur blur = instance;
        if (blur == null) {
            return;
        }
        // 与原模组一致：写回 8 位十六进制字符串，重新载入后色值不变。
        String hex = String.format("%08X", argb);
        if (second) {
            blur.colorSecond = argb;
            blur.config.gradientEndColor = hex;
        } else {
            blur.colorFirst = argb;
            blur.config.gradientStartColor = hex;
        }
        blur.saveConfig();
    }

    /**
     * 1.0.2_7：「重置所有设置」用。这几项（模糊强度 / 淡入时长 / 两个渐变色 / 模糊聊天）
     * 存在本模组自己的 blur.json 里，不在 ClientConfig 里，所以反射式重置够不着它们。
     * 这里按 BlurConfig 里声明的出厂值写回并落盘。
     */
    public static void resetDefaults() {
        bootstrap();
        Blur blur = instance;
        if (blur == null) {
            return;
        }
        blur.config.radius = 12;
        blur.config.fadeTime = 200;
        blur.config.gradientStartColor = "75000000";
        blur.config.gradientEndColor = "75000000";
        blur.config.guiExclusions = new String[]{"net.minecraft.client.gui.screen.ChatScreen"};
        blur.colorFirst = 0x75000000;
        blur.colorSecond = 0x75000000;
        blur.startTime = System.currentTimeMillis();
        blur.saveConfig();
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null) {
            onScreenChange(client.currentScreen);
        }
    }

    /** 聊天界面是否跟着模糊（原模组默认把 ChatScreen 排除，这里做成开关）。 */
    public static boolean chatBlurValue() {
        bootstrap();
        Blur blur = instance;
        if (blur == null) {
            return false;
        }
        if (blur.config.guiExclusions == null) {
            return true;
        }
        for (String exclusion : blur.config.guiExclusions) {
            if (exclusion != null && exclusion.endsWith("ChatScreen")) {
                return false;
            }
        }
        return true;
    }

    public static void setChatBlur(boolean blurChat) {
        bootstrap();
        Blur blur = instance;
        if (blur == null) {
            return;
        }
        String chat = "net.minecraft.client.gui.screen.ChatScreen";
        java.util.List<String> kept = new java.util.ArrayList<String>();
        if (blur.config.guiExclusions != null) {
            for (String exclusion : blur.config.guiExclusions) {
                boolean isChat = exclusion != null && exclusion.endsWith("ChatScreen");
                if (isChat == blurChat) {
                    continue;
                }
                kept.add(exclusion);
            }
        }
        if (!blurChat) {
            kept.add(chat);
        }
        blur.config.guiExclusions = kept.toArray(new String[0]);
        blur.saveConfig();
    }

    private void saveConfig() {
        if (configPath == null) {
            return;
        }
        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            java.nio.file.Path parent = configPath.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            try (java.io.Writer writer = java.nio.file.Files.newBufferedWriter(configPath, java.nio.charset.StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
        } catch (java.io.IOException e) {
            LOGGER.warn("[Blur] Failed to save config to {}", configPath, e);
        }
    }
}
