package com.github.crittscott.somebuckets;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Shared mod identity used by both loader entrypoints and by common code. */
public final class SomeBuckets {
    public static final String MODID = "somebuckets";
    public static final Logger LOGGER = LogUtils.getLogger();

    private SomeBuckets() {}

    /**
     * Logs the startup milestone once the loader's setup has finished.
     *
     * @param loaderName loader name shown in the log
     * @param version this mod's version from the loader's metadata
     */
    public static void logStarted(String loaderName, String version) {
        LOGGER.info("Some Buckets {} ({}) initialized", version, loaderName);
    }
}
