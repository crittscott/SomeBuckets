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
    public static final ResourceLocation MILK_ID = new ResourceLocation(SomeBuckets.MODID, "milk");
    private static final String FILE_NAME = "somebuckets-server.json";
    private static final List<String> DEFAULTS = List.of(
            "minecraft:water", "minecraft:lava", MILK_ID.toString());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FabricServerConfig() {}

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        List<String> configured = new ArrayList<>(DEFAULTS);
        if (Files.notExists(path)) {
            writeDefaults(path);
        } else {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                JsonArray values = root == null ? null : root.getAsJsonArray("allowedContents");
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
                SomeBuckets.LOGGER.error("Could not read {}; using defaults", path, exception);
                configured = new ArrayList<>(DEFAULTS);
            }
        }
        SBPolicy.refresh(configured, MILK_ID, FILE_NAME);
    }

    private static void writeDefaults(Path path) {
        JsonObject root = new JsonObject();
        JsonArray values = new JsonArray();
        DEFAULTS.forEach(values::add);
        root.add("allowedContents", values);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            SomeBuckets.LOGGER.error("Could not create default config {}", path, exception);
        }
    }
}
