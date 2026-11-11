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
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The sole source of Mob Bucket overlay colors. An override table
 * ({@code assets/somebuckets/mob_egg_colors.json}, merged across resource packs) supplies colors for
 * entities whose spawn eggs have no usable ones; every other type uses the two
 * {@code minecraft:constant} tints of its spawn egg's client item definition. Both are read from the
 * active resource packs. The reload listener prepares overrides and conventional egg definitions.
 * Once item components are bound, custom model ids are resolved and cached on first use.
 * {@link Tint} applies them to the Mob Bucket model; {@code /sb eggs} reports them.
 */
@Environment(EnvType.CLIENT)
public final class MobEggColors {
    private static final Identifier OVERRIDES_FILE = SomeBuckets.id("mob_egg_colors.json");
    private static final FileToIdConverter ITEM_DEFINITIONS = FileToIdConverter.json("items");
    private static final int MISSING_COLOR = 0xFF808080;
    private static final Codec<Integer> RGB_CODEC =
            TextColor.CODEC.xmap(TextColor::getValue, TextColor::fromRgb);
    private static final Codec<Colors> COLORS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RGB_CODEC.fieldOf("primary").forGetter(Colors::primary),
            RGB_CODEC.fieldOf("secondary").forGetter(Colors::secondary)
    ).apply(instance, Colors::new));
    private static final Codec<Map<Identifier, Colors>> OVERRIDES_CODEC =
            Codec.unboundedMap(Identifier.CODEC, COLORS_CODEC).fieldOf("overrides").codec();

    private static volatile Loaded loaded;

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
        if (current == null) return null;
        int[] override = current.overrides().get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (override != null) return override;
        return SpawnEggItem.byId(type).map(egg -> eggColors(current, egg)).orElse(null);
    }

    /**
     * The override colors for {@code entityId}.
     *
     * @return {@code {primaryARGB, secondaryARGB}}, or {@code null} when the table has no entry
     */
    @Nullable
    public static int[] override(Identifier entityId) {
        Loaded current = loaded;
        int[] override = current == null ? null : current.overrides().get(entityId);
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
        Loaded current = loaded;
        int[] colors = current == null ? null : eggColors(current, BuiltInRegistries.ITEM.wrapAsHolder(egg));
        return colors == null ? null : colors.clone();
    }

    /**
     * Reads the override table from every resource pack, later packs replacing earlier packs'
     * entries, and every spawn egg's item-definition colors. Safe off the game thread.
     */
    static Loaded load(ResourceManager resourceManager) {
        Map<Identifier, int[]> overrides = new LinkedHashMap<>();
        for (Resource resource : resourceManager.getResourceStack(OVERRIDES_FILE)) {
            readOverrides(resource, overrides);
        }
        ConcurrentMap<Identifier, Optional<int[]>> eggColors = new ConcurrentHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SpawnEggItem) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                eggColors.put(id, readEggColors(resourceManager, id));
            }
        }
        return new Loaded(Map.copyOf(overrides), resourceManager, eggColors);
    }

    /** Makes colors from {@link #load} current. Call on the game thread. */
    static void install(Loaded colors) {
        loaded = colors;
        SomeBuckets.LOGGER.info("Mob egg color overrides loaded: {}", colors.overrides().size());
    }

    @Nullable
    private static int[] eggColors(Loaded current, Holder<Item> egg) {
        if (!egg.areComponentsBound()) return null;
        Identifier model = egg.components().get(DataComponents.ITEM_MODEL);
        if (model == null) return null;
        return current.eggColors().computeIfAbsent(model, id -> readEggColors(current.resources(), id)).orElse(null);
    }

    private static Optional<int[]> readEggColors(ResourceManager resourceManager, Identifier model) {
        Identifier file = ITEM_DEFINITIONS.idToFile(model);
        Optional<Resource> resource = resourceManager.getResource(file);
        if (resource.isEmpty()) return Optional.empty();

        ClientItem item;
        try (Reader reader = resource.get().openAsReader()) {
            item = ClientItem.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .result().orElse(null);
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
        if (item == null || !(item.model() instanceof CuboidItemModelWrapper.Unbaked wrapper)) {
            return Optional.empty();
        }
        List<ItemTintSource> tints = wrapper.tints();
        if (tints.size() < 2 || !(tints.get(0) instanceof Constant primary)
                || !(tints.get(1) instanceof Constant secondary)) {
            return Optional.empty();
        }
        return Optional.of(new int[] {ARGB.opaque(primary.value()), ARGB.opaque(secondary.value())});
    }

    private static void readOverrides(Resource resource, Map<Identifier, int[]> parsed) {
        Map<Identifier, Colors> table;
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
    record Loaded(Map<Identifier, int[]> overrides, ResourceManager resources,
                  ConcurrentMap<Identifier, Optional<int[]>> eggColors) {}

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
