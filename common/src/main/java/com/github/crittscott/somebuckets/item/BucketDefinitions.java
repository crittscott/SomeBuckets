package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.resources.ResourceLocation;

/**
 * Defines the registry identities of the mod's items, their sound, and the custom recipe
 * ingredients the shared recipes name, plus bucket capacities.
 */
public final class BucketDefinitions {
    public static final ResourceLocation BIG_BUCKET_ID = id("big_bucket_8");
    /** Type id of the ingredient matching an empty bucket of a given item. */
    public static final ResourceLocation EMPTY_BUCKET_INGREDIENT_ID = id("empty_bucket");
    public static final ResourceLocation HUGE_BUCKET_ID = id("big_bucket_64");
    public static final ResourceLocation JUNK_BUCKET_ID = id("junk_bucket");
    public static final ResourceLocation MOB_BUCKET_ID = id("mob_bucket");
    public static final ResourceLocation SOURCE_BUCKET_ID = id("source_bucket");
    /** Type id of the ingredient matching any spawn egg. */
    public static final ResourceLocation SPAWN_EGG_INGREDIENT_ID = id("spawn_egg");
    /** Sound event the Trash Bucket plays when it ejects an item. */
    public static final ResourceLocation TRASH_BUCKET_EJECT_SOUND_ID = id("tb_eject");
    public static final ResourceLocation TRASH_BUCKET_ID = id("trash_bucket");

    /** JSON key naming the item in the empty-bucket ingredient. */
    public static final String INGREDIENT_ITEM_KEY = "item";

    public static final int BIG_BUCKET_CAPACITY_UNITS = 8;
    public static final int HUGE_BUCKET_CAPACITY_UNITS = 64;
    public static final int JUNK_BUCKET_CAPACITY_STACKS = 9;

    private BucketDefinitions() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, path);
    }
}
