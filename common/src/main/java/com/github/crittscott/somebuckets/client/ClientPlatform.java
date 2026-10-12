package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/**
 * The client's loader seam: the loader's fluid rendering facts and spawn-egg lookup, plus the config
 * directory and loader name the diagnostic reports need. The Big and Huge Bucket bar color, the Mob
 * Bucket tint, and the {@code /sb} diagnostic samples are derived here from those facts; item
 * models stay loader-owned.
 */
@Environment(EnvType.CLIENT)
public final class ClientPlatform {
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

    private static volatile FluidFactsSource source;
    private static volatile Function<EntityType<?>, SpawnEggItem> spawnEggs;
    private static volatile Path configDir;
    private static volatile String loaderName;

    private ClientPlatform() {}

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

    /** The loader config directory. */
    public static Path configDir() {
        return Objects.requireNonNull(configDir, "Client platform is not installed");
    }

    /** Short loader name for report headers. */
    public static String loaderName() {
        return Objects.requireNonNull(loaderName, "Client platform is not installed");
    }

    /** The loader's spawn egg for {@code type}, or {@code null} when it has none. */
    @Nullable
    public static SpawnEggItem spawnEgg(EntityType<?> type) {
        return Objects.requireNonNull(spawnEggs, "Client platform is not installed").apply(type);
    }

    /**
     * The tint of one Mob Bucket overlay for {@code type}: its spawn egg's base ({@code layer} 0) or
     * spots ({@code layer} 1) color, a {@code mob_bucket_colors.json} entry, or gray.
     */
    public static int mobBucketTint(@Nullable EntityType<?> type, int layer) {
        return MobBucketColors.tint(type, layer);
    }

    /**
     * Describes the {@code mob_bucket_colors.json} entry covering {@code type}, or {@code null} when
     * none does.
     */
    @Nullable
    public static String mobBucketOverride(EntityType<?> type) {
        return MobBucketColors.describeOverride(type);
    }

    /**
     * The still texture's average opaque color, or {@code fallbackRgb} when it has none, multiplied
     * by the stack-aware tint.
     */
    public static int barColor(StoredFluid fluid, int fallbackRgb) {
        if (fluid.isEmpty()) return fallbackRgb;
        FluidFacts facts = facts(fluid);
        return ClientTextureColors.color(facts.stillTexture(), facts.tint(), fallbackRgb);
    }

    /** One fluid's color breakdown for {@code /sb fluids}. */
    public static FluidDiagnostics.FluidColorSample sample(Fluid fluid) {
        FluidFacts facts = facts(new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB));
        ClientTextureColors.TextureAverage average = ClientTextureColors.average(facts.stillTexture());
        return new FluidDiagnostics.FluidColorSample(facts.stillTexture(), average.rgb(), facts.tint(),
                average.textureMissing(), average.imageMissing(), average.fullyTransparent(), average.ioError());
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
            } catch (RuntimeException | LinkageError ignored) {
                // A fluid whose client facts fail is averaged, or reported by /sb fluids, on use.
            }
        }
    }

    private static FluidFacts facts(StoredFluid fluid) {
        FluidFactsSource current = source;
        if (current == null) throw new IllegalStateException("Client platform is not installed");
        return current.facts(fluid);
    }
}
