package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.NeoForgeBBItem;
import com.github.crittscott.somebuckets.item.NeoForgeSBItem;
import com.github.crittscott.somebuckets.item.TBItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the mod's six bucket items. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SomeBuckets.MODID);

    public static final DeferredHolder<Item, Item> BIG_BUCKET_64 = ITEMS.register(
            BucketDefinitions.HUGE_BUCKET_ID.getPath(),
            () -> new NeoForgeBBItem(new Item.Properties(), BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS));
    public static final DeferredHolder<Item, Item> BIG_BUCKET_8 = ITEMS.register(
            BucketDefinitions.BIG_BUCKET_ID.getPath(),
            () -> new NeoForgeBBItem(new Item.Properties(), BucketDefinitions.BIG_BUCKET_CAPACITY_UNITS));
    public static final DeferredHolder<Item, Item> JUNK_BUCKET = ITEMS.register(
            BucketDefinitions.JUNK_BUCKET_ID.getPath(),
            () -> new JBItem(new Item.Properties(), BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS));
    public static final DeferredHolder<Item, Item> MOB_BUCKET = ITEMS.register(
            BucketDefinitions.MOB_BUCKET_ID.getPath(),
            () -> new MBItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> SOURCE_BUCKET = ITEMS.register(
            BucketDefinitions.SOURCE_BUCKET_ID.getPath(),
            () -> new NeoForgeSBItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> TRASH_BUCKET = ITEMS.register(
            BucketDefinitions.TRASH_BUCKET_ID.getPath(),
            () -> new TBItem(new Item.Properties()));

    private ModItems() {}

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
