package com.github.crittscott.somebuckets.diagnostic;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Loader environment the diagnostic commands need but common code cannot reach directly: the config
 * directory to write reports into and a display name for the report header. Installed by each
 * loader entry point beside {@code BucketOperations}.
 */
public interface DiagnosticsSupport {
    /** The loader config directory; reports are written under {@code <configDir>/somebuckets/}. */
    Path configDir();

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
