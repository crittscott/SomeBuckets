package com.github.crittscott.somebuckets.diagnostic;

import com.github.crittscott.somebuckets.client.ClientPlatform;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Row;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Status;
import com.github.crittscott.somebuckets.item.MBItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
 * {@code /sb eggs}: walks every registered entity type and records the colors the Mob Bucket tints
 * its overlays with, flagging capturable types that have no spawn egg and eggs whose two colors give
 * no usable tint. Types covered by {@code mob_bucket_colors.json} report their entry and are not
 * flagged. Those entries are client resources, so this runs only from the client {@code /sb} tree.
 */
@Environment(EnvType.CLIENT)
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
                    FluidDiagnostics.commandPath(FluidDiagnostics.EGGS_SUBCOMMAND), rows, null, file)) {
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
        boolean uncapturable = type.canSerialize() && !MBItem.canStoreType(type);
        // No live entity here, so approximate "could be put in a Mob Bucket" from the type alone.
        boolean capturable = MBItem.canStoreType(type) && type.getCategory() != MobCategory.MISC;
        String suffix = uncapturable ? " · uncapturable" : "";

        String override = ClientPlatform.mobBucketOverride(type);
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
                "primary " + DiagnosticReport.hex(primary)
                        + " · secondary " + DiagnosticReport.hex(secondary) + suffix);

        List<String> notes = new ArrayList<>();
        if ((primary & 0xFFFFFF) == (secondary & 0xFFFFFF)) notes.add("primary and secondary colors are identical");
        if (DiagnosticReport.noHue(primary) && DiagnosticReport.noHue(secondary)) {
            notes.add("both colors are gray (no hue)");
        }
        return new Row(id, notes.isEmpty() ? Status.OK : Status.SUSPECT, detail, List.copyOf(notes));
    }
}
