package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.platform.FabricFluidColors;
import com.github.crittscott.somebuckets.register.FabricItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/** Fabric client bootstrap for predicates, item tints, fluid colors, and dynamic item rendering. */
public final class SomeBucketsFabricClient implements ClientModInitializer {
    private static final int MISSING_EGG_COLOR = 0x808080;
    private static final int DEFAULT_FLUID_COLOR = 0x4A90E2;

    @Override
    public void onInitializeClient() {
        registerPredicates();
        registerColors();
        FabricFluidContainerModel.registerModels();
        FabricJunkBucketRenderer.registerModel();
        BuiltinItemRendererRegistry.INSTANCE.register(FabricItems.JUNK_BUCKET,
                new FabricJunkBucketRenderer());
        FabricFluidColors.install(fluid -> FabricClientFluidColors.color(fluid, DEFAULT_FLUID_COLOR));
    }

    private static void registerPredicates() {
        ItemProperties.register(FabricItems.BIG_BUCKET_8, FluidBucketItem.CONTENT_PROPERTY,
                (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
        ItemProperties.register(FabricItems.BIG_BUCKET_64, FluidBucketItem.CONTENT_PROPERTY,
                (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
        ItemProperties.register(FabricItems.SOURCE_BUCKET, FluidBucketItem.CONTENT_PROPERTY,
                (stack, level, entity, seed) -> FluidBucketItem.getContentProperty(stack));
        ItemProperties.register(FabricItems.MOB_BUCKET, MBItem.FILLED_PROPERTY,
                (stack, level, entity, seed) -> MBItem.getFilledProperty(stack));
    }

    private static void registerColors() {
        ColorProviderRegistry.ITEM.register(SomeBucketsFabricClient::bucketTint,
                FabricItems.BIG_BUCKET_8, FabricItems.BIG_BUCKET_64, FabricItems.SOURCE_BUCKET);
        ColorProviderRegistry.ITEM.register(SomeBucketsFabricClient::mobTint, FabricItems.MOB_BUCKET);
        ColorProviderRegistry.ITEM.register((stack, tint) -> tint == 1 ? 0x000000 : -1,
                FabricItems.TRASH_BUCKET);
    }

    private static int bucketTint(ItemStack stack, int tintIndex) {
        if (tintIndex != 1) return -1;
        if (NBTUtil.getMode(stack) == NBTUtil.Mode.MILK) return 0xFFFFFF;
        if (NBTUtil.getMode(stack) == NBTUtil.Mode.FLUID) {
            return FabricClientFluidColors.tint(NBTUtil.getStoredFluid(stack));
        }
        return -1;
    }

    private static int mobTint(ItemStack stack, int tintIndex) {
        if (tintIndex == 0) return -1;
        EntityType<?> type = NBTUtil.getCurrentEntityType(stack);
        SpawnEggItem egg = SpawnEggItem.byId(type);
        if (egg == null) return MISSING_EGG_COLOR;
        return egg.getColor(tintIndex - 1);
    }
}
