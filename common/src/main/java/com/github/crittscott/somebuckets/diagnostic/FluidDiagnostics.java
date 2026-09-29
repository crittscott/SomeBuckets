package com.github.crittscott.somebuckets.diagnostic;

import com.github.crittscott.somebuckets.client.ClientPlatform;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Row;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Status;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SomeBucketItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@code /sb fluids}: walks every registered source fluid and mirrors the Big and Source Bucket
 * bar-color path so a modpack's fluids can be checked for ones that render as the fallback color or
 * collapse to near-black. Client-only: the tint and still texture exist only on the physical client.
 * On a dedicated server an operator runs this from a connected client and the report lands there.
 */
@Environment(EnvType.CLIENT)
public final class FluidDiagnostics {
    /** Root diagnostic command name. */
    public static final String ROOT_COMMAND = "sb";
    /** Fluid diagnostic subcommand name. */
    public static final String FLUIDS_SUBCOMMAND = "fluids";
    /** Spawn-egg diagnostic subcommand name. */
    public static final String EGGS_SUBCOMMAND = "eggs";

    /**
     * One fluid's client color breakdown. {@code baseRgb < 0} means the still texture produced no
     * readable color.
     */
    public record FluidColorSample(@Nullable ResourceLocation stillTexture, int baseRgb, int tintArgb,
                                   boolean spriteMissing, boolean sourceImageMissing,
                                   boolean fullyTransparent, boolean ioError) {}

    private static final int FALLBACK = SomeBucketItem.DEFAULT_BUCKET_BAR_COLOR;

    private FluidDiagnostics() {}

    /** The displayed command path for a diagnostic subcommand. */
    public static String commandPath(String subcommand) {
        return "/" + ROOT_COMMAND + " " + subcommand;
    }

    /**
     * Builds the whole client {@code /sb} tree ({@code eggs} and {@code fluids}) for any Brigadier
     * source whose loader adapter can supply a component feedback consumer.
     */
    public static <S> LiteralArgumentBuilder<S> commandTree(
            Function<S, Consumer<Component>> feedback) {
        return LiteralArgumentBuilder.<S>literal(ROOT_COMMAND)
                .then(LiteralArgumentBuilder.<S>literal(FLUIDS_SUBCOMMAND).executes(context -> {
                    run(feedback.apply(context.getSource()));
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<S>literal(EGGS_SUBCOMMAND).executes(context -> {
                    EggDiagnostics.runReport(feedback.apply(context.getSource()));
                    return 1;
                }));
    }

    /**
     * Runs the sweep, writes {@code config/somebuckets/fluids-report.txt}, and routes the summary
     * lines to {@code feedback}.
     *
     */
    public static void run(Consumer<Component> feedback) {
        List<Row> rows = new ArrayList<>();
        int[] skipped = {0};
        BuiltInRegistries.FLUID.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    Fluid fluid = entry.getValue();
                    if (fluid == Fluids.EMPTY) return;
                    String id = entry.getKey().location().toString();
                    List<String> facts = List.of();
                    try {
                        if (!fluid.defaultFluidState().isSource()) {
                            skipped[0]++;
                            return;
                        }
                        facts = facts(fluid);
                        rows.add(classify(id, fluid, facts));
                    } catch (RuntimeException | LinkageError throwable) {
                        rows.add(new Row(id, Status.ERROR, facts,
                                List.of(throwable.getClass().getSimpleName() + ": " + throwable.getMessage())));
                    }
                });

        try {
            Path file = DiagnosticReport.write("fluids-report.txt", "fluid color diagnostic",
                    List.of(rows.size() + " source fluids · " + skipped[0] + " non-source skipped"), rows);
            Component tail = Component.translatable(
                    "commands.somebuckets.sb.fluids.skipped", skipped[0]);
            for (Component line : DiagnosticReport.feedback(
                    commandPath(FLUIDS_SUBCOMMAND), rows, tail, file)) {
                feedback.accept(line);
            }
        } catch (IOException exception) {
            feedback.accept(Component.translatable("commands.somebuckets.sb.write_failed",
                    "fluids-report.txt", exception.getMessage()));
        }
    }

    private static Row classify(String id, Fluid fluid, List<String> facts) {
        FluidColorSample sample = ClientPlatform.sample(fluid);

        int barColor = ClientPlatform.barColor(
                new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB), FALLBACK);

        List<String> detail = new ArrayList<>();
        detail.add("final " + DiagnosticReport.hex(barColor)
                + " · base " + (sample.baseRgb() < 0 ? "n/a" : DiagnosticReport.hex(sample.baseRgb()))
                + " · tint " + DiagnosticReport.argb(sample.tintArgb()));
        detail.add("still " + (sample.stillTexture() == null ? "<none>" : sample.stillTexture()));
        detail.addAll(facts);

        List<String> notes = new ArrayList<>();
        Status status;
        if (sample.spriteMissing() || sample.stillTexture() == null) {
            notes.add("no still texture; bucket bar uses the fallback color");
            status = Status.FALLBACK;
        } else if (sample.sourceImageMissing() && sample.baseRgb() < 0) {
            notes.add("still sprite has no readable source image; bucket bar uses the fallback color "
                    + "(the fluid may still render in world)");
            status = Status.FALLBACK;
        } else {
            if (sample.fullyTransparent()) notes.add("still texture is fully transparent");
            if (sample.ioError()) notes.add("still texture image could not be read");
            if (DiagnosticReport.nearBlack(barColor)) notes.add("tint collapses the color to near-black");
            status = notes.isEmpty() ? Status.OK : Status.SUSPECT;
        }
        return new Row(id, status, detail, List.copyOf(notes));
    }

    private static List<String> facts(Fluid fluid) {
        BlockState legacy = fluid.defaultFluidState().createLegacyBlock();
        boolean worldPickup = legacy.getBlock() instanceof BucketPickup;
        var bucket = fluid.getBucket();
        String bucketId = bucket == Items.AIR ? "none" : String.valueOf(BuiltInRegistries.ITEM.getKey(bucket));
        return List.of("world-pickup " + (worldPickup ? "yes" : "no") + " · bucket " + bucketId);
    }
}
