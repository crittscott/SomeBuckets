package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SomeBucketItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.github.crittscott.somebuckets.util.TwoColorPalette.Colors;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

/**
 * The client {@code /sb} operator diagnostics. They read client resources and the loader's client
 * fluid data, so they exist only on the physical client; on a dedicated server an operator runs them
 * from a connected client and the report lands there.
 *
 * <ul>
 *   <li>{@code /sb fluids} walks every registered source fluid and computes its Big and Huge Bucket
 *       bar color through {@link ClientPlatform#barColor}, so a modpack's fluids can be checked for
 *       ones that render as the fallback color or collapse to near-black.</li>
 *   <li>{@code /sb eggs} renders every spawn egg, samples the two-color palette the Mob Bucket tints
 *       its overlays with, and reports each entity type's colors, flagging capturable types that have
 *       no egg or whose egg could not be sampled. Types listed in {@code mob_egg_colors.json} report
 *       their override colors and are not flagged. It also writes a thumbnail contact sheet of the
 *       sampled eggs; sampling waits on GPU readbacks, so this report completes asynchronously.</li>
 * </ul>
 *
 * <p>Each writes {@code <configDir>/somebuckets/<report>}, overwriting any previous run, and sends a
 * summary to the command source. Every finding reaches the operator through that summary and the
 * written report; none is logged.
 */
@Environment(EnvType.CLIENT)
public final class ClientDiagnostics {
    private static final int CHAT_PROBLEM_LIMIT = 15;
    private static final String EGGS_IMAGE = "eggs-palettes.png";
    private static final String EGGS_REPORT = "eggs-report.txt";
    private static final String EGGS_SUBCOMMAND = "eggs";
    private static final int FLUID_FALLBACK_COLOR = SomeBucketItem.DEFAULT_BUCKET_BAR_COLOR;
    private static final String FLUIDS_REPORT = "fluids-report.txt";
    private static final String FLUIDS_SUBCOMMAND = "fluids";
    private static final int NEAR_BLACK_CHANNEL_MAX = 12;
    private static final String ROOT_COMMAND = "sb";
    private static final int SHEET_CELL_HEIGHT = 72;
    private static final int SHEET_CELL_WIDTH = 256;
    private static final int SHEET_COLUMNS = 4;
    private static final int SHEET_LABEL_CHARS = 36;
    private static final int STATUS_COLUMN_WIDTH = 9;
    // Out of alphabetical order: its initializer reads STATUS_COLUMN_WIDTH.
    private static final String DETAIL_INDENT = " ".repeat(STATUS_COLUMN_WIDTH);

    private static boolean eggsRunning;

    private ClientDiagnostics() {}

    private record EggThumbnail(String id, MobEggColors.Sample sample) {}

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

    private static Row classifyEntity(String id, EntityType<?> type, MobEggColors.ReportSamples samples) {
        boolean blacklisted = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(BucketDefinitions.MB_BLACKLIST);
        // No live entity here, so approximate "could be put in a Mob Bucket" from the type alone.
        boolean capturable = type.canSerialize() && type.getCategory() != MobCategory.MISC && !blacklisted;
        String suffix = blacklisted ? " · blacklisted" : "";
        Colors override = samples.overrides().get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        Item egg = SpawnEggItem.byId(type).map(Holder::value).orElse(null);
        MobEggColors.Sample sample = egg == null ? null : samples.eggs().get(egg);
        if (override != null) {
            List<String> detail = new ArrayList<>();
            detail.add("override primary " + hex(override.primary())
                    + " · secondary " + hex(override.secondary()) + suffix);
            if (sample != null && sample.colors() != null) detail.add(paletteLine(sample.colors()));
            List<String> notes = new ArrayList<>();
            notes.add("colors supplied by mob_egg_colors.json; contact sheet shows the automatic sample");
            if (sample != null && sample.error() != null) notes.add("automatic sampling failed: " + sample.error());
            return new Row(id, Status.OK, detail, notes);
        }
        if (egg == null) {
            return new Row(id, capturable ? Status.MISSING : Status.OK, List.of("no spawn egg" + suffix),
                    capturable ? List.of("Mob Bucket tint falls back to gray") : List.of());
        }
        String eggId = BuiltInRegistries.ITEM.getKey(egg).toString();
        if (sample == null || sample.colors() == null) {
            return new Row(id, capturable ? Status.FALLBACK : Status.OK, List.of("egg " + eggId + suffix),
                    List.of(sample == null ? "no completed sample" : sample.error(),
                            "Mob Bucket tint falls back to gray"));
        }
        Colors colors = sample.colors();
        List<String> notes = colors.primary() == colors.secondary()
                ? List.of("single-color palette; identical colors are valid") : List.of();
        return new Row(id, Status.OK, List.of("egg " + eggId + suffix, paletteLine(colors)), notes);
    }

    private static Row classifyFluid(String id, Fluid fluid, List<String> worldFacts) {
        StoredFluid unit = new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB);
        ClientPlatform.FluidFacts facts = ClientPlatform.facts(unit);
        ClientTextureColors.SpriteAverage average = ClientTextureColors.average(facts.sprite());
        int barColor = ClientPlatform.barColor(unit, FLUID_FALLBACK_COLOR);

        List<String> detail = new ArrayList<>();
        detail.add("final " + hex(barColor)
                + " · base " + (average.hasColor() ? hex(average.rgb()) : "n/a")
                + " · tint " + argb(facts.tint()));
        detail.add("still " + (facts.stillTexture() == null ? "<none>" : facts.stillTexture()));
        detail.addAll(worldFacts);

        List<String> notes = new ArrayList<>();
        Status status = switch (average.outcome()) {
            case NO_SPRITE -> {
                notes.add("no still texture; bucket bar uses the fallback color");
                yield Status.FALLBACK;
            }
            case NO_IMAGE -> {
                notes.add("still sprite has no readable source image; bucket bar uses the fallback color "
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
        return ARGB.red(rgb) <= NEAR_BLACK_CHANNEL_MAX
                && ARGB.green(rgb) <= NEAR_BLACK_CHANNEL_MAX
                && ARGB.blue(rgb) <= NEAR_BLACK_CHANNEL_MAX;
    }

    private static String paletteLine(Colors colors) {
        return "sampled primary " + hex(colors.primary()) + " · secondary " + hex(colors.secondary());
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

    /** Queues the egg sweep; the report is written after the asynchronous GPU readbacks complete. */
    private static void runEggs(Consumer<Component> sink) {
        if (eggsRunning) {
            sink.accept(Component.translatable("commands.somebuckets.sb.eggs.running"));
            return;
        }
        eggsRunning = true;
        sink.accept(Component.translatable("commands.somebuckets.sb.eggs.sampling"));
        MobEggColors.sampleAll().whenComplete((samples, error) -> Minecraft.getInstance().schedule(() -> {
            if (error != null) {
                eggsRunning = false;
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                sink.accept(cause instanceof CancellationException
                        ? Component.translatable("commands.somebuckets.sb.eggs.cancelled")
                        : Component.translatable("commands.somebuckets.sb.write_failed", EGGS_REPORT, cause.toString()));
                return;
            }
            List<Row> rows = new ArrayList<>();
            BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
                    .forEach(entry -> {
                        String id = entry.getKey().identifier().toString();
                        try {
                            rows.add(classifyEntity(id, entry.getValue(), samples));
                        } catch (RuntimeException | LinkageError throwable) {
                            rows.add(new Row(id, Status.ERROR, List.of(),
                                    List.of(throwable.getClass().getSimpleName() + ": " + throwable.getMessage())));
                        }
                    });
            List<EggThumbnail> thumbnails = samples.eggs().entrySet().stream()
                    .map(entry -> new EggThumbnail(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(),
                            entry.getValue()))
                    .sorted(Comparator.comparing(EggThumbnail::id))
                    .toList();
            Util.ioPool().execute(() -> writeEggReport(List.copyOf(rows), thumbnails, sink));
        }));
    }

    private static void runFluids(Consumer<Component> sink) {
        List<Row> rows = new ArrayList<>();
        int[] skipped = {0};
        BuiltInRegistries.FLUID.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
                .forEach(entry -> {
                    Fluid fluid = entry.getValue();
                    if (fluid == Fluids.EMPTY) return;
                    String id = entry.getKey().identifier().toString();
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

    private static void writeContactSheet(List<EggThumbnail> thumbnails, Path file) throws IOException {
        int rows = Math.max(1, (thumbnails.size() + SHEET_COLUMNS - 1) / SHEET_COLUMNS);
        BufferedImage sheet = new BufferedImage(SHEET_COLUMNS * SHEET_CELL_WIDTH, rows * SHEET_CELL_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(40, 40, 40));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            for (int index = 0; index < thumbnails.size(); index++) {
                EggThumbnail thumbnail = thumbnails.get(index);
                int x = index % SHEET_COLUMNS * SHEET_CELL_WIDTH;
                int y = index / SHEET_COLUMNS * SHEET_CELL_HEIGHT;
                graphics.setColor(Color.WHITE);
                String id = thumbnail.id();
                graphics.drawString(id.substring(0, Math.min(SHEET_LABEL_CHARS, id.length())), x + 8, y + 12);
                if (id.length() > SHEET_LABEL_CHARS) {
                    graphics.drawString(id.substring(SHEET_LABEL_CHARS,
                            Math.min(SHEET_LABEL_CHARS * 2, id.length())), x + 8, y + 25);
                }
                for (int cy = 0; cy < MobEggColors.SAMPLE_SIZE; cy++) {
                    for (int cx = 0; cx < MobEggColors.SAMPLE_SIZE; cx++) {
                        int shade = ((cx / 4 + cy / 4) & 1) == 0 ? 176 : 208;
                        sheet.setRGB(x + 8 + cx, y + 32 + cy, 0xFF000000 | shade << 16 | shade << 8 | shade);
                    }
                }
                MobEggColors.Sample sample = thumbnail.sample();
                if (sample.pixels().length != 0) {
                    BufferedImage egg = new BufferedImage(MobEggColors.SAMPLE_SIZE, MobEggColors.SAMPLE_SIZE,
                            BufferedImage.TYPE_INT_ARGB);
                    egg.setRGB(0, 0, MobEggColors.SAMPLE_SIZE, MobEggColors.SAMPLE_SIZE, sample.pixels(),
                            0, MobEggColors.SAMPLE_SIZE);
                    graphics.drawImage(egg, x + 8, y + 32, null);
                    egg.flush();
                }
                if (sample.colors() == null) {
                    graphics.setColor(Color.PINK);
                    graphics.drawString("sampling failed", x + 48, y + 48);
                } else {
                    Colors colors = sample.colors();
                    graphics.setColor(new Color(colors.primary(), true));
                    graphics.fillRect(x + 48, y + 32, 12, 32);
                    graphics.setColor(new Color(colors.secondary(), true));
                    graphics.fillRect(x + 64, y + 32, 12, 32);
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(hex(colors.primary()), x + 88, y + 44);
                    graphics.drawString(hex(colors.secondary()), x + 88, y + 60);
                }
            }
            if (!ImageIO.write(sheet, "png", file.toFile())) throw new IOException("PNG writer is unavailable");
        } finally {
            graphics.dispose();
            sheet.flush();
        }
    }

    /* Runs on the IO pool; hands its messages back to the client thread, which also clears the run flag. */
    private static void writeEggReport(List<Row> rows, List<EggThumbnail> thumbnails, Consumer<Component> sink) {
        List<Component> messages = new ArrayList<>();
        String writing = EGGS_REPORT;
        try {
            Path file = write(EGGS_REPORT, "spawn egg diagnostic",
                    List.of(rows.size() + " entity types; " + thumbnails.size() + " sampled eggs",
                            "automatic palettes: opacity-weighted Oklab clusters of rendered default eggs",
                            "thumbnail contact sheet: " + EGGS_IMAGE), rows);
            writing = EGGS_IMAGE;
            Path image = file.resolveSibling(EGGS_IMAGE);
            writeContactSheet(thumbnails, image);
            messages.addAll(feedback("/" + ROOT_COMMAND + " " + EGGS_SUBCOMMAND, rows, null, file));
            messages.add(Component.translatable("commands.somebuckets.sb.eggs.image", image.toString()));
        } catch (IOException exception) {
            messages.add(Component.translatable("commands.somebuckets.sb.write_failed", writing, exception.getMessage()));
        }
        Minecraft.getInstance().schedule(() -> {
            eggsRunning = false;
            messages.forEach(sink);
        });
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
                .append(" · MC ").append(SharedConstants.getCurrentVersion().name()).append('\n');
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
