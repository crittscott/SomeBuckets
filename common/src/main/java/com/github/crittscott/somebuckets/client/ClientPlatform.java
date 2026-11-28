package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SomeBucketItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * The client's loader seam: the loader's fluid rendering facts, plus the config directory and loader
 * name the diagnostic reports need. Every client fluid appearance is derived from those facts: the
 * {@link FluidBucketModel} fluid layer and the Big and Huge Bucket bar color here, and the
 * {@code /sb fluids} report in {@link ClientDiagnostics}.
 */
@Environment(EnvType.CLIENT)
public final class ClientPlatform {
    /**
     * Loader-resolved rendering facts for a stored fluid.
     *
     * @param sprite the still sprite on the block atlas, or {@code null} when the fluid has none
     * @param stillTexture the still texture's id, or {@code null} when the fluid declares none
     * @param tint stack-aware ARGB tint
     * @param emissive whether the fluid emits light
     */
    public record FluidFacts(@Nullable TextureAtlasSprite sprite, @Nullable Identifier stillTexture,
                             int tint, boolean emissive) {}

    /** Resolves a stored fluid's rendering facts through the loader's client fluid API. */
    @FunctionalInterface
    public interface FluidFactsSource {
        FluidFacts facts(StoredFluid fluid);
    }

    private static volatile FluidFactsSource source;
    private static volatile Path configDir;
    private static volatile String loaderName;

    private ClientPlatform() {}

    /**
     * Installs the loader's client seam and the bar-color resolver it backs. Called once during
     * client bootstrap.
     *
     * @param factsSource the loader's fluid rendering facts
     * @param loaderConfigDir the loader config directory; reports go under {@code <dir>/somebuckets/}
     * @param loader short loader name for report headers ("Forge", "NeoForge", "Fabric")
     */
    public static void install(FluidFactsSource factsSource, Path loaderConfigDir, String loader) {
        source = factsSource;
        configDir = loaderConfigDir;
        loaderName = loader;
        BBItem.installFluidBarColor(fluid -> barColor(fluid, SomeBucketItem.DEFAULT_BUCKET_BAR_COLOR));
        SomeBuckets.LOGGER.info("Some Buckets ({} client): client platform installed", loaderName);
    }

    /** The loader config directory. */
    public static Path configDir() {
        return configDir;
    }

    /** Short loader name for report headers. */
    public static String loaderName() {
        return loaderName;
    }

    /** Resolves the {@link FluidBucketModel} fluid layer, or {@code null} when the fluid has no still sprite. */
    @Nullable
    static FluidBucketModel.Look look(StoredFluid fluid) {
        FluidFacts facts = facts(fluid);
        return facts.sprite() == null ? null
                : new FluidBucketModel.Look(facts.sprite(), facts.tint(), facts.emissive());
    }

    /**
     * The still texture's average opaque color, or {@code fallbackRgb} when it has none, multiplied
     * by the stack-aware tint.
     */
    public static int barColor(StoredFluid fluid, int fallbackRgb) {
        if (fluid.isEmpty()) return fallbackRgb;
        FluidFacts facts = facts(fluid);
        return ClientTextureColors.color(facts.sprite(), facts.tint(), fallbackRgb);
    }

    /**
     * Client resource reload listener each loader registers: reads Mob Bucket palette overrides in
     * background preparation, then, on the game thread, resets sampled egg palettes and averages every source
     * fluid's still texture so drawing a bucket bar never reads a file. Loaders run mod listeners
     * after vanilla's, so the block atlas is already stitched when {@code apply} resolves sprites.
     * Stack-dependent variant textures are averaged on first use.
     */
    public static class ColorReloadListener extends SimplePreparableReloadListener<MobEggColors.Loaded> {
        @Override
        protected MobEggColors.Loaded prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return MobEggColors.load(resourceManager);
        }

        @Override
        protected void apply(MobEggColors.Loaded eggColors, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            MobEggColors.install(eggColors);
            ClientTextureColors.clearCache();
            // Forge-family loaders may run the first reload before client setup installs the platform.
            if (source == null) return;
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                if (fluid == Fluids.EMPTY || !fluid.defaultFluidState().isSource()) continue;
                try {
                    ClientTextureColors.average(facts(new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB))
                            .sprite());
                } catch (RuntimeException | LinkageError e) {
                    // One fluid's failing client facts must not abort the reload for every other fluid.
                    SomeBuckets.LOGGER.warn("Could not read client fluid facts for {} while preloading bucket "
                            + "bar colors; /sb fluids reports it", BuiltInRegistries.FLUID.getKey(fluid), e);
                }
            }
        }
    }

    /** The loader's rendering facts for {@code fluid}. */
    static FluidFacts facts(StoredFluid fluid) {
        return source.facts(fluid);
    }
}
