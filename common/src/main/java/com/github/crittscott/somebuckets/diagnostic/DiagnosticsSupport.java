package com.github.crittscott.somebuckets.diagnostic;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Loader environment the diagnostic commands need but common code cannot reach directly: the config
 * directory to write reports into, the loader's spawn-egg-for-entity-type lookup, and a display name
 * for the report header. Installed by each loader entry point beside {@code BucketOperations}.
 */
public interface DiagnosticsSupport {
    /** The loader config directory; reports are written under {@code <configDir>/somebuckets/}. */
    Path configDir();

    /**
     * The vanilla-style spawn egg registered for {@code type}, or {@code null} when it has none.
     * Forge resolves modded eggs through {@code ForgeSpawnEggItem}; NeoForge and Fabric use
     * {@code SpawnEggItem.byId}.
     */
    @Nullable
    SpawnEggItem spawnEggFor(EntityType<?> type);

    /** Short loader name for the report header ("Forge", "NeoForge", "Fabric"). */
    String loaderName();

    /** Holds the loader-installed implementation without forcing eager platform initialization. */
    final class Holder {
        private static DiagnosticsSupport instance;
        private Holder() {}
    }

    /**
     * Installs the loader implementation, replacing any previous instance. Called once during
     * single-threaded mod bootstrap.
     */
    static void install(DiagnosticsSupport support) {
        Holder.instance = Objects.requireNonNull(support, "diagnostics support");
    }

    /**
     * Returns the installed implementation.
     *
     * @throws IllegalStateException if no loader entry point has installed one
     */
    static DiagnosticsSupport get() {
        DiagnosticsSupport support = Holder.instance;
        if (support == null) throw new IllegalStateException("Diagnostics support is not installed");
        return support;
    }
}
