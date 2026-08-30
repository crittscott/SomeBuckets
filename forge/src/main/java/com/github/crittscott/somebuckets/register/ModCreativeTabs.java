package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Registers the mod's creative tab, populated with representative empty and filled bucket variants. */
public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SomeBuckets.MODID);

    public static final RegistryObject<CreativeModeTab> BB_TAB = TABS.register(SomeBuckets.MODID,
            () -> CreativeModeTab.builder()
                    .title(Component.translatable(CreativeBucketCatalog.TAB_TITLE_KEY))
                    .icon(() -> new ItemStack(ModItems.BIG_BUCKET_8.get()))
                    .displayItems((params, output) -> CreativeBucketCatalog.populate(
                            ModItems.BIG_BUCKET_8.get(), ModItems.BIG_BUCKET_64.get(),
                            ModItems.SOURCE_BUCKET.get(), ModItems.JUNK_BUCKET.get(),
                            ModItems.MOB_BUCKET.get(), ModItems.TRASH_BUCKET.get(),
                            output::accept))
                    .build()
    );

    public static void register(IEventBus bus) { TABS.register(bus); }
}
