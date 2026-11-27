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
 * configured content list to {@link #refresh}.
 */
public final class SBPolicy {
    public static final ResourceLocation MILK_ID =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "milk");
    public static final String CONFIG_SECTION = "sourceBucket";
    public static final String ALLOWED_CONTENTS_KEY = "allowedContents";
    public static final List<String> DEFAULT_ALLOWED_CONTENT_IDS = List.of(
            "minecraft:water", "minecraft:lava", MILK_ID.toString());

    /** Snapshot used before the owning loader's first {@link #refresh} call: the default allowlist. */
    private static volatile Snapshot snapshot = resolve(DEFAULT_ALLOWED_CONTENT_IDS);

    private SBPolicy() {}

    /**
     * Reports whether the current allowlist permits a fluid as Source Bucket content: the fluid, or a
     * fluid {@code isSame} with it such as its flowing form, is on the list.
     *
     * @param fluid fluid to test
     * @return {@code true} when the fluid is on the current allowlist
     */
    public static boolean allows(Fluid fluid) {
        for (Fluid allowed : current().allowedFluids()) {
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
        return current().milkAllowed();
    }

    /**
     * Whether {@code id} is a well-formed content id. Loader configs admit only such entries, since
     * policy resolution parses each one as a resource location.
     */
    public static boolean isValidContentId(String id) {
        return ResourceLocation.tryParse(id) != null;
    }

    /**
     * Resolves the policy from one loader's configured content-id list for fast checks until the
     * next config event. An initial load, such as each server start, logs the resolved policy; a
     * reload logs it only when it changed.
     *
     * @param configuredIds registry-name-shaped ids from the loader's config, including the milk id
     * @param source where the configured list came from, for logging
     * @param reload whether this refresh came from a config or data-pack reload
     */
    public static synchronized void refresh(List<? extends String> configuredIds, String source,
                                            boolean reload) {
        Snapshot previous = snapshot;
        Snapshot resolved = resolve(configuredIds);
        snapshot = resolved;

        for (String unknownId : resolved.unknownIds()) {
            SomeBuckets.LOGGER.warn(
                    "Ignoring unknown Source Bucket allowed content '{}' in {}", unknownId, source);
        }

        if (!reload) {
            SomeBuckets.LOGGER.info("Source Bucket allowlist resolved from {}: {}",
                    source, describeAllowed(resolved));
        } else if (!resolved.equals(previous)) {
            SomeBuckets.LOGGER.info("Source Bucket allowlist reloaded from {}: {}",
                    source, describeAllowed(resolved));
        }
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

    private static Snapshot current() {
        return snapshot;
    }

    private static Snapshot resolve(List<? extends String> configuredIds) {
        Set<Fluid> allowedFluids = new LinkedHashSet<>();
        Set<String> unknownIds = new LinkedHashSet<>();
        boolean milkAllowed = false;

        for (String configuredId : configuredIds) {
            ResourceLocation id = ResourceLocation.parse(configuredId);
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

        return new Snapshot(
                Set.copyOf(allowedFluids),
                milkAllowed,
                Set.copyOf(unknownIds));
    }

    private record Snapshot(Set<Fluid> allowedFluids, boolean milkAllowed, Set<String> unknownIds) {}
}
