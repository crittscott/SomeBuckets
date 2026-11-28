package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Registers and populates the Fabric creative tab. */
public final class FabricCreativeTabs {
    private FabricCreativeTabs() {}

    /** Registers and populates the mod's creative tab. */
    public static void register() {
        Identifier id = SomeBuckets.id(SomeBuckets.MODID);
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable(CreativeBucketCatalog.TAB_TITLE_KEY))
                .icon(() -> new ItemStack(FabricItems.BIG_BUCKET_8))
                .displayItems((parameters, output) -> CreativeBucketCatalog.populate(output::accept))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab);
    }
}
