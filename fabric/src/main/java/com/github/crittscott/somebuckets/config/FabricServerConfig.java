package com.github.crittscott.somebuckets.config;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small dependency-free Fabric server config for the Source Bucket allowlist. */
public final class FabricServerConfig {
    private static final String FILE_NAME = "somebuckets-server.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FabricServerConfig() {}

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        List<String> configured = new ArrayList<>(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS);
        if (Files.notExists(path)) {
            writeDefaults(path);
        } else {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                JsonArray values = root == null ? null : root.getAsJsonArray(SBPolicy.ALLOWED_CONTENTS_KEY);
                if (values != null) {
                    configured.clear();
                    for (JsonElement value : values) {
                        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                                && ResourceLocation.tryParse(value.getAsString()) != null) {
                            configured.add(value.getAsString());
                        } else {
                            SomeBuckets.LOGGER.warn("Ignoring invalid Source Bucket config entry in {}", path);
                        }
                    }
                }
            } catch (IOException | RuntimeException exception) {
                SomeBuckets.LOGGER.warn("Could not read {}; using defaults", path, exception);
                configured = new ArrayList<>(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS);
            }
        }
        SBPolicy.refresh(configured, FILE_NAME);
    }

    private static void writeDefaults(Path path) {
        JsonObject root = new JsonObject();
        JsonArray values = new JsonArray();
        SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS.forEach(values::add);
        root.add(SBPolicy.ALLOWED_CONTENTS_KEY, values);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            SomeBuckets.LOGGER.warn("Could not create default config {}", path, exception);
        }
    }
}
