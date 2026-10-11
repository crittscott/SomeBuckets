package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Chooses which entity type's spawn egg tints a Mob Bucket. The client resource
 * {@code assets/somebuckets/mob_bucket_colors.json} maps entity type IDs to the type whose egg
 * colors they borrow, for mobs that have no spawn egg of their own. Every resource pack's copy is
 * read, later packs overriding earlier ones entry by entry; entries naming unregistered types are
 * ignored, so entries for absent mods are harmless.
 */
@Environment(EnvType.CLIENT)
final class MobBucketColors {
    private static final ResourceLocation FILE =
            new ResourceLocation(SomeBuckets.MODID, "mob_bucket_colors.json");

    private static volatile Map<EntityType<?>, EntityType<?>> borrowed;

    private MobBucketColors() {}

    /** The entity type whose spawn egg colors a Mob Bucket holding {@code type} uses. */
    static EntityType<?> eggSource(EntityType<?> type) {
        Map<EntityType<?>, EntityType<?>> cached = borrowed;
        if (cached == null) {
            cached = read();
            borrowed = cached;
        }
        return cached.getOrDefault(type, type);
    }

    static void clearCache() {
        borrowed = null;
    }

    private static Map<EntityType<?>, EntityType<?>> read() {
        Map<EntityType<?>, EntityType<?>> out = new HashMap<>();
        for (Resource resource : Minecraft.getInstance().getResourceManager().getResourceStack(FILE)) {
            Map<EntityType<?>, EntityType<?>> pack = new HashMap<>();
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    String target = GsonHelper.convertToString(entry.getValue(), entry.getKey());
                    Optional<EntityType<?>> from = entityType(entry.getKey());
                    Optional<EntityType<?>> to = entityType(target);
                    if (from.isPresent() && to.isPresent()) pack.put(from.get(), to.get());
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

    private static Optional<EntityType<?>> entityType(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(location);
    }
}
