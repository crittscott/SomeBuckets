package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.BucketState;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.Constant;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The sole source of Mob Bucket overlay colors. An override table
 * ({@code assets/somebuckets/mob_egg_colors.json}, merged across resource packs) supplies colors for
 * entities whose spawn eggs have no usable ones; every other type uses the two
 * {@code minecraft:constant} tints of its spawn egg's client item definition. Both are read from the
 * active resource packs by {@link #load} in a client reload listener's background preparation and
 * installed by {@link #install}, so rendering only looks them up.
 * {@link Tint} applies them to the Mob Bucket model; {@code /sb eggs} reports them.
 */
@Environment(EnvType.CLIENT)
public final class MobEggColors {
    private static final ResourceLocation OVERRIDES_FILE = SomeBuckets.id("mob_egg_colors.json");
    private static final FileToIdConverter ITEM_DEFINITIONS = FileToIdConverter.json("items");
    private static final int MISSING_COLOR = 0xFF808080;
    private static final Codec<Integer> RGB_CODEC =
            TextColor.CODEC.xmap(TextColor::getValue, TextColor::fromRgb);
    private static final Codec<Colors> COLORS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RGB_CODEC.fieldOf("primary").forGetter(Colors::primary),
            RGB_CODEC.fieldOf("secondary").forGetter(Colors::secondary)
    ).apply(instance, Colors::new));
    private static final Codec<Map<ResourceLocation, Colors>> OVERRIDES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, COLORS_CODEC).fieldOf("overrides").codec();

    private static volatile Loaded loaded = new Loaded(Map.of(), Map.of());

    private MobEggColors() {}

    /**
     * Resolves the primary and secondary overlay colors for a captured entity type.
     *
     * @return {@code {primaryARGB, secondaryARGB}} from the override table if present, otherwise from
     *         the spawn egg's item definition, otherwise {@code null}; the cached array itself, which
     *         must not be modified
     */
    @Nullable
    private static int[] resolve(EntityType<?> type) {
        Loaded current = loaded;
        int[] override = current.overrides().get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (override != null) return override;
        SpawnEggItem egg = SpawnEggItem.byId(type);
        return egg == null ? null : current.eggColors().get(egg);
    }

    /**
     * The override colors for {@code entityId}.
     *
     * @return {@code {primaryARGB, secondaryARGB}}, or {@code null} when the table has no entry
     */
    @Nullable
    public static int[] override(ResourceLocation entityId) {
        int[] override = loaded.overrides().get(entityId);
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
        int[] colors = loaded.eggColors().get(egg);
        return colors == null ? null : colors.clone();
    }

    /**
     * Reads the override table from every resource pack, later packs replacing earlier packs'
     * entries, and every spawn egg's item-definition colors. Safe off the game thread.
     */
    static Loaded load(ResourceManager resourceManager) {
        Map<ResourceLocation, int[]> overrides = new LinkedHashMap<>();
        for (Resource resource : resourceManager.getResourceStack(OVERRIDES_FILE)) {
            readOverrides(resource, overrides);
        }
        Map<Item, int[]> eggColors = new HashMap<>();
        for (SpawnEggItem egg : SpawnEggItem.eggs()) {
            readEggColors(resourceManager, egg).ifPresent(colors -> eggColors.put(egg, colors));
        }
        return new Loaded(Collections.unmodifiableMap(overrides), Map.copyOf(eggColors));
    }

    /** Makes colors from {@link #load} current. Call on the game thread. */
    static void install(Loaded colors) {
        loaded = colors;
        SomeBuckets.LOGGER.info("Mob egg color overrides loaded: {}", colors.overrides().size());
    }

    private static Optional<int[]> readEggColors(ResourceManager resourceManager, Item egg) {
        ResourceLocation model = egg.components().get(DataComponents.ITEM_MODEL);
        if (model == null) return Optional.empty();
        ResourceLocation file = ITEM_DEFINITIONS.idToFile(model);
        Optional<Resource> resource = resourceManager.getResource(file);
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
        return Optional.of(new int[] {ARGB.opaque(primary.value()), ARGB.opaque(secondary.value())});
    }

    private static void readOverrides(Resource resource, Map<ResourceLocation, int[]> parsed) {
        Map<ResourceLocation, Colors> table;
        try (Reader reader = resource.openAsReader()) {
            table = OVERRIDES_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(IllegalArgumentException::new);
        } catch (IOException | RuntimeException exception) {
            SomeBuckets.LOGGER.warn("Ignoring unreadable {} from pack {}", OVERRIDES_FILE,
                    resource.sourcePackId(), exception);
            return;
        }
        table.forEach((id, colors) -> parsed.put(id,
                new int[] {ARGB.opaque(colors.primary()), ARGB.opaque(colors.secondary())}));
    }

    private record Colors(int primary, int secondary) {}

    /** Override and spawn-egg colors read from one set of resource packs. */
    record Loaded(Map<ResourceLocation, int[]> overrides, Map<Item, int[]> eggColors) {}

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
