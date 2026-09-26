package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.NeoForgeBBItem;
import com.github.crittscott.somebuckets.item.NeoForgeSBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the mod's bucket items and diagnostic fluid-model probe. */
public final class ModItems {
    /** Deferred register for all mod items. */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SomeBuckets.MODID);

    /** Huge Bucket registry holder. */
    public static final DeferredHolder<Item, Item> BIG_BUCKET_64 = ITEMS.register(
            BucketDefinitions.HUGE_BUCKET_ID.getPath(),
            () -> new NeoForgeBBItem(itemProperties(BucketDefinitions.HUGE_BUCKET_ID),
                    BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS));
    /** Big Bucket registry holder. */
    public static final DeferredHolder<Item, Item> BIG_BUCKET_8 = ITEMS.register(
            BucketDefinitions.BIG_BUCKET_ID.getPath(),
            () -> new NeoForgeBBItem(itemProperties(BucketDefinitions.BIG_BUCKET_ID),
                    BucketDefinitions.BIG_BUCKET_CAPACITY_UNITS));
    /** Junk Bucket registry holder. */
    public static final DeferredHolder<Item, Item> JUNK_BUCKET = ITEMS.register(
            BucketDefinitions.JUNK_BUCKET_ID.getPath(),
            () -> new JBItem(itemProperties(BucketDefinitions.JUNK_BUCKET_ID),
                    BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS));
    /** Mob Bucket registry holder. */
    public static final DeferredHolder<Item, Item> MOB_BUCKET = ITEMS.register(
            BucketDefinitions.MOB_BUCKET_ID.getPath(),
            () -> new MBItem(itemProperties(BucketDefinitions.MOB_BUCKET_ID)));
    /** Source Bucket registry holder. */
    public static final DeferredHolder<Item, Item> SOURCE_BUCKET = ITEMS.register(
            BucketDefinitions.SOURCE_BUCKET_ID.getPath(),
            () -> new NeoForgeSBItem(itemProperties(BucketDefinitions.SOURCE_BUCKET_ID)));
    /** Trash Bucket registry holder. */
    public static final DeferredHolder<Item, Item> TRASH_BUCKET = ITEMS.register(
            BucketDefinitions.TRASH_BUCKET_ID.getPath(),
            () -> new TBItem(itemProperties(BucketDefinitions.TRASH_BUCKET_ID)));

    private static Item.Properties itemProperties(ResourceLocation id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
    }

    private ModItems() {}

    /** Attaches item registration to the mod event bus. */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
