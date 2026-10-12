package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.register.ForgeItems;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * Item tints, the client fluid facts, and the resource-reload listener delegated by
 * {@link ForgeClientSetup}. Fluid-container content, the Trash Bucket void, and Mob Bucket overlays
 * are tinted; layer 0 metal remains unchanged.
 */
final class ForgeClientColorHandlers {
    private ForgeClientColorHandlers() {}

    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(ForgeClientColorHandlers::bucketTint,
                ForgeItems.BIG_BUCKET_8.get(), ForgeItems.BIG_BUCKET_64.get(), ForgeItems.SOURCE_BUCKET.get());
        event.register(ForgeClientColorHandlers::mobBucketTint, ForgeItems.MOB_BUCKET.get());
        event.register(ForgeClientColorHandlers::trashBucketTint, ForgeItems.TRASH_BUCKET.get());
    }

    static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
                ClientPlatform.reloadResources());
    }

    /** The Forge client fluid type's still texture and stack-aware tint for {@code fluid}. */
    static ClientPlatform.FluidFacts fluidFacts(StoredFluid fluid) {
        FluidStack stack = ForgeFluidStacks.of(fluid);
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.fluid());
        return new ClientPlatform.FluidFacts(extensions.getStillTexture(stack), extensions.getTintColor(stack));
    }

    // Tint the white content mask pure black while leaving the bucket metal unchanged.
    private static int trashBucketTint(ItemStack stack, int tintIndex) {
        return tintIndex == 1 ? 0x000000 : -1;
    }

    // Tint the two Mob Bucket overlays from the entity's spawn egg.
    private static int mobBucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex == 0) return -1;
        return ClientPlatform.mobBucketTint(BucketState.getCurrentEntityType(stack), tintIndex - 1);
    }

    // Tint the content overlay at tint index 1.
    private static int bucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex != 1) return -1;

        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode == BucketState.Mode.MILK) return 0xFFFFFF;

        if (mode == BucketState.Mode.FLUID) {
            FluidStack fs = ForgeFluidStacks.of(BucketState.getStoredFluid(stack));
            if (!fs.isEmpty()) {
                return IClientFluidTypeExtensions.of(fs.getFluid()).getTintColor(fs);
            }
        }
        return -1;
    }
}
