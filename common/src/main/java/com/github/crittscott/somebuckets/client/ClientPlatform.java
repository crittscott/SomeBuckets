package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.FluidDiagnostics;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SomeBucketItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The client's loader seam: the loader's fluid rendering facts, plus the config directory and loader
 * name the diagnostic reports need. Every client fluid appearance is derived here from those facts:
 * the {@link FluidBucketModel} fluid layer, the Big and Huge Bucket bar color, and the
 * {@code /sb fluids} diagnostic sample.
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
    public record FluidFacts(@Nullable TextureAtlasSprite sprite, @Nullable ResourceLocation stillTexture,
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
        source = Objects.requireNonNull(factsSource, "fluid facts source");
        configDir = Objects.requireNonNull(loaderConfigDir, "config directory");
        loaderName = Objects.requireNonNull(loader, "loader name");
        BBItem.installFluidBarColor(fluid -> barColor(fluid, SomeBucketItem.DEFAULT_BUCKET_BAR_COLOR));
        SomeBuckets.LOGGER.info("Some Buckets ({} client): client platform installed", loaderName);
    }

    /** The loader config directory. */
    public static Path configDir() {
        return Objects.requireNonNull(configDir, "Client platform is not installed");
    }

    /** Short loader name for report headers. */
    public static String loaderName() {
        return Objects.requireNonNull(loaderName, "Client platform is not installed");
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

    /** One fluid's color breakdown for {@code /sb fluids}. */
    public static FluidDiagnostics.FluidColorSample sample(Fluid fluid) {
        FluidFacts facts = facts(new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB));
        ClientTextureColors.SpriteAverage average = ClientTextureColors.average(facts.sprite());
        return new FluidDiagnostics.FluidColorSample(facts.stillTexture(), average.rgb(), facts.tint(),
                average.spriteMissing(), average.sourceImageMissing(), average.fullyTransparent(),
                average.ioError());
    }

    /** Drops cached texture colors; called from each loader's client resource reload listener. */
    public static void clearCaches() {
        ClientTextureColors.clearCache();
    }

    private static FluidFacts facts(StoredFluid fluid) {
        FluidFactsSource current = source;
        if (current == null) throw new IllegalStateException("Client platform is not installed");
        return current.facts(fluid);
    }
}
