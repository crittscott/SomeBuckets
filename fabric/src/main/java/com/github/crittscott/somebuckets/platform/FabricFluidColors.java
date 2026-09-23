package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.util.StoredFluid;

import java.util.function.ToIntFunction;

/** Server-safe indirection installed by the Fabric client entry point for fluid tint lookup. */
public final class FabricFluidColors {
    private static ToIntFunction<StoredFluid> resolver = fluid -> -1;

    private FabricFluidColors() {}

    /** Installs the client-side resolver; the default resolver reports no color on a dedicated server. */
    public static void install(ToIntFunction<StoredFluid> colorResolver) {
        resolver = colorResolver;
    }

    /** Returns the resolved 24-bit RGB color, or {@code fallback} when the installed resolver has none. */
    public static int color(StoredFluid fluid, int fallback) {
        int color = resolver.applyAsInt(fluid);
        return color < 0 ? fallback : color & 0xFFFFFF;
    }
}
