package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fluids.FluidStack;

/**
 * Item tint and color-cache-reload wiring delegated by {@link ClientSetup}. Fluid-container
 * content, the Trash Bucket void, and Mob Bucket overlays are tinted; layer 0 metal remains
 * unchanged.
 */
final class ClientColorHandlers {
    private static final int MISSING_EGG_COLOR = 0xFF808080;

    private ClientColorHandlers() {}

    static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(ClientColorHandlers::bucketTint,
                ModItems.BIG_BUCKET_8.get(), ModItems.BIG_BUCKET_64.get(), ModItems.SOURCE_BUCKET.get());
        // Registered items above must be exactly the FluidBucketItem implementations.

        event.register(ClientColorHandlers::mobBucketTint, ModItems.MOB_BUCKET.get());
        event.register(ClientColorHandlers::trashBucketTint, ModItems.TRASH_BUCKET.get());
    }

    static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            ClientFluidColors.clearCache();
            BucketMouth.clearCache();
            JunkBucketRenderData.clearCache();
        });
    }

    // Tint the white content mask pure black while leaving the bucket metal unchanged.
    private static int trashBucketTint(ItemStack stack, int tintIndex) {
        return tintIndex == 1 ? 0xFF000000 : -1;
    }

    // Tint the two Mob Bucket overlays from the entity's spawn egg.
    private static int mobBucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex == 0) return -1; // No tint for base layer

        EntityType<?> entityType = NBTUtil.getCurrentEntityType(stack);
        if (entityType == null) return MISSING_EGG_COLOR;

        SpawnEggItem spawnEgg = ForgeSpawnEggItem.fromEntityType(entityType);
        if (spawnEgg == null) return MISSING_EGG_COLOR;

        int rgb = tintIndex == 1 ? spawnEgg.getColor(0) : spawnEgg.getColor(1);
        return 0xFF000000 | rgb;
    }

    // Tint the content overlay at tint index 1.
    private static int bucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex != 1) return -1; // no tint on metal or other layers

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode == NBTUtil.Mode.MILK) return 0xFFFFFFFF;

        if (mode == NBTUtil.Mode.FLUID) {
            FluidStack fs = ForgeFluidStacks.get(stack);
            if (!fs.isEmpty()) {
                return IClientFluidTypeExtensions.of(fs.getFluid()).getTintColor(fs);
            }
        }
        return -1;
    }
}
