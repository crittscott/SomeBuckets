package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.FabricBBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

/** Registers and populates the Fabric creative tab. */
public final class FabricCreativeTabs {
    private FabricCreativeTabs() {}

    public static void register() {
        ResourceLocation id = new ResourceLocation(SomeBuckets.MODID, SomeBuckets.MODID);
        CreativeModeTab tab = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.somebuckets"))
                .icon(() -> new ItemStack(FabricItems.BIG_BUCKET_8))
                .displayItems((parameters, output) -> addItems(output))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab);
    }

    private static void addItems(CreativeModeTab.Output output) {
        output.accept(FabricItems.BIG_BUCKET_8);
        output.accept(FabricItems.BIG_BUCKET_64);
        addFiniteVariants(output, FabricItems.BIG_BUCKET_8);
        addFiniteVariants(output, FabricItems.BIG_BUCKET_64);

        output.accept(FabricItems.SOURCE_BUCKET);
        ItemStack sourceWater = new ItemStack(FabricItems.SOURCE_BUCKET);
        NBTUtil.setStoredFluid(sourceWater,
                new StoredFluid(Fluids.WATER, FluidBucketItem.BUCKET_VOLUME_MB, null));
        output.accept(sourceWater);
        ItemStack sourceLava = new ItemStack(FabricItems.SOURCE_BUCKET);
        NBTUtil.setStoredFluid(sourceLava,
                new StoredFluid(Fluids.LAVA, FluidBucketItem.BUCKET_VOLUME_MB, null));
        output.accept(sourceLava);
        ItemStack sourceMilk = new ItemStack(FabricItems.SOURCE_BUCKET);
        NBTUtil.setMilkAmount(sourceMilk, FluidBucketItem.BUCKET_VOLUME_MB);
        output.accept(sourceMilk);

        output.accept(FabricItems.JUNK_BUCKET);
        output.accept(FabricItems.MOB_BUCKET);
        output.accept(FabricItems.TRASH_BUCKET);
    }

    private static void addFiniteVariants(CreativeModeTab.Output output, FabricBBItem item) {
        ItemStack water = new ItemStack(item);
        NBTUtil.setStoredFluid(water, new StoredFluid(Fluids.WATER, item.getCapacityMb(), null));
        output.accept(water);
        ItemStack lava = new ItemStack(item);
        NBTUtil.setStoredFluid(lava, new StoredFluid(Fluids.LAVA, item.getCapacityMb(), null));
        output.accept(lava);
        ItemStack milk = new ItemStack(item);
        NBTUtil.setMilkAmount(milk, item.getCapacityMb());
        output.accept(milk);
        ItemStack powder = new ItemStack(item);
        NBTUtil.setPowderUnits(powder, item.getCapacityUnits());
        output.accept(powder);
    }
}
