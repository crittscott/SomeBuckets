package com.github.crittscott.somebuckets;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Shared mod identity used by all three loader entrypoints and by common code. */
public final class SomeBuckets {
    /** Mod namespace used for every registered id. */
    public static final String MODID = "somebuckets";
    /** Shared logger for lifecycle state and runtime anomalies. */
    public static final Logger LOGGER = LogUtils.getLogger();

    private SomeBuckets() {}
}
