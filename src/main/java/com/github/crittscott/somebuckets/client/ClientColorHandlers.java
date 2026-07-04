package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.FluidData;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import static com.github.crittscott.somebuckets.client.FluidTintHelper.getTintRgb;

/**
 * Registers item tinting for the Big Buckets overlay (layer1).
 * Layer0 (metal) is not tinted.
 */
@Mod.EventBusSubscriber(modid = "somebuckets", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientColorHandlers {
    private ClientColorHandlers() {}

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        // Look up by registry name to avoid touching your main class.
        Item big8  = ForgeRegistries.ITEMS.getValue(new ResourceLocation("somebuckets", "big_bucket_8"));
        Item big64 = ForgeRegistries.ITEMS.getValue(new ResourceLocation("somebuckets", "big_bucket_64"));
        if (big8 != null) {
            event.register(ClientColorHandlers::bucketTint, big8);
        }
        if (big64 != null) {
            event.register(ClientColorHandlers::bucketTint, big64);
        }
    }

    /** Tint function for the overlay (layer1 == tintIndex 1). */
    private static int bucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex != 1) return -1; // no tint on metal or other layers

        String mode = NBTUtil.getMode(stack);
        if ("milk".equals(mode)) return 0xFFFFFF;

        if ("fluid".equals(mode)) {
            FluidStack fs = NBTUtil.getFluidStack(stack);
            if (!fs.isEmpty()) {
                if (fs.getFluid().isSame(Fluids.LAVA) || fs.getFluid().isSame(Fluids.FLOWING_LAVA)) return -1;

                ResourceLocation key = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                int fallback = FluidData.getBarColor(key != null ? key.toString() : "", 0x4A90E2);
                return getTintRgb(fs, fallback);
            }
        }
        return -1;
    }
}
