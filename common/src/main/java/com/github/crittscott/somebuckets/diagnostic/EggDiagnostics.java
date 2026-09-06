package com.github.crittscott.somebuckets.diagnostic;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Row;
import com.github.crittscott.somebuckets.diagnostic.DiagnosticReport.Status;
import com.github.crittscott.somebuckets.item.MobEggColors;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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
 * Bucket tints its overlays with, flagging capturable types that have no egg and eggs whose two
 * colors give no usable tint. Types listed in {@code mob_egg_colors.json} report their override
 * colors and are not flagged. Server-side; safe on a dedicated server.
 */
public final class EggDiagnostics {
    private static final TagKey<EntityType<?>> MB_BLACKLIST = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "mb_blacklist"));

    private EggDiagnostics() {}

    /** Server registration: {@code /sb eggs}, permission level 2, for headless use. */
    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sb").then(Commands.literal("eggs")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    runReport(line -> source.sendSuccess(() -> line, false));
                    return 1;
                })));
    }

    /**
     * Runs the sweep, writes {@code config/somebuckets/eggs-report.txt}, and routes the summary lines
     * to {@code feedback}. Shared by the server command and the client {@code /sb} tree.
     */
    public static void runReport(Consumer<Component> feedback) {
        List<Row> rows = collect();
        try {
            Path file = DiagnosticReport.write("eggs-report.txt", "spawn egg diagnostic",
                    List.of(rows.size() + " entity types"), rows);
            for (Component line : DiagnosticReport.feedback("/sb eggs", rows, "", file, 15)) {
                feedback.accept(line);
            }
        } catch (IOException exception) {
            feedback.accept(Component.literal(
                    "[Some Buckets] could not write eggs-report.txt: " + exception.getMessage()));
        }
    }

    private static List<Row> collect() {
        DiagnosticsSupport support = DiagnosticsSupport.get();
        List<Row> rows = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    String id = entry.getKey().location().toString();
                    try {
                        rows.add(classify(id, entry.getValue(), support));
                    } catch (Throwable throwable) {
                        rows.add(new Row(id, Status.ERROR, List.of(),
                                List.of(throwable.getClass().getSimpleName() + ": " + throwable.getMessage())));
                    }
                });
        return rows;
    }

    private static Row classify(String id, EntityType<?> type, DiagnosticsSupport support) {
        boolean blacklisted = type.is(MB_BLACKLIST);
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

        try {
            SpawnEggItem egg = support.spawnEggFor(type);
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
            if (primary == secondary) notes.add("primary and secondary colors are identical");
            if (DiagnosticReport.noHue(primary) && DiagnosticReport.noHue(secondary)) {
                notes.add("both colors are gray (no hue)");
            }
            return new Row(id, notes.isEmpty() ? Status.OK : Status.SUSPECT, detail, List.copyOf(notes));
        } catch (Throwable throwable) {
            return new Row(id, Status.ERROR, List.of("spawn egg" + suffix),
                    List.of(throwable.getClass().getSimpleName() + ": " + throwable.getMessage()));
        }
    }
}
