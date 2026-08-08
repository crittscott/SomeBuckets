package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.register.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FuelHandler {
    private static final int LAVA_BUCKET_BURN_TIME_TICKS = 20_000;

    @SubscribeEvent
    public static void onFurnaceFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        ItemStack stack = event.getItemStack();

        // Check if this is one of our bucket types
        boolean isOurBucket = stack.is(ModItems.BIG_BUCKET_8.get()) ||
                stack.is(ModItems.BIG_BUCKET_64.get()) ||
                stack.getItem() instanceof SBItem;

        if (!isOurBucket) return;

        // Only treat as fuel when it contains lava
        if (NBTUtil.getMode(stack) == NBTUtil.Mode.FLUID) {
            FluidStack fluidStack = NBTUtil.getFluidStack(stack);
            if (stack.getItem() instanceof SBItem && !SBPolicy.allows(fluidStack)) return;
            if (!fluidStack.isEmpty() && fluidStack.getFluid() == Fluids.LAVA
                    && fluidStack.getAmount() >= FluidType.BUCKET_VOLUME) {
                // Set burn time for one bucket only (100 items).
                event.setBurnTime(LAVA_BUCKET_BURN_TIME_TICKS);
            }
        }
    }
}
