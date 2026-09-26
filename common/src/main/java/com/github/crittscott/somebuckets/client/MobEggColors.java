package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.BucketState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.Constant;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sole source of Mob Bucket overlay colors. A shipped table ({@code /somebuckets/mob_egg_colors.json}
 * in the mod jar) supplies colors for entities whose spawn eggs have no usable ones; every other type
 * uses the two {@code minecraft:constant} tints of its spawn egg's client item definition. Egg colors
 * are read from the active resource packs, cached, and cleared by {@link #clearCache()} on reload.
 * {@link Tint} applies them to the Mob Bucket model; {@code /sb eggs} reports them.
 */
@Environment(EnvType.CLIENT)
public final class MobEggColors {
    private static final String MANIFEST_PATH = "/somebuckets/mob_egg_colors.json";
    private static final int MISSING_COLOR = 0xFF808080;

    private static final Map<ResourceLocation, int[]> OVERRIDES = load();
    private static final Map<Item, Optional<int[]>> EGG_COLORS = new ConcurrentHashMap<>();

    private MobEggColors() {}

    /**
     * Resolves the primary and secondary overlay colors for a captured entity type.
     *
     * @return {@code {primaryARGB, secondaryARGB}} from the override table if present, otherwise from
     *         the spawn egg's item definition, otherwise {@code null}
     */
    @Nullable
    public static int[] resolve(EntityType<?> type) {
        int[] override = override(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (override != null) return override;
        SpawnEggItem egg = SpawnEggItem.byId(type);
        return egg == null ? null : eggColors(egg);
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

    /**
     * The two constant tints of {@code egg}'s client item definition.
     *
     * @return {@code {primaryARGB, secondaryARGB}}, or {@code null} when the definition is missing,
     *         unreadable, or does not tint a single model with two constants
     */
    @Nullable
    public static int[] eggColors(Item egg) {
        return EGG_COLORS.computeIfAbsent(egg, MobEggColors::readEggColors).map(int[]::clone).orElse(null);
    }

    /** Discards cached egg colors. Call on every client resource reload. */
    public static void clearCache() {
        EGG_COLORS.clear();
    }

    private static Optional<int[]> readEggColors(Item egg) {
        ResourceLocation model = egg.components().get(DataComponents.ITEM_MODEL);
        if (model == null) return Optional.empty();
        ResourceLocation file = model.withPath(path -> "items/" + path + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return Optional.empty();

        ClientItem item;
        try (Reader reader = resource.get().openAsReader()) {
            item = ClientItem.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .result().orElse(null);
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
        if (item == null || !(item.model() instanceof BlockModelWrapper.Unbaked wrapper)) {
            return Optional.empty();
        }
        List<ItemTintSource> tints = wrapper.tints();
        if (tints.size() < 2 || !(tints.get(0) instanceof Constant primary)
                || !(tints.get(1) instanceof Constant secondary)) {
            return Optional.empty();
        }
        return Optional.of(new int[] {0xFF000000 | primary.value(), 0xFF000000 | secondary.value()});
    }

    /* The manifest ships in the mod jar, so any defect is a packaging error and fails class loading. */
    private static Map<ResourceLocation, int[]> load() {
        InputStream input = MobEggColors.class.getResourceAsStream(MANIFEST_PATH);
        if (input == null) {
            throw new IllegalStateException("Mob egg color manifest " + MANIFEST_PATH + " is missing from the mod jar");
        }

        JsonObject overrides;
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            overrides = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("overrides");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unreadable mob egg color manifest " + MANIFEST_PATH, exception);
        }
        if (overrides == null) {
            throw new IllegalStateException("Mob egg color manifest " + MANIFEST_PATH + " has no overrides object");
        }

        Map<ResourceLocation, int[]> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
            try {
                JsonObject colors = entry.getValue().getAsJsonObject();
                parsed.put(ResourceLocation.parse(entry.getKey()), new int[] {
                        0xFF000000 | parseRgb(colors.get("primary").getAsString()),
                        0xFF000000 | parseRgb(colors.get("secondary").getAsString())});
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Malformed entry '" + entry.getKey()
                        + "' in mob egg color manifest " + MANIFEST_PATH + ": " + entry.getValue(), exception);
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

    /**
     * Mob Bucket overlay tint, registered as {@code somebuckets:mob_egg}:
     * {@code {"type": "somebuckets:mob_egg", "index": 0|1}} selects the primary or secondary color of
     * the captured type. Gray when the bucket is empty or the type has no resolvable colors.
     */
    public record Tint(int index) implements ItemTintSource {
        public static final MapCodec<Tint> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(0, 1).fieldOf("index").forGetter(Tint::index)
        ).apply(instance, Tint::new));

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
            EntityType<?> type = BucketState.getCurrentEntityType(stack);
            int[] colors = type == null ? null : resolve(type);
            return colors == null ? MISSING_COLOR : colors[index];
        }

        @Override
        public MapCodec<Tint> type() {
            return MAP_CODEC;
        }
    }
}
