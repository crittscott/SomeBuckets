package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.FabricBBItem;
import com.github.crittscott.somebuckets.item.FabricSBItem;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Registers the six Fabric item instances. */
public final class FabricItems {
    /** Huge Bucket item instance. */
    public static final FabricBBItem BIG_BUCKET_64 = new FabricBBItem(
            itemProperties(BucketDefinitions.HUGE_BUCKET_ID), BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS);
    /** Big Bucket item instance. */
    public static final FabricBBItem BIG_BUCKET_8 = new FabricBBItem(
            itemProperties(BucketDefinitions.BIG_BUCKET_ID), BucketDefinitions.BIG_BUCKET_CAPACITY_UNITS);
    /** Junk Bucket item instance. */
    public static final JBItem JUNK_BUCKET = new JBItem(
            itemProperties(BucketDefinitions.JUNK_BUCKET_ID), BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS);
    /** Mob Bucket item instance. */
    public static final MBItem MOB_BUCKET = new MBItem(itemProperties(BucketDefinitions.MOB_BUCKET_ID));
    /** Source Bucket item instance. */
    public static final FabricSBItem SOURCE_BUCKET =
            new FabricSBItem(itemProperties(BucketDefinitions.SOURCE_BUCKET_ID));
    /** Trash Bucket item instance. */
    public static final TBItem TRASH_BUCKET = new TBItem(itemProperties(BucketDefinitions.TRASH_BUCKET_ID));

    private FabricItems() {}

    private static Item.Properties itemProperties(ResourceLocation id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
    }

    /** Registers all six bucket item instances. */
    public static void register() {
        register(BucketDefinitions.HUGE_BUCKET_ID, BIG_BUCKET_64);
        register(BucketDefinitions.BIG_BUCKET_ID, BIG_BUCKET_8);
        register(BucketDefinitions.JUNK_BUCKET_ID, JUNK_BUCKET);
        register(BucketDefinitions.MOB_BUCKET_ID, MOB_BUCKET);
        register(BucketDefinitions.SOURCE_BUCKET_ID, SOURCE_BUCKET);
        register(BucketDefinitions.TRASH_BUCKET_ID, TRASH_BUCKET);
    }

    private static void register(ResourceLocation id, Item item) {
        Registry.register(BuiltInRegistries.ITEM, id, item);
    }
}
