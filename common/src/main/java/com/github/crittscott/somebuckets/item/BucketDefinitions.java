package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.resources.ResourceLocation;

/** Defines the registry identities and capacities of the mod's bucket items. */
public final class BucketDefinitions {
    public static final ResourceLocation BIG_BUCKET_ID = id("big_bucket_8");
    public static final ResourceLocation HUGE_BUCKET_ID = id("big_bucket_64");
    public static final ResourceLocation JUNK_BUCKET_ID = id("junk_bucket");
    public static final ResourceLocation MOB_BUCKET_ID = id("mob_bucket");
    public static final ResourceLocation SOURCE_BUCKET_ID = id("source_bucket");
    public static final ResourceLocation TRASH_BUCKET_ID = id("trash_bucket");

    public static final int BIG_BUCKET_CAPACITY_UNITS = 8;
    public static final int HUGE_BUCKET_CAPACITY_UNITS = 64;
    public static final int JUNK_BUCKET_CAPACITY_STACKS = 9;

    private BucketDefinitions() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, path);
    }
}
