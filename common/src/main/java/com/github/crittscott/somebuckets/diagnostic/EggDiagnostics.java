package com.github.crittscott.somebuckets.diagnostic;

import com.github.crittscott.somebuckets.client.MobEggColors;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Row;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Status;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /sb eggs}: walks every registered entity type and records the spawn-egg colors the Mob
 * Bucket tints its overlays with, flagging capturable types that have no egg, eggs whose item
 * definition supplies no colors, and eggs whose two colors give no usable tint. Types listed in
 * {@code mob_egg_colors.json} report their override colors and are not flagged. Egg colors live in
 * client resources, so this runs only from the client {@code /sb} tree.
 */
public final class EggDiagnostics {
    private EggDiagnostics() {}

    /**
     * Runs the sweep, writes {@code config/somebuckets/eggs-report.txt}, and routes the summary lines
     * to {@code feedback}.
     */
    public static void runReport(Consumer<Component> feedback) {
        List<Row> rows = collect();
        try {
            Path file = DiagnosticReport.write("eggs-report.txt", "spawn egg diagnostic",
                    List.of(rows.size() + " entity types"), rows);
            for (Component line : DiagnosticReport.feedback(
                    FluidDiagnostics.commandPath(FluidDiagnostics.EGGS_SUBCOMMAND), rows, file)) {
                feedback.accept(line);
            }
        } catch (IOException exception) {
            feedback.accept(Component.translatable("commands.somebuckets.sb.write_failed",
                    "eggs-report.txt", exception.getMessage()));
        }
    }

    private static List<Row> collect() {
        List<Row> rows = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    String id = entry.getKey().location().toString();
                    try {
                        rows.add(classify(id, entry.getValue()));
                    } catch (RuntimeException | LinkageError throwable) {
                        rows.add(new Row(id, Status.ERROR, List.of(),
                                List.of(throwable.getClass().getSimpleName() + ": " + throwable.getMessage())));
                    }
                });
        return rows;
    }

    private static Row classify(String id, EntityType<?> type) {
        boolean blacklisted = type.is(BucketDefinitions.MB_BLACKLIST);
        // No live entity here, so approximate "could be put in a Mob Bucket" from the type alone.
        boolean capturable = type.canSerialize() && type.getCategory() != MobCategory.MISC && !blacklisted;
        String suffix = blacklisted ? " · blacklisted" : "";

        int[] override = MobEggColors.override(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (override != null) {
            return new Row(id, Status.OK,
                    List.of("override primary " + DiagnosticReport.hex(override[0])
                            + " · secondary " + DiagnosticReport.hex(override[1]) + suffix),
                    List.of("colors supplied by mob_egg_colors.json"));
        }

        SpawnEggItem egg = SpawnEggItem.byId(type);
        if (egg == null) {
            return new Row(id, capturable ? Status.MISSING : Status.OK,
                    List.of("no spawn egg" + suffix),
                    capturable
                            ? List.of("capturable type has no spawn egg; Mob Bucket tint falls back to gray")
                            : List.of());
        }
        int[] colors = MobEggColors.eggColors(egg);
        if (colors == null) {
            return new Row(id, capturable ? Status.MISSING : Status.OK,
                    List.of("egg " + BuiltInRegistries.ITEM.getKey(egg) + " has no constant tints" + suffix),
                    capturable
                            ? List.of("egg item definition supplies no colors; Mob Bucket tint falls back to gray")
                            : List.of());
        }
        int primary = colors[0];
        int secondary = colors[1];
        List<String> detail = List.of(
                "egg " + BuiltInRegistries.ITEM.getKey(egg),
                "primary " + DiagnosticReport.hex(primary)
                        + " · secondary " + DiagnosticReport.hex(secondary) + suffix);

        List<String> notes = new ArrayList<>();
        if (primary == secondary) notes.add("primary and secondary colors are identical");
        if (DiagnosticReport.noHue(primary) && DiagnosticReport.noHue(secondary)) {
            notes.add("both colors are gray (no hue)");
        }
        return new Row(id, notes.isEmpty() ? Status.OK : Status.SUSPECT, detail, List.copyOf(notes));
    }
}
