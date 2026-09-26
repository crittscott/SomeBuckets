package com.github.crittscott.somebuckets.diagnostic;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Fabric {@link DiagnosticsSupport}: config path and loader name. */
public final class FabricDiagnosticsSupport implements DiagnosticsSupport {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }
}
