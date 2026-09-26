package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.ForgeBBItem;
import com.github.crittscott.somebuckets.item.ForgeJBItem;
import com.github.crittscott.somebuckets.item.ForgeSBItem;
import com.github.crittscott.somebuckets.item.ForgeTBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers the mod's bucket items and diagnostic fluid-model probe. */
public class ModItems {
    /** Deferred register for all mod items. */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SomeBuckets.MODID);

    /** Huge Bucket registry object. */
    public static final RegistryObject<Item> BIG_BUCKET_64 = ITEMS.register(
            BucketDefinitions.HUGE_BUCKET_ID.getPath(),
            () -> new ForgeBBItem(itemProperties(BucketDefinitions.HUGE_BUCKET_ID),
                    BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS));
    /** Big Bucket registry object. */
    public static final RegistryObject<Item> BIG_BUCKET_8 = ITEMS.register(
            BucketDefinitions.BIG_BUCKET_ID.getPath(),
            () -> new ForgeBBItem(itemProperties(BucketDefinitions.BIG_BUCKET_ID),
                    BucketDefinitions.BIG_BUCKET_CAPACITY_UNITS));
    /** Junk Bucket registry object. */
    public static final RegistryObject<Item> JUNK_BUCKET = ITEMS.register(
            BucketDefinitions.JUNK_BUCKET_ID.getPath(),
            () -> new ForgeJBItem(itemProperties(BucketDefinitions.JUNK_BUCKET_ID),
                    BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS));
    /** Mob Bucket registry object. */
    public static final RegistryObject<Item> MOB_BUCKET = ITEMS.register(
            BucketDefinitions.MOB_BUCKET_ID.getPath(),
            () -> new MBItem(itemProperties(BucketDefinitions.MOB_BUCKET_ID)));
    /** Source Bucket registry object. */
    public static final RegistryObject<Item> SOURCE_BUCKET = ITEMS.register(
            BucketDefinitions.SOURCE_BUCKET_ID.getPath(),
            () -> new ForgeSBItem(itemProperties(BucketDefinitions.SOURCE_BUCKET_ID)));
    /** Trash Bucket registry object. */
    public static final RegistryObject<Item> TRASH_BUCKET = ITEMS.register(
            BucketDefinitions.TRASH_BUCKET_ID.getPath(),
            () -> new ForgeTBItem(itemProperties(BucketDefinitions.TRASH_BUCKET_ID)));

    private static Item.Properties itemProperties(ResourceLocation id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
    }

    /** Attaches item registration to the mod event bus. */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
