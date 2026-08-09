package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.ForgeBBItem;
import com.github.crittscott.somebuckets.item.ForgeJBItem;
import com.github.crittscott.somebuckets.item.ForgeTBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.item.ForgeSBItem;
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
    public static final RegistryObject<Item> BIG_BUCKET_64 = ITEMS.register("big_bucket_64",
            () -> new ForgeBBItem(new Item.Properties().stacksTo(1), 64));
    public static final RegistryObject<Item> BIG_BUCKET_8 = ITEMS.register("big_bucket_8",
            () -> new ForgeBBItem(new Item.Properties().stacksTo(1), 8));
    public static final RegistryObject<Item> JUNK_BUCKET = ITEMS.register("junk_bucket",
            () -> new ForgeJBItem(new Item.Properties().stacksTo(1), 9));
    public static final RegistryObject<Item> MOB_BUCKET = ITEMS.register("mob_bucket",
            () -> new MBItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SOURCE_BUCKET = ITEMS.register("source_bucket",
            () -> new ForgeSBItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TRASH_BUCKET = ITEMS.register("trash_bucket",
            () -> new ForgeTBItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
