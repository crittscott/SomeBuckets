package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SomeBuckets.MODID);

    public static final RegistryObject<CreativeModeTab> BB_TAB = TABS.register("somebuckets",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.somebuckets"))
                    .icon(() -> new ItemStack(ModItems.BIG_BUCKET_8.get()))
                    .displayItems((params, output) -> {
                        // BBs
                        output.accept(new ItemStack(ModItems.BIG_BUCKET_8.get()));
                        output.accept(new ItemStack(ModItems.BIG_BUCKET_64.get()));

                        ItemStack wF_8 = new ItemStack(ModItems.BIG_BUCKET_8.get());
                        NBTUtil.setFluidStack(wF_8,
                                new FluidStack(Fluids.WATER, ((BBItem) wF_8.getItem()).getCapacityMb()));
                        output.accept(wF_8);
                        ItemStack wF_64 = new ItemStack(ModItems.BIG_BUCKET_64.get());
                        NBTUtil.setFluidStack(wF_64,
                                new FluidStack(Fluids.WATER, ((BBItem) wF_64.getItem()).getCapacityMb()));
                        output.accept(wF_64);

                        ItemStack lF_8 = new ItemStack(ModItems.BIG_BUCKET_8.get());
                        NBTUtil.setFluidStack(lF_8,
                                new FluidStack(Fluids.LAVA, ((BBItem) lF_8.getItem()).getCapacityMb()));
                        output.accept(lF_8);
                        ItemStack lF_64 = new ItemStack(ModItems.BIG_BUCKET_64.get());
                        NBTUtil.setFluidStack(lF_64,
                                new FluidStack(Fluids.LAVA, ((BBItem) lF_64.getItem()).getCapacityMb()));
                        output.accept(lF_64);

                        ItemStack mF_8 = new ItemStack(ModItems.BIG_BUCKET_8.get());   NBTUtil.setMilkAmount(mF_8,
                                ((BBItem) mF_8.getItem()).getCapacityMb());
                        output.accept(mF_8);
                        ItemStack mF_64 = new ItemStack(ModItems.BIG_BUCKET_64.get()); NBTUtil.setMilkAmount(mF_64,
                                ((BBItem) mF_64.getItem()).getCapacityMb());
                        output.accept(mF_64);

                        ItemStack pF_8 = new ItemStack(ModItems.BIG_BUCKET_8.get());   NBTUtil.setPowderUnits(pF_8,
                                ((BBItem) pF_8.getItem()).getCapacityUnits());
                        output.accept(pF_8);
                        ItemStack pF_64 = new ItemStack(ModItems.BIG_BUCKET_64.get()); NBTUtil.setPowderUnits(pF_64,
                                ((BBItem) pF_64.getItem()).getCapacityUnits());
                        output.accept(pF_64);

                        // Source Bucket variants
                        output.accept(new ItemStack(ModItems.SOURCE_BUCKET.get()));

                        ItemStack sbWater = new ItemStack(ModItems.SOURCE_BUCKET.get());
                        NBTUtil.setFluidStack(sbWater, new FluidStack(Fluids.WATER, 1000));
                        output.accept(sbWater);

                        ItemStack sbLava = new ItemStack(ModItems.SOURCE_BUCKET.get());
                        NBTUtil.setFluidStack(sbLava, new FluidStack(Fluids.LAVA, 1000));
                        output.accept(sbLava);

                        ItemStack sbMilk = new ItemStack(ModItems.SOURCE_BUCKET.get());
                        NBTUtil.setMilkAmount(sbMilk, 1000);
                        output.accept(sbMilk);

                        // other buckets
                        output.accept(new ItemStack(ModItems.JUNK_BUCKET.get()));
                        output.accept(new ItemStack(ModItems.MOB_BUCKET.get()));
                        output.accept(new ItemStack(ModItems.TRASH_BUCKET.get()));
                    })
                    .build()
    );

    public static void register(IEventBus bus) { TABS.register(bus); }
}
