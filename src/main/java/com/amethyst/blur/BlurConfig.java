package com.amethyst.blur;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Plain JSON replacement for Forge's Configuration. The keys are kept identical to the
 * original mod so existing configs can be migrated by hand.
 */
public class BlurConfig {

    private static final Logger LOGGER = LogManager.getLogger("Blur");

    public String[] guiExclusions = { "net.minecraft.client.gui.screen.ChatScreen" };
    public int fadeTime = 200;
    public int radius = 12;
    public String gradientStartColor = "75000000";
    public String gradientEndColor = "75000000";

    public static BlurConfig load(Path path) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        BlurConfig config = null;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = gson.fromJson(reader, BlurConfig.class);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("[Blur] Failed to read config, using defaults", e);
            }
        }

        if (config == null) {
            config = new BlurConfig();
        }

        config.validate();

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[Blur] Failed to save config to {}", path, e);
        }

        return config;
    }

    private void validate() {
        if (guiExclusions == null) {
            guiExclusions = new String[0];
        }
        if (fadeTime < 0) {
            fadeTime = 0;
        }
        // 1.0.2_1：0 表示关闭模糊（原版往上限是 100）。
        if (radius < 0) {
            radius = 0;
        } else if (radius > 100) {
            radius = 100;
        }
        if (gradientStartColor == null) {
            gradientStartColor = "75000000";
        }
        if (gradientEndColor == null) {
            gradientEndColor = "75000000";
        }
    }

    public static int parseColor(String hex, int fallback) {
        try {
            return Integer.parseUnsignedInt(hex.trim(), 16);
        } catch (RuntimeException e) {
            LOGGER.warn("[Blur] Invalid color '{}', using default", hex);
            return fallback;
        }
    }
}
