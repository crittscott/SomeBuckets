package com.github.crittscott.somebuckets.config;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

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

    /**
     * Reads the global allowlist, creating a default file when absent, and refreshes {@link SBPolicy}.
     * When the file cannot supply a list, the default allowlist is used and the logged source says
     * so.
     *
     * @param reload whether this read was triggered by a data-pack reload
     */
    public static void load(boolean reload) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        List<String> configured = new ArrayList<>(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS);
        String source = FILE_NAME;
        if (Files.notExists(path)) {
            if (!writeDefaults(path)) source = "defaults (could not create " + FILE_NAME + ")";
        } else {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                JsonElement list = root == null ? null : root.get(SBPolicy.ALLOWED_CONTENTS_KEY);
                if (list != null && list.isJsonArray()) {
                    configured.clear();
                    for (JsonElement value : list.getAsJsonArray()) {
                        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                                && SBPolicy.isValidContentId(value.getAsString())) {
                            configured.add(value.getAsString());
                        } else {
                            SomeBuckets.LOGGER.warn("Ignoring invalid Source Bucket config entry {} in {}",
                                    value, path);
                        }
                    }
                } else {
                    SomeBuckets.LOGGER.warn("{} has no {} list; using the default Source Bucket allowlist",
                            path, SBPolicy.ALLOWED_CONTENTS_KEY);
                    source = "defaults (" + FILE_NAME + " has no " + SBPolicy.ALLOWED_CONTENTS_KEY + ")";
                }
            } catch (IOException | JsonParseException exception) {
                SomeBuckets.LOGGER.error("Could not read {}; using the default Source Bucket allowlist",
                        path, exception);
                configured = new ArrayList<>(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS);
                source = "defaults (" + FILE_NAME + " unreadable)";
            }
        }
        SBPolicy.refresh(configured, source, reload);
    }

    /** Writes the default allowlist to {@code path}, returning whether the file was written. */
    private static boolean writeDefaults(Path path) {
        JsonObject root = new JsonObject();
        JsonArray values = new JsonArray();
        SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS.forEach(values::add);
        root.add(SBPolicy.ALLOWED_CONTENTS_KEY, values);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
            return true;
        } catch (IOException exception) {
            SomeBuckets.LOGGER.error("Could not create default config {}; using the default Source Bucket allowlist",
                    path, exception);
            return false;
        }
    }
}
