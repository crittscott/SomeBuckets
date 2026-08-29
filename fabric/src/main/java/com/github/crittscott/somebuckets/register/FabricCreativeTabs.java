package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Registers and populates the Fabric creative tab. */
public final class FabricCreativeTabs {
    private FabricCreativeTabs() {}

    public static void register() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, SomeBuckets.MODID);
        CreativeModeTab tab = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.somebuckets"))
                .icon(() -> new ItemStack(FabricItems.BIG_BUCKET_8))
                .displayItems((parameters, output) -> addItems(output))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab);
    }

    private static void addItems(CreativeModeTab.Output output) {
        CreativeBucketCatalog.populate(
                FabricItems.BIG_BUCKET_8, FabricItems.BIG_BUCKET_64,
                FabricItems.SOURCE_BUCKET, FabricItems.JUNK_BUCKET,
                FabricItems.MOB_BUCKET, FabricItems.TRASH_BUCKET,
                output::accept);
    }
}
