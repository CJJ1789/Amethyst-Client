package com.amethyst.blur.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import net.minecraft.client.resource.ResourceMetadataProvider;
import net.minecraft.client.resource.ResourcePackMetadata;
import net.minecraft.resource.ResourcePack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.MetadataSerializer;

/**
 * Virtual resource pack that exposes the mod's shader files under the vanilla
 * {@code minecraft:shaders/...} namespace, so {@code GameRenderer.loadShader} can find them.
 */
public class ShaderResourcePack implements ResourcePack {

    private static final Set<String> NAMESPACES = ImmutableSet.of("minecraft");

    private final Map<Identifier, String> loadedData = new HashMap<>();

    private static boolean validPath(Identifier location) {
        return "minecraft".equals(location.getNamespace()) && location.getPath().startsWith("shaders/");
    }

    @Override
    public InputStream open(Identifier id) throws IOException {
        if (!validPath(id)) {
            throw new FileNotFoundException(id.toString());
        }

        String data = loadedData.get(id);
        if (data == null) {
            try (InputStream in = ShaderResourcePack.class.getResourceAsStream("/" + id.getPath())) {
                if (in == null) {
                    throw new FileNotFoundException(id.toString());
                }
                StringBuilder builder = new StringBuilder();
                Scanner scanner = new Scanner(in, "UTF-8");
                try {
                    while (scanner.hasNextLine()) {
                        builder.append(scanner.nextLine()).append('\n');
                    }
                } finally {
                    scanner.close();
                }
                data = builder.toString();
            }
            loadedData.put(id, data);
        }

        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean contains(Identifier id) {
        return validPath(id) && ShaderResourcePack.class.getResource("/" + id.getPath()) != null;
    }

    @Override
    public Set<String> getNamespaces() {
        return NAMESPACES;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ResourceMetadataProvider> T parseMetadata(MetadataSerializer serializer, String key) throws IOException {
        if ("pack".equals(key)) {
            return (T) new ResourcePackMetadata(new LiteralText("Blur shaders"), 3);
        }
        return null;
    }

    @Override
    public BufferedImage getIcon() throws IOException {
        throw new FileNotFoundException("pack.png");
    }

    @Override
    public String getName() {
        return "Blur shaders";
    }
}
