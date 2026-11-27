package net.client.ui.md3.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/** Material Icons glyph atlas. */
public final class Md3Icons {
    private static int texture = -1;
    private static float atlasSize = 512f;
    private static float cell = 64f;
    private static final Map<String, float[]> UV = new HashMap<String, float[]>();
    /** Visual centre of each 64px cell, as a 0..1 fraction of the cell. */
    private static final Map<String, float[]> OPTICAL = new HashMap<String, float[]>();

    private Md3Icons() {
    }

    private static void ensure() {
        if (texture > 0) return;
        try (InputStreamReader reader = new InputStreamReader(
                Md3Icons.class.getResourceAsStream("/assets/md3/icon/material_icons.json"), "UTF-8")) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            atlasSize = root.get("atlas").getAsFloat();
            cell = root.get("cell").getAsFloat();
            JsonObject icons = root.getAsJsonObject("icons");
            BufferedImage image = Md3Gl.loadPng("/assets/md3/icon/material_icons.png");
            for (Map.Entry<String, com.google.gson.JsonElement> e : icons.entrySet()) {
                com.google.gson.JsonArray xy = e.getValue().getAsJsonArray();
                float x = xy.get(0).getAsFloat();
                float y = xy.get(1).getAsFloat();
                UV.put(e.getKey(), new float[]{x / atlasSize, y / atlasSize,
                        (x + cell) / atlasSize, (y + cell) / atlasSize});
                OPTICAL.put(e.getKey(), opticalCenter(image, (int) x, (int) y, (int) cell));
            }
            texture = Md3Gl.upload(image, true);
        } catch (Exception e) {
            System.err.println("[MD3] icon atlas failed: " + e);
        }
    }

    public static void draw(Md3Canvas canvas, String name, float x, float y, float size, int color) {
        ensure();
        if (texture <= 0) return;
        float[] uv = UV.get(name);
        if (uv == null) return;
        float[] centre = OPTICAL.get(name);
        float drawX = x;
        float drawY = y;
        if (centre != null) {
            // Material's glyphs sit above the middle of their 64px cell (the
            // icon font baseline).  Shift the whole cell so the visible ink,
            // rather than the transparent box, lands in the button's centre.
            drawX = x + size * (0.5f - centre[0]);
            drawY = y + size * (0.5f - centre[1]);
        }
        canvas.image(texture, drawX, drawY, size, size, uv[0], uv[1], uv[2], uv[3], color);
    }

    private static float[] opticalCenter(BufferedImage image, int x0, int y0, int cell) {
        if (image == null) {
            return new float[]{0.5f, 0.5f};
        }
        int minX = cell;
        int minY = cell;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
                int px = x0 + x;
                int py = y0 + y;
                if (px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
                    continue;
                }
                int argb = image.getRGB(px, py);
                if ((argb >>> 24) < 10) {
                    continue;
                }
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) {
            return new float[]{0.5f, 0.5f};
        }
        return new float[]{(minX + maxX + 1) / (2f * cell),
                (minY + maxY + 1) / (2f * cell)};
    }

    public static boolean has(String name) {
        ensure();
        return UV.containsKey(name);
    }
}
