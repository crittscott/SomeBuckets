package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shipped table of Mob Bucket overlay colors for entities whose spawn eggs do not advertise usable
 * ones. Loaded once from {@code /somebuckets/mob_egg_colors.json} in the mod jar. Consulted before the
 * loader spawn-egg lookup by every Mob Bucket tint handler and by {@code /sb eggs}; the three loader
 * tint handlers must not read {@link SpawnEggItem#getColor} directly.
 */
public final class MobEggColors {
    private static final String MANIFEST_PATH = "/somebuckets/mob_egg_colors.json";

    private static final Map<ResourceLocation, int[]> OVERRIDES = load();

    private MobEggColors() {}

    /**
     * Resolves the primary and secondary overlay colors for a captured entity type.
     *
     * @param type the captured entity type
     * @param egg  the loader-resolved spawn egg for that type, or {@code null} when it has none
     * @return {@code {primaryARGB, secondaryARGB}} from the override table if present, otherwise from
     *         {@code egg}, otherwise {@code null} so the caller can substitute its own fallback
     */
    @Nullable
    public static int[] resolve(EntityType<?> type, @Nullable SpawnEggItem egg) {
        int[] override = OVERRIDES.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (override != null) return override.clone();
        if (egg == null) return null;
        return new int[] {0xFF000000 | egg.getColor(0), 0xFF000000 | egg.getColor(1)};
    }

    /** Whether the shipped table supplies colors for {@code entityId}. */
    public static boolean hasOverride(ResourceLocation entityId) {
        return OVERRIDES.containsKey(entityId);
    }

    /**
     * The override colors for {@code entityId}.
     *
     * @return {@code {primaryARGB, secondaryARGB}}, or {@code null} when the table has no entry
     */
    @Nullable
    public static int[] override(ResourceLocation entityId) {
        int[] override = OVERRIDES.get(entityId);
        return override == null ? null : override.clone();
    }

    private static Map<ResourceLocation, int[]> load() {
        InputStream input = MobEggColors.class.getResourceAsStream(MANIFEST_PATH);
        if (input == null) {
            SomeBuckets.LOGGER.error("Mob egg color manifest {} is missing from the mod jar", MANIFEST_PATH);
            throw new IllegalStateException("Missing mob egg color manifest");
        }

        JsonObject overrides;
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            overrides = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("overrides");
        } catch (IOException exception) {
            SomeBuckets.LOGGER.error("Could not read mob egg color manifest {}", MANIFEST_PATH, exception);
            throw new IllegalStateException("Unreadable mob egg color manifest", exception);
        }

        Map<ResourceLocation, int[]> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) {
                SomeBuckets.LOGGER.warn("Mob egg color manifest {}: skipping malformed entity id '{}'",
                        MANIFEST_PATH, entry.getKey());
                continue;
            }
            try {
                JsonObject colors = entry.getValue().getAsJsonObject();
                parsed.put(id, new int[] {
                        0xFF000000 | parseRgb(colors.get("primary").getAsString()),
                        0xFF000000 | parseRgb(colors.get("secondary").getAsString())});
            } catch (RuntimeException exception) {
                SomeBuckets.LOGGER.warn("Mob egg color manifest {}: skipping unreadable entry for {}",
                        MANIFEST_PATH, id, exception);
            }
        }
        SomeBuckets.LOGGER.info("Mob egg color manifest {} loaded: {} overrides", MANIFEST_PATH, parsed.size());
        return Collections.unmodifiableMap(parsed);
    }

    private static int parseRgb(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() != 6) throw new NumberFormatException("expected 6 hex digits: " + hex);
        return Integer.parseInt(digits, 16);
    }
}
