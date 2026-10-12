package com.github.crittscott.somebuckets.config;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable, reloadable view of the server allowlist governing every Source Bucket input and
 * output boundary, including the special non-fluid milk mode.
 *
 * <p>This class holds no reference to any loader's config machinery; each loader passes its
 * configured content list to {@link #refresh}, or a client applies the server's resolved policy
 * through {@link #replaceFromServer}.
 */
public final class SBPolicy {
    public static final ResourceLocation MILK_ID = new ResourceLocation(SomeBuckets.MODID, "milk");
    public static final String CONFIG_SECTION = "sourceBucket";
    public static final String ALLOWED_CONTENTS_KEY = "allowedContents";
    public static final List<String> DEFAULT_ALLOWED_CONTENT_IDS = List.of(
            "minecraft:water", "minecraft:lava", MILK_ID.toString());

    /*
     * Snapshot used before the owning loader's first refresh call, matching the shipped default
     * allowlist (water, lava, milk) that every loader's config machinery defaults to before a config
     * file overrides it.
     */
    private static volatile Snapshot snapshot = resolve(DEFAULT_ALLOWED_CONTENT_IDS);

    private SBPolicy() {}

    public static boolean allows(Fluid fluid) {
        for (Fluid allowed : snapshot.allowedFluids()) {
            if (fluid.isSame(allowed)) return true;
        }
        return false;
    }

    public static boolean allowsMilk() {
        return snapshot.milkAllowed();
    }

    /**
     * Returns the registered fluid ids in the currently resolved policy for loader synchronization.
     * Milk is reported separately by {@link #allowsMilk()} because it is not a registered fluid.
     *
     * @return immutable, deterministically ordered fluid-id list
     */
    public static List<ResourceLocation> resolvedFluidIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Fluid fluid : snapshot.allowedFluids()) {
            ids.add(BuiltInRegistries.FLUID.getKey(fluid));
        }
        ids.sort(ResourceLocation::compareTo);
        return List.copyOf(ids);
    }

    /** Replaces this process's policy with a server-resolved network snapshot. */
    public static synchronized void replaceFromServer(List<ResourceLocation> fluidIds, boolean milkAllowed) {
        List<String> configuredIds = new ArrayList<>(fluidIds.size() + (milkAllowed ? 1 : 0));
        fluidIds.forEach(id -> configuredIds.add(id.toString()));
        if (milkAllowed) configuredIds.add(MILK_ID.toString());
        Snapshot previous = snapshot;
        Snapshot resolved = resolve(configuredIds);
        snapshot = resolved;
        if (!resolved.sameEffectivePolicy(previous)) {
            SomeBuckets.LOGGER.info("Source Bucket allowlist synchronized from server: {}",
                    describeAllowed(resolved));
        }
    }

    /** Restores the shipped policy after leaving a server. */
    public static synchronized void resetToDefaults() {
        snapshot = resolve(DEFAULT_ALLOWED_CONTENT_IDS);
    }

    /**
     * Resolves the policy from one loader's configured content-id list for fast checks until the
     * next config event. An initial load logs the resolved policy; a reload logs it only when it
     * changed.
     *
     * @param configuredIds registry-name-shaped ids from the loader's config, including the milk id
     * @param configFileName file name for logging
     * @param reload whether this refresh came from a config or data-pack reload
     * @return whether the effective fluid or milk policy changed
     */
    public static synchronized boolean refresh(List<? extends String> configuredIds,
                                               String configFileName, boolean reload) {
        Snapshot previous = snapshot;
        Snapshot resolved = resolve(configuredIds);
        snapshot = resolved;
        boolean changed = !resolved.sameEffectivePolicy(previous);

        for (String unknownId : resolved.unknownIds()) {
            SomeBuckets.LOGGER.warn(
                    "Ignoring unknown Source Bucket allowed content '{}' in {}", unknownId, configFileName);
        }
        if (!reload) {
            SomeBuckets.LOGGER.info("Source Bucket allowlist resolved from {}: {}",
                    configFileName, describeAllowed(resolved));
        } else if (changed) {
            SomeBuckets.LOGGER.info("Source Bucket allowlist reloaded from {}: {}",
                    configFileName, describeAllowed(resolved));
        }
        return changed;
    }

    private static String describeAllowed(Snapshot snapshot) {
        List<String> ids = new ArrayList<>();
        for (Fluid fluid : snapshot.allowedFluids()) {
            ids.add(BuiltInRegistries.FLUID.getKey(fluid).toString());
        }
        if (snapshot.milkAllowed()) ids.add(MILK_ID.toString());
        ids.sort(null);
        return ids.isEmpty() ? "(none)" : String.join(", ", ids);
    }

    private static Snapshot resolve(List<? extends String> configuredIds) {
        Set<Fluid> allowedFluids = new LinkedHashSet<>();
        Set<String> unknownIds = new LinkedHashSet<>();
        boolean milkAllowed = false;

        for (String configuredId : configuredIds) {
            ResourceLocation id = new ResourceLocation(configuredId);
            if (id.equals(MILK_ID)) {
                milkAllowed = true;
                continue;
            }
            if (!BuiltInRegistries.FLUID.containsKey(id)) {
                unknownIds.add(configuredId);
                continue;
            }

            allowedFluids.add(BuiltInRegistries.FLUID.get(id));
        }

        return new Snapshot(Set.copyOf(allowedFluids), milkAllowed, Set.copyOf(unknownIds));
    }

    private record Snapshot(Set<Fluid> allowedFluids, boolean milkAllowed, Set<String> unknownIds) {
        private boolean sameEffectivePolicy(Snapshot other) {
            return milkAllowed == other.milkAllowed && allowedFluids.equals(other.allowedFluids);
        }
    }
}
