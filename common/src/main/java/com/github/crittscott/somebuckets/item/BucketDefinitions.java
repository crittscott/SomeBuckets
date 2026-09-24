package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.resources.ResourceLocation;

/** Defines the registry identities and capacities of the mod's bucket items. */
public final class BucketDefinitions {
    /** Registry id of the eight-unit Big Bucket. */
    public static final ResourceLocation BIG_BUCKET_ID = id("big_bucket_8");
    /** Registry id of the 64-unit Huge Bucket. */
    public static final ResourceLocation HUGE_BUCKET_ID = id("big_bucket_64");
    /** Registry id of the Junk Bucket. */
    public static final ResourceLocation JUNK_BUCKET_ID = id("junk_bucket");
    /** Registry id of the Mob Bucket. */
    public static final ResourceLocation MOB_BUCKET_ID = id("mob_bucket");
    /** Registry id of the Source Bucket. */
    public static final ResourceLocation SOURCE_BUCKET_ID = id("source_bucket");
    /** Registry id of the Trash Bucket. */
    public static final ResourceLocation TRASH_BUCKET_ID = id("trash_bucket");
    /** Registry id of the hidden diagnostic fluid-model probe. */
    public static final ResourceLocation FLUID_MODEL_PROBE_ID = id("fluid_model_probe");

    /** Big Bucket capacity in bucket-volume units. */
    public static final int BIG_BUCKET_CAPACITY_UNITS = 8;
    /** Huge Bucket capacity in bucket-volume units. */
    public static final int HUGE_BUCKET_CAPACITY_UNITS = 64;
    /** Junk Bucket capacity in stored stack entries. */
    public static final int JUNK_BUCKET_CAPACITY_STACKS = 9;

    private BucketDefinitions() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, path);
    }
}
