package com.github.crittscott.somebuckets.config;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable, reloadable view of the server allowlist governing every Source Bucket input and
 * output boundary, including the special non-fluid milk mode.
 */
public final class SBPolicy {
    private static volatile Snapshot snapshot;

    private SBPolicy() {}

    public static boolean allows(FluidStack stack) {
        return !stack.isEmpty() && allows(stack.getFluid());
    }

    public static boolean allows(Fluid fluid) {
        for (Fluid allowed : current().allowedFluids()) {
            if (fluid.isSame(allowed)) return true;
        }
        return false;
    }

    public static boolean allowsMilk() {
        return current().milkAllowed();
    }

    /** Resolves the current server config for fast policy checks until the next config event. */
    public static synchronized void refresh(String configFileName) {
        Snapshot resolved = resolve(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get());
        snapshot = resolved;

        String context = configFileName == null || configFileName.isBlank()
                ? "the server configuration"
                : configFileName;
        for (String unknownId : resolved.unknownIds()) {
            SomeBuckets.LOGGER.warn(
                    "Ignoring unknown Source Bucket allowed content '{}' in {}", unknownId, context);
        }
    }

    private static Snapshot current() {
        Snapshot current = snapshot;
        if (current != null) return current;

        synchronized (SBPolicy.class) {
            if (snapshot == null) {
                snapshot = resolve(ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get());
            }
            return snapshot;
        }
    }

    private static Snapshot resolve(List<? extends String> configuredIds) {
        Set<Fluid> allowedFluids = new LinkedHashSet<>();
        Set<String> unknownIds = new LinkedHashSet<>();
        boolean milkAllowed = false;

        for (String configuredId : configuredIds) {
            ResourceLocation id = new ResourceLocation(configuredId);
            if (id.equals(ServerConfig.MILK_ID)) {
                milkAllowed = true;
                continue;
            }
            if (!ForgeRegistries.FLUIDS.containsKey(id)) {
                unknownIds.add(configuredId);
                continue;
            }

            allowedFluids.add(ForgeRegistries.FLUIDS.getValue(id));
        }

        return new Snapshot(
                Set.copyOf(allowedFluids),
                milkAllowed,
                Set.copyOf(unknownIds));
    }

    private record Snapshot(Set<Fluid> allowedFluids, boolean milkAllowed, Set<String> unknownIds) {}
}
