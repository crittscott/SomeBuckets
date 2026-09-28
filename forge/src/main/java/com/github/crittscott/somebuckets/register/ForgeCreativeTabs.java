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
public final class ForgeCreativeTabs {
    /** Deferred register for the mod's creative tabs. */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SomeBuckets.MODID);

    /** Creative tab containing the bucket items and representative filled variants. */
    public static final RegistryObject<CreativeModeTab> BB_TAB = TABS.register(SomeBuckets.MODID,
            () -> CreativeModeTab.builder()
                    .title(Component.translatable(CreativeBucketCatalog.TAB_TITLE_KEY))
                    .icon(() -> new ItemStack(ForgeItems.BIG_BUCKET_8.get()))
                    .displayItems((params, output) -> CreativeBucketCatalog.populate(
                            ForgeItems.BIG_BUCKET_8.get(), ForgeItems.BIG_BUCKET_64.get(),
                            ForgeItems.SOURCE_BUCKET.get(), ForgeItems.JUNK_BUCKET.get(),
                            ForgeItems.MOB_BUCKET.get(), ForgeItems.TRASH_BUCKET.get(),
                            output::accept))
                    .build()
    );

    /** Attaches creative-tab registration to the mod event bus. */
    public static void register(IEventBus bus) { TABS.register(bus); }
}
