package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The client's loader seam and the item presentation built on it. Each loader installs its fluid
 * rendering facts, spawn-egg lookup, config directory, and name, then registers the item model
 * predicates and item tints declared here. The Big and Huge Bucket bar color is derived here from
 * the fluid facts; item models stay loader-owned.
 */
@Environment(EnvType.CLIENT)
public final class ClientPlatform {
    /**
     * The tint index of the bucket item models' {@code layer1} content mask: a fluid bucket's
     * content and the Trash Bucket's void.
     */
    public static final int CONTENT_TINT_INDEX = 1;

    // The Mob Bucket model's first overlay layer; later layers take later spawn-egg colors.
    private static final int MOB_BUCKET_FIRST_OVERLAY_INDEX = 1;
    private static final int NO_TINT = -1;
    private static final int TRASH_VOID_TINT = 0x000000;

    private static volatile Path configDir;
    private static volatile String loaderName;
    private static volatile FluidFactsSource source;
    private static volatile Function<EntityType<?>, SpawnEggItem> spawnEggs;

    private ClientPlatform() {}

    /**
     * Loader-resolved rendering facts for a stored fluid.
     *
     * @param stillTexture the still texture's id, or {@code null} when the fluid declares none
     * @param tint stack-aware ARGB tint
     */
    public record FluidFacts(@Nullable ResourceLocation stillTexture, int tint) {}

    /** Resolves a stored fluid's rendering facts through the loader's client fluid API. */
    @FunctionalInterface
    public interface FluidFactsSource {
        FluidFacts facts(StoredFluid fluid);
    }

    /**
     * Registers one item model predicate. Vanilla's {@code ItemProperties.register} is private and
     * only the loaders widen it, so each loader passes its own access in.
     */
    @FunctionalInterface
    public interface ItemPropertyRegistrar {
        void register(Item item, ResourceLocation property, ClampedItemPropertyFunction function);
    }

    /**
     * The still texture's average opaque color, or {@code fallbackRgb} when it has none, multiplied
     * by the stack-aware tint.
     */
    static int barColor(StoredFluid fluid, int fallbackRgb) {
        if (fluid.isEmpty()) return fallbackRgb;
        FluidFacts facts = facts(fluid);
        return ClientTextureColors.color(facts.stillTexture(), facts.tint(), fallbackRgb);
    }

    /** The loader config directory. */
    static Path configDir() {
        return configDir;
    }

    /** The loader's rendering facts for {@code fluid}. */
    static FluidFacts facts(StoredFluid fluid) {
        return source.facts(fluid);
    }

    /*
     * The fluid content mask takes the stored fluid's color. Milk models have no content layer, so
     * only fluid mode reaches a tinted quad.
     */
    private static int fluidBucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex != CONTENT_TINT_INDEX || BucketState.getMode(stack) != BucketState.Mode.FLUID) return NO_TINT;
        StoredFluid stored = BucketState.getStoredFluid(stack);
        return stored.isEmpty() ? NO_TINT : facts(stored).tint();
    }

    /**
     * Installs the loader's client seam and the bar-color resolver it backs. Called once during
     * client bootstrap.
     *
     * @param factsSource the loader's fluid rendering facts
     * @param eggLookup the loader's spawn egg for an entity type, or {@code null} for none
     * @param loaderConfigDir the loader config directory; reports go under {@code <dir>/somebuckets/}
     * @param loader short loader name for report headers ("Forge", "Fabric")
     */
    public static void install(FluidFactsSource factsSource, Function<EntityType<?>, SpawnEggItem> eggLookup,
                               Path loaderConfigDir, String loader) {
        source = factsSource;
        spawnEggs = eggLookup;
        configDir = loaderConfigDir;
        loaderName = loader;
        BBItem.installFluidBarColor(fluid -> barColor(fluid, BBItem.DEFAULT_FLUID_BAR_COLOR));
        SomeBuckets.LOGGER.info("Some Buckets ({} client): client platform installed", loader);
    }

    /** Short loader name for report headers. */
    static String loaderName() {
        return loaderName;
    }

    private static int mobBucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex < MOB_BUCKET_FIRST_OVERLAY_INDEX) return NO_TINT;
        return MobBucketColors.tint(BucketState.getCurrentEntityType(stack),
                tintIndex - MOB_BUCKET_FIRST_OVERLAY_INDEX);
    }

    /**
     * Registers the item tints with the loader's item color registry. Fluid buckets tint their
     * content mask with the stored fluid's stack-aware color, Mob Buckets tint their two overlays
     * from the stored mob, and the Trash Bucket tints its mask black; every other layer is untinted.
     */
    public static void registerItemColors(BiConsumer<ItemColor, Item> registrar) {
        for (ResourceLocation id : List.of(BucketDefinitions.BIG_BUCKET_ID, BucketDefinitions.HUGE_BUCKET_ID,
                BucketDefinitions.SOURCE_BUCKET_ID)) {
            registrar.accept(ClientPlatform::fluidBucketTint, BuiltInRegistries.ITEM.get(id));
        }
        registrar.accept(ClientPlatform::mobBucketTint, BuiltInRegistries.ITEM.get(BucketDefinitions.MOB_BUCKET_ID));
        registrar.accept(ClientPlatform::trashBucketTint,
                BuiltInRegistries.ITEM.get(BucketDefinitions.TRASH_BUCKET_ID));
    }

    /**
     * Registers the content predicate of the fluid buckets and the filled predicate of the Mob Bucket,
     * which select their item model overrides.
     */
    public static void registerItemProperties(ItemPropertyRegistrar registrar) {
        ClampedItemPropertyFunction content = (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack);
        for (ResourceLocation id : List.of(BucketDefinitions.BIG_BUCKET_ID, BucketDefinitions.HUGE_BUCKET_ID,
                BucketDefinitions.SOURCE_BUCKET_ID)) {
            registrar.register(BuiltInRegistries.ITEM.get(id), FluidBucketItem.CONTENT_PROPERTY, content);
        }
        registrar.register(BuiltInRegistries.ITEM.get(BucketDefinitions.MOB_BUCKET_ID), MBItem.FILLED_PROPERTY,
                (stack, level, entity, seed) -> MBItem.getFilledProperty(stack));
    }

    /**
     * Drops every client color and geometry cache after a resource reload, then averages every source
     * fluid's still texture so drawing a bucket bar never reads a file. Each loader calls this from
     * its client resource reload listener. Stack-dependent variant textures are averaged on first use.
     */
    public static void reloadResources() {
        ClientTextureColors.clearCache();
        BucketMouth.clearCache();
        MobBucketColors.clearCache();
        if (source == null) return;
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY || !fluid.defaultFluidState().isSource()) continue;
            try {
                ClientTextureColors.average(facts(new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB))
                        .stillTexture());
            } catch (RuntimeException | LinkageError e) {
                // One fluid's failing client facts must not abort the reload for every other fluid.
                SomeBuckets.LOGGER.warn("Could not read client fluid facts for {} while preloading bucket bar colors;"
                        + " /sb fluids reports it", BuiltInRegistries.FLUID.getKey(fluid), e);
            }
        }
    }

    /** The loader's spawn egg for {@code type}, or {@code null} when it has none. */
    @Nullable
    static SpawnEggItem spawnEgg(EntityType<?> type) {
        return spawnEggs.apply(type);
    }

    private static int trashBucketTint(ItemStack stack, int tintIndex) {
        return tintIndex == CONTENT_TINT_INDEX ? TRASH_VOID_TINT : NO_TINT;
    }
}
