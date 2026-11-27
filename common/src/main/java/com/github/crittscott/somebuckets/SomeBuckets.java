package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Dispensers;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Shared mod identity and setup used by both loader entrypoints and by common code. */
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

    /**
     * Registers the vanilla behaviors the bucket items plug into: dispenser behaviors for all six
     * items and the Big and Huge Bucket cauldron interactions. Called once during mod setup, after
     * the items are registered and the loader's {@code BucketOperations} is installed.
     */
    public static void registerBehaviors() {
        Dispensers.register();
        Cauldrons.register();
    }
}
