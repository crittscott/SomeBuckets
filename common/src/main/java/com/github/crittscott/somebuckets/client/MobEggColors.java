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
import net.minecraft.client.Minecraft;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The sole source of Mob Bucket overlay colors. An override table
 * ({@code assets/somebuckets/mob_egg_colors.json}, merged across resource packs) supplies colors for
 * entities whose spawn eggs have no usable ones; every other type uses the two
 * {@code minecraft:constant} tints of its spawn egg's client item definition. Both are read from the
 * active resource packs; {@link #reload} refreshes them on every client resource reload.
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

    private static final Map<Item, Optional<int[]>> EGG_COLORS = new ConcurrentHashMap<>();
    private static volatile Map<ResourceLocation, int[]> overrides = Map.of();

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
        int[] override = overrides.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (override != null) return override;
        SpawnEggItem egg = SpawnEggItem.byId(type);
        return egg == null ? null : EGG_COLORS.computeIfAbsent(egg, MobEggColors::readEggColors).orElse(null);
    }

    /**
     * The override colors for {@code entityId}.
     *
     * @return {@code {primaryARGB, secondaryARGB}}, or {@code null} when the table has no entry
     */
    @Nullable
    public static int[] override(ResourceLocation entityId) {
        int[] override = overrides.get(entityId);
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

    /**
     * Reloads the override table from every resource pack, later packs replacing earlier packs'
     * entries, and discards cached egg colors. Call on every client resource reload.
     */
    public static void reload(ResourceManager resourceManager) {
        Map<ResourceLocation, int[]> parsed = new LinkedHashMap<>();
        for (Resource resource : resourceManager.getResourceStack(OVERRIDES_FILE)) {
            readOverrides(resource, parsed);
        }
        overrides = Collections.unmodifiableMap(parsed);
        EGG_COLORS.clear();
        SomeBuckets.LOGGER.info("Mob egg color overrides loaded: {}", parsed.size());
    }

    private static Optional<int[]> readEggColors(Item egg) {
        ResourceLocation model = egg.components().get(DataComponents.ITEM_MODEL);
        if (model == null) return Optional.empty();
        ResourceLocation file = ITEM_DEFINITIONS.idToFile(model);
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
