package com.github.crittscott.somebuckets.fuel;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adapts the shared bucket-fuel policy to Forge's furnace event. */
@Mod.EventBusSubscriber(modid = SomeBuckets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeFuelEvents {
    private ForgeFuelEvents() {}

    @SubscribeEvent
    public static void onFurnaceFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        if (BucketFuel.isLavaFuel(event.getItemStack())) {
            event.setBurnTime(FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS);
        }
    }
}
