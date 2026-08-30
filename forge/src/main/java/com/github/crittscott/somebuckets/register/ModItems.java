package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.ForgeBBItem;
import com.github.crittscott.somebuckets.item.ForgeJBItem;
import com.github.crittscott.somebuckets.item.ForgeSBItem;
import com.github.crittscott.somebuckets.item.ForgeTBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers the mod's six bucket items. */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SomeBuckets.MODID);

    // Items
    public static final RegistryObject<Item> BIG_BUCKET_64 = ITEMS.register(
            BucketDefinitions.HUGE_BUCKET_ID.getPath(),
            () -> new ForgeBBItem(new Item.Properties(), BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS));
    public static final RegistryObject<Item> BIG_BUCKET_8 = ITEMS.register(
            BucketDefinitions.BIG_BUCKET_ID.getPath(),
            () -> new ForgeBBItem(new Item.Properties(), BucketDefinitions.BIG_BUCKET_CAPACITY_UNITS));
    public static final RegistryObject<Item> JUNK_BUCKET = ITEMS.register(
            BucketDefinitions.JUNK_BUCKET_ID.getPath(),
            () -> new ForgeJBItem(new Item.Properties(), BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS));
    public static final RegistryObject<Item> MOB_BUCKET = ITEMS.register(
            BucketDefinitions.MOB_BUCKET_ID.getPath(),
            () -> new MBItem(new Item.Properties()));
    public static final RegistryObject<Item> SOURCE_BUCKET = ITEMS.register(
            BucketDefinitions.SOURCE_BUCKET_ID.getPath(),
            () -> new ForgeSBItem(new Item.Properties()));
    public static final RegistryObject<Item> TRASH_BUCKET = ITEMS.register(
            BucketDefinitions.TRASH_BUCKET_ID.getPath(),
            () -> new ForgeTBItem(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
