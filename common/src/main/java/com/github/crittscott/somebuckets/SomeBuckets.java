package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Dispensers;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/** Shared mod identity and setup used by all three loader entrypoints and by common code. */
public final class SomeBuckets {
    /** Mod namespace used for every registered id. */
    public static final String MODID = "somebuckets";
    /** Shared logger for lifecycle state and runtime anomalies. */
    public static final Logger LOGGER = LogUtils.getLogger();

    private SomeBuckets() {}

    /** Creates an id in the Some Buckets namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

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
