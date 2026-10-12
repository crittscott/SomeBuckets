package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Chooses the two overlay colors of a Mob Bucket from the stored entity type's spawn egg. The
 * client resource {@code assets/somebuckets/mob_bucket_colors.json} covers mobs that have no spawn
 * egg of their own: each entry maps an entity type ID either to the type whose egg colors it
 * borrows, or to an array of two hex RGB colors (base, then spots). Every resource pack's copy is
 * read, later packs overriding earlier ones entry by entry; entries naming unregistered types are
 * ignored, so entries for absent mods are harmless.
 */
@Environment(EnvType.CLIENT)
final class MobBucketColors {
    private static final ResourceLocation FILE =
            new ResourceLocation(SomeBuckets.MODID, "mob_bucket_colors.json");
    private static final int MISSING_EGG_COLOR = 0x808080;

    /** An entry's color source: a borrowed spawn egg, or explicit colors when {@code egg} is null. */
    private record Source(@Nullable EntityType<?> egg, int base, int spots) {}

    private static volatile Map<EntityType<?>, Source> sources;

    private MobBucketColors() {}

    /**
     * The color of one Mob Bucket overlay.
     *
     * @param type  the stored entity type, or {@code null} when the bucket is empty
     * @param layer 0 for the spawn egg's base color, 1 for its spots
     */
    static int tint(@Nullable EntityType<?> type, int layer) {
        if (type == null) return MISSING_EGG_COLOR;
        Source source = sources().get(type);
        if (source != null && source.egg() == null) return layer == 0 ? source.base() : source.spots();
        SpawnEggItem egg = ClientPlatform.spawnEgg(source != null ? source.egg() : type);
        return egg == null ? MISSING_EGG_COLOR : egg.getColor(layer);
    }

    /**
     * Describes the {@code mob_bucket_colors.json} entry for {@code type}, for {@code /sb eggs}.
     *
     * @return the borrowed type's id, or the two explicit colors, or {@code null} when no entry
     *         covers the type
     */
    @Nullable
    static String describeOverride(EntityType<?> type) {
        Source source = sources().get(type);
        if (source == null) return null;
        if (source.egg() != null) return "borrows " + BuiltInRegistries.ENTITY_TYPE.getKey(source.egg());
        return String.format("base #%06X · spots #%06X", source.base(), source.spots());
    }

    private static Map<EntityType<?>, Source> sources() {
        Map<EntityType<?>, Source> cached = sources;
        if (cached == null) {
            cached = read();
            sources = cached;
        }
        return cached;
    }

    static void clearCache() {
        sources = null;
    }

    private static Map<EntityType<?>, Source> read() {
        Map<EntityType<?>, Source> out = new HashMap<>();
        for (Resource resource : Minecraft.getInstance().getResourceManager().getResourceStack(FILE)) {
            Map<EntityType<?>, Source> pack = new HashMap<>();
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    Optional<EntityType<?>> from = entityType(entry.getKey());
                    if (from.isEmpty()) continue;
                    Source source = source(entry.getKey(), entry.getValue());
                    if (source != null) pack.put(from.get(), source);
                }
            } catch (IOException | RuntimeException e) {
                SomeBuckets.LOGGER.warn("Ignoring unreadable {} from pack {}: {}",
                        FILE, resource.sourcePackId(), e.getMessage());
                continue;
            }
            out.putAll(pack);
        }
        return Map.copyOf(out);
    }

    /** Parses an entry value, or returns {@code null} when it borrows an unregistered type. */
    @Nullable
    private static Source source(String key, JsonElement value) {
        if (value.isJsonArray()) {
            JsonArray colors = value.getAsJsonArray();
            if (colors.size() != 2) throw new JsonParseException(key + " must list exactly two colors");
            return new Source(null, hexColor(key, colors.get(0)), hexColor(key, colors.get(1)));
        }
        return entityType(GsonHelper.convertToString(value, key))
                .map(egg -> new Source(egg, 0, 0))
                .orElse(null);
    }

    private static int hexColor(String key, JsonElement element) {
        return Integer.parseInt(GsonHelper.convertToString(element, key), 16) & 0xFFFFFF;
    }

    private static Optional<EntityType<?>> entityType(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(location);
    }
}
