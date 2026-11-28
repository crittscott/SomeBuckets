package com.github.crittscott.somebuckets.config;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
 * configured content list to {@link #refresh}.
 */
public final class SBPolicy {
    /** Synthetic content id representing milk, which is not a registered fluid. */
    public static final Identifier MILK_ID = SomeBuckets.id("milk");
    /** Config section containing Source Bucket policy. */
    public static final String CONFIG_SECTION = "sourceBucket";
    /** Config key containing allowed fluid and synthetic content ids. */
    public static final String ALLOWED_CONTENTS_KEY = "allowedContents";
    /** Shipped allowlist used before configuration is first resolved. */
    public static final List<String> DEFAULT_ALLOWED_CONTENT_IDS = List.of(
            "minecraft:water", "minecraft:lava", MILK_ID.toString());

    /* Snapshot used before the owning loader's first refresh call: the default allowlist. */
    private static volatile Snapshot snapshot = resolve(DEFAULT_ALLOWED_CONTENT_IDS);

    private SBPolicy() {}

    /**
     * Whether {@code fluid}, or a fluid {@link Fluid#isSame isSame} with it (such as its flowing
     * form), is on the current allowlist.
     *
     * @param fluid fluid to test
     * @return {@code true} when the fluid or a same fluid is on the current allowlist
     */
    public static boolean allows(Fluid fluid) {
        for (Fluid allowed : snapshot.allowedFluids()) {
            if (fluid.isSame(allowed)) return true;
        }
        return false;
    }

    /**
     * Reports whether the current allowlist permits milk as Source Bucket content.
     *
     * @return {@code true} when milk is on the current allowlist
     */
    public static boolean allowsMilk() {
        return snapshot.milkAllowed();
    }

    /**
     * Whether {@code id} is a well-formed content id. Loader configs admit only such entries, since
     * policy resolution parses each one as a resource location.
     *
     * @param id configured content id
     * @return {@code true} when {@code id} parses as a resource location
     */
    public static boolean isValidContentId(String id) {
        return Identifier.tryParse(id) != null;
    }

    /**
     * Returns the registered fluid ids in the currently resolved policy for loader synchronization.
     * Milk is reported separately by {@link #allowsMilk()} because it is not a registered fluid.
     *
     * @return immutable, deterministically ordered fluid-id list
     */
    public static List<Identifier> resolvedFluidIds() {
        List<Identifier> ids = new ArrayList<>();
        for (Fluid fluid : snapshot.allowedFluids()) {
            ids.add(BuiltInRegistries.FLUID.getKey(fluid));
        }
        ids.sort(Identifier::compareTo);
        return List.copyOf(ids);
    }

    /** Replaces this process's policy with a server-resolved network snapshot. */
    public static synchronized void replaceFromServer(List<Identifier> fluidIds,
                                                      boolean milkAllowed) {
        List<String> configuredIds = new ArrayList<>(fluidIds.size() + (milkAllowed ? 1 : 0));
        fluidIds.forEach(id -> configuredIds.add(id.toString()));
        if (milkAllowed) configuredIds.add(MILK_ID.toString());
        Snapshot previous = snapshot;
        Snapshot resolved = resolve(configuredIds);
        snapshot = resolved;
        if (!resolved.equals(previous)) {
            SomeBuckets.LOGGER.info("Source Bucket allowlist synchronized from server: {}",
                    describeAllowed(resolved));
        }
    }

    /** Restores the shipped policy after leaving a server. */
    public static synchronized void resetToDefaults() {
        snapshot = resolve(DEFAULT_ALLOWED_CONTENT_IDS);
    }

    /**
     * Resolves the policy and distinguishes an initial config load from a reload for log severity.
     *
     * @param configuredIds registry-name-shaped ids from the loader's config, including the milk id
     * @param source where the configured list came from, for logging
     * @param reload whether this refresh came from a config or data-pack reload
     * @return whether the effective fluid or milk policy changed
     */
    public static synchronized boolean refresh(List<? extends String> configuredIds,
                                               String source, boolean reload) {
        Snapshot previous = snapshot;
        Snapshot resolved = resolve(configuredIds);
        snapshot = resolved;
        boolean changed = !resolved.sameEffectivePolicy(previous);

        for (String unknownId : resolved.unknownIds()) {
            SomeBuckets.LOGGER.warn(
                    "Ignoring unknown Source Bucket allowed content '{}' in {}", unknownId, source);
        }

        if (reload) {
            if (changed) {
                SomeBuckets.LOGGER.info("Source Bucket allowlist reloaded from {}: {}",
                        source, describeAllowed(resolved));
            }
            return changed;
        }
        SomeBuckets.LOGGER.info("Source Bucket allowlist resolved from {}: {}",
                source, describeAllowed(resolved));
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
            Identifier id = Identifier.parse(configuredId);
            if (id.equals(MILK_ID)) {
                milkAllowed = true;
                continue;
            }
            if (!BuiltInRegistries.FLUID.containsKey(id)) {
                unknownIds.add(configuredId);
                continue;
            }

            allowedFluids.add(BuiltInRegistries.FLUID.getValue(id));
        }

        return new Snapshot(
                Set.copyOf(allowedFluids),
                milkAllowed,
                Set.copyOf(unknownIds));
    }

    private record Snapshot(Set<Fluid> allowedFluids, boolean milkAllowed, Set<String> unknownIds) {
        private boolean sameEffectivePolicy(Snapshot other) {
            return milkAllowed == other.milkAllowed && allowedFluids.equals(other.allowedFluids);
        }
    }
}
