package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The client {@code /sb} operator diagnostics. They read client resources and the loader's client
 * fluid data, so they exist only on the physical client; on a dedicated server an operator runs them
 * from a connected client and the report lands there.
 *
 * <ul>
 *   <li>{@code /sb fluids} walks every registered source fluid and computes its Big and Huge Bucket
 *       bar color through {@code ClientPlatform.barColor}, so a modpack's fluids can be checked for
 *       ones that render as the fallback color or collapse to near-black.</li>
 *   <li>{@code /sb eggs} walks every registered entity type and records the colors the Mob Bucket
 *       tints its overlays with, flagging capturable types that have no spawn egg and eggs whose two
 *       colors give no usable tint. Types covered by {@code mob_bucket_colors.json} report their entry
 *       and are not flagged.</li>
 * </ul>
 *
 * <p>Each writes {@code <configDir>/somebuckets/<report>}, overwriting any previous run, and sends a
 * summary to the command source. Every finding reaches the operator through that summary and the
 * written report; none is logged.
 */
@Environment(EnvType.CLIENT)
public final class ClientDiagnostics {
    private static final int CHAT_PROBLEM_LIMIT = 15;
    private static final String EGGS_REPORT = "eggs-report.txt";
    private static final String EGGS_SUBCOMMAND = "eggs";
    private static final int FLUID_FALLBACK_COLOR = BBItem.DEFAULT_FLUID_BAR_COLOR;
    private static final String FLUIDS_REPORT = "fluids-report.txt";
    private static final String FLUIDS_SUBCOMMAND = "fluids";
    private static final int NEAR_BLACK_CHANNEL_MAX = 12;
    private static final String ROOT_COMMAND = "sb";
    private static final int STATUS_COLUMN_WIDTH = 9;
    // Out of alphabetical order: its initializer reads STATUS_COLUMN_WIDTH.
    private static final String DETAIL_INDENT = " ".repeat(STATUS_COLUMN_WIDTH);

    private ClientDiagnostics() {}

    /** Classification of one report entry, most benign first. */
    private enum Status { OK, SUSPECT, FALLBACK, MISSING, ERROR }

    /**
     * One report entry. {@code detail} lines print indented under the id; {@code notes} print as
     * {@code - reason} bullets. Blank strings in either list are dropped.
     */
    private record Row(String id, Status status, List<String> detail, List<String> notes) {}

    private static void append(StringBuilder sb, Row row) {
        sb.append(pad(row.status().name())).append(row.id()).append('\n');
        for (String line : row.detail()) {
            if (!line.isBlank()) sb.append(DETAIL_INDENT).append(line).append('\n');
        }
        for (String note : row.notes()) {
            if (!note.isBlank()) sb.append(DETAIL_INDENT).append("- ").append(note).append('\n');
        }
    }

    /** Formats all 32 bits of {@code color} as {@code #AARRGGBB}. */
    private static String argb(int color) {
        return String.format("#%08X", color);
    }

    private static Row classifyEntity(String id, EntityType<?> type) {
        boolean uncapturable = type.canSerialize() && !MBItem.canStoreType(type);
        // No live entity here, so approximate "could be put in a Mob Bucket" from the type alone.
        boolean capturable = MBItem.canStoreType(type) && type.getCategory() != MobCategory.MISC;
        String suffix = uncapturable ? " · uncapturable" : "";

        String override = MobBucketColors.describeOverride(type);
        if (override != null) {
            return new Row(id, Status.OK, List.of(override + suffix),
                    List.of("colors supplied by mob_bucket_colors.json"));
        }

        SpawnEggItem egg = ClientPlatform.spawnEgg(type);
        if (egg == null) {
            return new Row(id, capturable ? Status.MISSING : Status.OK,
                    List.of("no spawn egg" + suffix),
                    capturable
                            ? List.of("capturable type has no spawn egg; Mob Bucket tint falls back to gray")
                            : List.of());
        }
        int primary = egg.getColor(0);
        int secondary = egg.getColor(1);
        List<String> detail = List.of(
                "egg " + BuiltInRegistries.ITEM.getKey(egg),
                "primary " + hex(primary) + " · secondary " + hex(secondary) + suffix);

        List<String> notes = new ArrayList<>();
        if ((primary & 0xFFFFFF) == (secondary & 0xFFFFFF)) notes.add("primary and secondary colors are identical");
        if (noHue(primary) && noHue(secondary)) notes.add("both colors are gray (no hue)");
        return new Row(id, notes.isEmpty() ? Status.OK : Status.SUSPECT, detail, List.copyOf(notes));
    }

    private static Row classifyFluid(String id, Fluid fluid, List<String> worldFacts) {
        StoredFluid unit = new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB);
        ClientPlatform.FluidFacts facts = ClientPlatform.facts(unit);
        ClientTextureColors.TextureAverage average = ClientTextureColors.average(facts.stillTexture());
        int barColor = ClientPlatform.barColor(unit, FLUID_FALLBACK_COLOR);

        List<String> detail = new ArrayList<>();
        detail.add("final " + hex(barColor)
                + " · base " + (average.hasColor() ? hex(average.rgb()) : "n/a")
                + " · tint " + argb(facts.tint()));
        detail.add("still " + (facts.stillTexture() == null ? "<none>" : facts.stillTexture()));
        detail.addAll(worldFacts);

        List<String> notes = new ArrayList<>();
        Status status = switch (average.outcome()) {
            case NO_TEXTURE -> {
                notes.add("no still texture; bucket bar uses the fallback color");
                yield Status.FALLBACK;
            }
            case NO_IMAGE -> {
                notes.add("still texture has no readable image; bucket bar uses the fallback color "
                        + "(the fluid may still render in world)");
                yield Status.FALLBACK;
            }
            case FULLY_TRANSPARENT, READ_ERROR, OK -> {
                if (average.outcome() == ClientTextureColors.Outcome.FULLY_TRANSPARENT) {
                    notes.add("still texture is fully transparent");
                }
                if (average.outcome() == ClientTextureColors.Outcome.READ_ERROR) {
                    notes.add("still texture image could not be read");
                }
                if (nearBlack(barColor)) notes.add("tint collapses the color to near-black");
                yield notes.isEmpty() ? Status.OK : Status.SUSPECT;
            }
        };
        return new Row(id, status, detail, List.copyOf(notes));
    }

    /**
     * Builds the whole client {@code /sb} tree ({@code eggs} and {@code fluids}) for any Brigadier
     * source whose loader adapter can supply a component feedback consumer.
     */
    public static <S> LiteralArgumentBuilder<S> commandTree(Function<S, Consumer<Component>> feedback) {
        return LiteralArgumentBuilder.<S>literal(ROOT_COMMAND)
                .then(LiteralArgumentBuilder.<S>literal(FLUIDS_SUBCOMMAND).executes(context -> {
                    runFluids(feedback.apply(context.getSource()));
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<S>literal(EGGS_SUBCOMMAND).executes(context -> {
                    runEggs(feedback.apply(context.getSource()));
                    return 1;
                }));
    }

    /** Counts rows by every status, including zero-valued statuses. */
    private static Map<Status, Integer> counts(List<Row> rows) {
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        for (Status status : Status.values()) counts.put(status, 0);
        for (Row row : rows) counts.merge(row.status(), 1, Integer::sum);
        return counts;
    }

    /**
     * The lines to send back to the command source: a one-line count summary with an optional
     * translated suffix, an inline list of the first problem ids, and the report path.
     */
    private static List<Component> feedback(String label, List<Row> rows, @Nullable Component tail, Path file) {
        Map<Status, Integer> counts = counts(rows);
        List<Component> lines = new ArrayList<>();
        Component summary = Component.translatable("commands.somebuckets.sb.summary",
                label, rows.size(), counts.get(Status.OK), counts.get(Status.SUSPECT),
                counts.get(Status.FALLBACK), counts.get(Status.MISSING), counts.get(Status.ERROR));
        if (tail != null) summary = summary.copy().append(" ").append(tail);
        lines.add(summary);

        List<String> problems = new ArrayList<>();
        for (Row row : rows) {
            if (isProblem(row.status())) problems.add(row.id());
        }
        if (!problems.isEmpty()) {
            String shown = String.join(", ", problems.subList(
                    0, Math.min(CHAT_PROBLEM_LIMIT, problems.size())));
            lines.add(problems.size() > CHAT_PROBLEM_LIMIT
                    ? Component.translatable("commands.somebuckets.sb.problems_more", shown,
                            problems.size() - CHAT_PROBLEM_LIMIT)
                    : Component.translatable("commands.somebuckets.sb.problems", shown));
        }
        lines.add(Component.translatable("commands.somebuckets.sb.report_path",
                ClientPlatform.configDir().getParent().relativize(file).toString().replace('\\', '/')));
        return lines;
    }

    /** Formats the low 24 bits of {@code rgb} as {@code #RRGGBB}. */
    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** Returns whether {@code status} belongs in the report's {@code PROBLEMS} section. */
    private static boolean isProblem(Status status) {
        return status != Status.OK;
    }

    /** Whether every channel is near zero - the visible outcome of a color-crushing tint. */
    private static boolean nearBlack(int rgb) {
        return FastColor.ARGB32.red(rgb) <= NEAR_BLACK_CHANNEL_MAX
                && FastColor.ARGB32.green(rgb) <= NEAR_BLACK_CHANNEL_MAX
                && FastColor.ARGB32.blue(rgb) <= NEAR_BLACK_CHANNEL_MAX;
    }

    /** Whether the three channels are equal, i.e. the color carries no hue. */
    private static boolean noHue(int rgb) {
        int green = FastColor.ARGB32.green(rgb);
        return FastColor.ARGB32.red(rgb) == green && green == FastColor.ARGB32.blue(rgb);
    }

    private static String pad(String status) {
        return status.length() >= STATUS_COLUMN_WIDTH
                ? status + " "
                : status + " ".repeat(STATUS_COLUMN_WIDTH - status.length());
    }

    /**
     * Writes the report file and routes the summary lines to {@code sink}, or reports the write
     * failure there.
     */
    private static void report(Consumer<Component> sink, String fileName, String title, List<String> summary,
                               String subcommand, List<Row> rows, @Nullable Component tail) {
        try {
            Path file = write(fileName, title, summary, rows);
            for (Component line : feedback("/" + ROOT_COMMAND + " " + subcommand, rows, tail, file)) {
                sink.accept(line);
            }
        } catch (IOException exception) {
            sink.accept(Component.translatable("commands.somebuckets.sb.write_failed",
                    fileName, exception.getMessage()));
        }
    }

    private static void runEggs(Consumer<Component> sink) {
        List<Row> rows = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    String id = entry.getKey().location().toString();
                    try {
                        rows.add(classifyEntity(id, entry.getValue()));
                    } catch (RuntimeException | LinkageError throwable) {
                        rows.add(new Row(id, Status.ERROR, List.of(),
                                List.of(throwable.getClass().getSimpleName() + ": " + throwable.getMessage())));
                    }
                });
        report(sink, EGGS_REPORT, "spawn egg diagnostic", List.of(rows.size() + " entity types"),
                EGGS_SUBCOMMAND, rows, null);
    }

    private static void runFluids(Consumer<Component> sink) {
        List<Row> rows = new ArrayList<>();
        int[] skipped = {0};
        BuiltInRegistries.FLUID.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    Fluid fluid = entry.getValue();
                    if (fluid == Fluids.EMPTY) return;
                    String id = entry.getKey().location().toString();
                    List<String> worldFacts = List.of();
                    try {
                        if (!fluid.defaultFluidState().isSource()) {
                            skipped[0]++;
                            return;
                        }
                        worldFacts = worldFacts(fluid);
                        rows.add(classifyFluid(id, fluid, worldFacts));
                    } catch (RuntimeException | LinkageError throwable) {
                        rows.add(new Row(id, Status.ERROR, worldFacts,
                                List.of(throwable.getClass().getSimpleName() + ": " + throwable.getMessage())));
                    }
                });
        report(sink, FLUIDS_REPORT, "fluid color diagnostic",
                List.of(rows.size() + " source fluids · " + skipped[0] + " non-source skipped"),
                FLUIDS_SUBCOMMAND, rows, Component.translatable("commands.somebuckets.sb.fluids.skipped", skipped[0]));
    }

    private static List<String> worldFacts(Fluid fluid) {
        BlockState legacy = fluid.defaultFluidState().createLegacyBlock();
        boolean worldPickup = legacy.getBlock() instanceof BucketPickup;
        Item bucket = fluid.getBucket();
        String bucketId = bucket == Items.AIR ? "none" : String.valueOf(BuiltInRegistries.ITEM.getKey(bucket));
        return List.of("world-pickup " + (worldPickup ? "yes" : "no") + " · bucket " + bucketId);
    }

    /**
     * Writes {@code <configDir>/somebuckets/<fileName>}: a header, a {@code == PROBLEMS ==} section of
     * every non-OK row, then {@code == ALL ==}.
     *
     * @return the written file path
     */
    private static Path write(String fileName, String title, List<String> summary, List<Row> rows)
            throws IOException {
        Path dir = ClientPlatform.configDir().resolve(SomeBuckets.MODID);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);

        StringBuilder sb = new StringBuilder();
        sb.append("Some Buckets — ").append(title).append('\n');
        sb.append("generated ").append(Instant.now())
                .append(" · ").append(ClientPlatform.loaderName())
                .append(" · MC ").append(SharedConstants.getCurrentVersion().getName()).append('\n');
        for (String line : summary) sb.append(line).append('\n');
        sb.append('\n');

        sb.append("== PROBLEMS ==\n");
        boolean anyProblem = false;
        for (Row row : rows) {
            if (isProblem(row.status())) {
                append(sb, row);
                anyProblem = true;
            }
        }
        if (!anyProblem) sb.append("(none)\n");
        sb.append('\n');

        sb.append("== ALL ==\n");
        for (Row row : rows) append(sb, row);

        Files.writeString(file, sb.toString());
        return file;
    }
}
