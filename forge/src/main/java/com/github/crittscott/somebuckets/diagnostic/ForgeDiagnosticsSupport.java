package com.github.crittscott.somebuckets.diagnostic;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Forge {@link DiagnosticsSupport}: config path and loader name. */
public final class ForgeDiagnosticsSupport implements DiagnosticsSupport {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String loaderName() {
        return "Forge";
    }
}
