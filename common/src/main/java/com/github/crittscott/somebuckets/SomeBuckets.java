package com.github.crittscott.somebuckets;

import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.Dispensers;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

/** Shared mod identity and setup used by all three loader entrypoints and by common code. */
public final class SomeBuckets {
    /** Mod namespace used for every registered id. */
    public static final String MODID = "somebuckets";
    /** Shared logger for lifecycle state and runtime anomalies. */
    public static final Logger LOGGER = LogUtils.getLogger();

    private SomeBuckets() {}

    /**
     * Registers the vanilla behaviors the bucket items plug into: dispenser behaviors for all six
     * items and the Big and Huge Bucket cauldron interactions. Called once during mod setup, after
     * the items are registered and the loader's {@code BucketOperations} is installed.
     */
    public static void registerBehaviors(Item big8, Item big64, Item sourceBucket,
                                         Item mobBucket, Item junkBucket, Item trashBucket) {
        Dispensers.register(big8, big64, sourceBucket, mobBucket, junkBucket, trashBucket);
        Cauldrons.register(big8, big64);
    }
}
