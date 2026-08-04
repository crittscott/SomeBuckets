package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers item tinting for fluid-container layers and the Mob Bucket's spawn-egg-colored
 * overlays. Layer 0 (metal) is not tinted.
 */
@Mod.EventBusSubscriber(modid = "somebuckets", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientColorHandlers {
    private ClientColorHandlers() {}

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(ClientColorHandlers::bucketTint,
                ModItems.BIG_BUCKET_8.get(), ModItems.BIG_BUCKET_64.get(), ModItems.SOURCE_BUCKET.get());

        event.register(ClientColorHandlers::mobBucketTint, ModItems.MOB_BUCKET.get());
    }

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                ClientFluidColors.clearCache());
    }

    /** Tint function for the two Mob Bucket overlays, taken from the entity's spawn egg. */
    private static int mobBucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex == 0) return -1; // No tint for base layer

        EntityType<?> entityType = NBTUtil.getCurrentEntityType(stack);
        if (entityType == null) return 0x808080; // Gray fallback

        SpawnEggItem spawnEgg = ForgeSpawnEggItem.fromEntityType(entityType);
        if (spawnEgg == null) return 0x808080;

        return tintIndex == 1 ? spawnEgg.getColor(0) : spawnEgg.getColor(1);
    }

    /** Tint function for the overlay (layer1 == tintIndex 1). */
    private static int bucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex != 1) return -1; // no tint on metal or other layers

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode == NBTUtil.Mode.MILK) return 0xFFFFFF;

        if (mode == NBTUtil.Mode.FLUID) {
            FluidStack fs = NBTUtil.getFluidStack(stack);
            if (!fs.isEmpty()) {
                return IClientFluidTypeExtensions.of(fs.getFluid()).getTintColor(fs);
            }
        }
        return -1;
    }
}
