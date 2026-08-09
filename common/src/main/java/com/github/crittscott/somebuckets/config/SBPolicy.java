package com.github.crittscott.somebuckets.config;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable, reloadable view of the server allowlist governing every Source Bucket input and
 * output boundary, including the special non-fluid milk mode.
 *
 * <p>This class holds no reference to any loader's config machinery; each loader resolves its own
 * configured content list and milk id and passes them to {@link #refresh}.
 */
public final class SBPolicy {
    /**
     * Snapshot used before the owning loader's first {@link #refresh} call, matching the shipped
     * default allowlist (water, lava, milk) that every loader's config machinery defaults to before
     * a config file overrides it.
     */
    private static volatile Snapshot snapshot =
            new Snapshot(Set.of(Fluids.WATER, Fluids.LAVA), true, Set.of());

    private SBPolicy() {}

    public static boolean allows(Fluid fluid) {
        for (Fluid allowed : current().allowedFluids()) {
            if (fluid.isSame(allowed)) return true;
        }
        return false;
    }

    public static boolean allowsMilk() {
        return current().milkAllowed();
    }

    /**
     * Resolves the policy from one loader's configured content-id list for fast checks until the
     * next config event.
     *
     * @param configuredIds registry-name-shaped ids from the loader's config, including the milk id
     * @param milkId the loader's registry-name-shaped id representing milk (not a real fluid)
     * @param configFileName file name for logging, or {@code null}/blank if unavailable
     */
    public static synchronized void refresh(List<? extends String> configuredIds, ResourceLocation milkId,
                                            String configFileName) {
        Snapshot resolved = resolve(configuredIds, milkId);
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
        return snapshot;
    }

    private static Snapshot resolve(List<? extends String> configuredIds, ResourceLocation milkId) {
        Set<Fluid> allowedFluids = new LinkedHashSet<>();
        Set<String> unknownIds = new LinkedHashSet<>();
        boolean milkAllowed = false;

        for (String configuredId : configuredIds) {
            ResourceLocation id = new ResourceLocation(configuredId);
            if (id.equals(milkId)) {
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
