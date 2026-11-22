package com.github.crittscott.somebuckets.diagnostic;

import com.github.crittscott.somebuckets.client.MobEggColors;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Row;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Status;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.util.TwoColorPalette.Colors;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Samples every registered egg, then reports actual Mob Bucket colors and a thumbnail contact sheet. */
public final class EggDiagnostics {
    private static final int CELL_HEIGHT = 72;
    private static final int CELL_WIDTH = 256;
    private static final int COLUMNS = 4;
    private static final int LABEL_CHARS = 36;
    private static final String IMAGE_FILE = "eggs-palettes.png";
    private static final String REPORT_FILE = "eggs-report.txt";

    private static boolean running;

    private EggDiagnostics() {}

    private record Report(List<Row> rows, List<Thumbnail> thumbnails) {}
    private record Thumbnail(String id, MobEggColors.Sample sample) {}

    private static Row classify(String id, EntityType<?> type, MobEggColors.ReportSamples samples) {
        boolean blacklisted = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(BucketDefinitions.MB_BLACKLIST);
        boolean capturable = type.canSerialize() && type.getCategory() != MobCategory.MISC && !blacklisted;
        String suffix = blacklisted ? " · blacklisted" : "";
        Colors override = samples.overrides().get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        Item egg = SpawnEggItem.byId(type).map(Holder::value).orElse(null);
        MobEggColors.Sample sample = egg == null ? null : samples.eggs().get(egg);
        if (override != null) {
            List<String> details = new ArrayList<>();
            details.add("override primary " + DiagnosticReport.hex(override.primary())
                    + " · secondary " + DiagnosticReport.hex(override.secondary()) + suffix);
            if (sample != null && sample.colors() != null) details.add(paletteLine(sample.colors()));
            List<String> notes = new ArrayList<>();
            notes.add("colors supplied by mob_egg_colors.json; contact sheet shows the automatic sample");
            if (sample != null && sample.error() != null) notes.add("automatic sampling failed: " + sample.error());
            return new Row(id, Status.OK, details, notes);
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

    private static Report collect(MobEggColors.ReportSamples samples) {
        List<Row> rows = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
                .forEach(entry -> {
                    String id = entry.getKey().identifier().toString();
                    try {
                        rows.add(classify(id, entry.getValue(), samples));
                    } catch (RuntimeException | LinkageError throwable) {
                        rows.add(new Row(id, Status.ERROR, List.of(), List.of(throwable.toString())));
                    }
                });
        List<Thumbnail> thumbnails = samples.eggs().entrySet().stream()
                .sorted(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.getKey()).toString()))
                .map(entry -> new Thumbnail(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue()))
                .toList();
        return new Report(List.copyOf(rows), thumbnails);
    }

    private static String paletteLine(Colors colors) {
        return "sampled primary " + DiagnosticReport.hex(colors.primary())
                + " · secondary " + DiagnosticReport.hex(colors.secondary());
    }

    /** Queues the sweep; feedback and reports are completed after the asynchronous GPU readbacks. */
    public static void runReport(Consumer<Component> feedback) {
        if (running) {
            feedback.accept(Component.translatable("commands.somebuckets.sb.eggs.running"));
            return;
        }
        running = true;
        feedback.accept(Component.translatable("commands.somebuckets.sb.eggs.sampling"));
        MobEggColors.sampleAll().whenComplete((samples, error) -> Minecraft.getInstance().schedule(() -> {
            if (error != null) {
                running = false;
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                feedback.accept(cause instanceof CancellationException
                        ? Component.translatable("commands.somebuckets.sb.eggs.cancelled")
                        : Component.translatable("commands.somebuckets.sb.write_failed", REPORT_FILE, cause.toString()));
                return;
            }
            Report report = collect(samples);
            Util.ioPool().execute(() -> writeReport(report, feedback));
        }));
    }

    private static void writeContactSheet(List<Thumbnail> thumbnails, Path file) throws IOException {
        int rows = Math.max(1, (thumbnails.size() + COLUMNS - 1) / COLUMNS);
        BufferedImage sheet = new BufferedImage(COLUMNS * CELL_WIDTH, rows * CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(new Color(40, 40, 40));
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            for (int index = 0; index < thumbnails.size(); index++) {
                Thumbnail thumbnail = thumbnails.get(index);
                int x = index % COLUMNS * CELL_WIDTH;
                int y = index / COLUMNS * CELL_HEIGHT;
                graphics.setColor(Color.WHITE);
                String id = thumbnail.id();
                graphics.drawString(id.substring(0, Math.min(LABEL_CHARS, id.length())), x + 8, y + 12);
                if (id.length() > LABEL_CHARS) {
                    graphics.drawString(id.substring(LABEL_CHARS, Math.min(LABEL_CHARS * 2, id.length())), x + 8, y + 25);
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
                    graphics.drawString(DiagnosticReport.hex(colors.primary()), x + 88, y + 44);
                    graphics.drawString(DiagnosticReport.hex(colors.secondary()), x + 88, y + 60);
                }
            }
            if (!ImageIO.write(sheet, "png", file.toFile())) throw new IOException("PNG writer is unavailable");
        } finally {
            graphics.dispose();
            sheet.flush();
        }
    }

    private static void writeReport(Report report, Consumer<Component> feedback) {
        List<Component> messages = new ArrayList<>();
        String writing = REPORT_FILE;
        try {
            Path file = DiagnosticReport.write(REPORT_FILE, "spawn egg diagnostic",
                    List.of(report.rows().size() + " entity types; " + report.thumbnails().size() + " sampled eggs",
                            "automatic palettes: opacity-weighted Oklab clusters of rendered default eggs",
                            "thumbnail contact sheet: " + IMAGE_FILE), report.rows());
            writing = IMAGE_FILE;
            Path image = file.resolveSibling(IMAGE_FILE);
            writeContactSheet(report.thumbnails(), image);
            messages.addAll(DiagnosticReport.feedback(
                    FluidDiagnostics.commandPath(FluidDiagnostics.EGGS_SUBCOMMAND), report.rows(), file));
            messages.add(Component.translatable("commands.somebuckets.sb.eggs.image", image.toString()));
        } catch (IOException exception) {
            messages.add(Component.translatable("commands.somebuckets.sb.write_failed", writing, exception.getMessage()));
        }
        Minecraft.getInstance().schedule(() -> {
            running = false;
            messages.forEach(feedback);
        });
    }
}
