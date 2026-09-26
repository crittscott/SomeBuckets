package com.github.crittscott.somebuckets.diagnostic;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** NeoForge {@link DiagnosticsSupport}: config path and loader name. */
public final class NeoForgeDiagnosticsSupport implements DiagnosticsSupport {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String loaderName() {
        return "NeoForge";
    }
}
