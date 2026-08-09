package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.FabricBBItem;
import com.github.crittscott.somebuckets.item.FabricSBItem;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.core.Registry;

/** Registers the six Fabric item instances. */
public final class FabricItems {
    public static final FabricBBItem BIG_BUCKET_64 = new FabricBBItem(new Item.Properties(), 64);
    public static final FabricBBItem BIG_BUCKET_8 = new FabricBBItem(new Item.Properties(), 8);
    public static final JBItem JUNK_BUCKET = new JBItem(new Item.Properties(), 9);
    public static final MBItem MOB_BUCKET = new MBItem(new Item.Properties());
    public static final FabricSBItem SOURCE_BUCKET = new FabricSBItem(new Item.Properties());
    public static final TBItem TRASH_BUCKET = new TBItem(new Item.Properties());

    private FabricItems() {}

    public static void register() {
        register("big_bucket_64", BIG_BUCKET_64);
        register("big_bucket_8", BIG_BUCKET_8);
        register("junk_bucket", JUNK_BUCKET);
        register("mob_bucket", MOB_BUCKET);
        register("source_bucket", SOURCE_BUCKET);
        register("trash_bucket", TRASH_BUCKET);
    }

    private static void register(String name, Item item) {
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(SomeBuckets.MODID, name), item);
    }
}
