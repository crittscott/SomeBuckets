package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a Big, Huge, or Source Bucket furnace fuel while it holds at least one unit of lava.
 * Fabric's {@code FuelRegistry} is keyed by item, so it cannot express fuel that depends on the
 * stack's contents.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
    private static void somebuckets$isFuel(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (FluidBucketItem.isLavaFuel(stack)) callback.setReturnValue(true);
    }

    @Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true)
    private void somebuckets$getBurnDuration(ItemStack stack, CallbackInfoReturnable<Integer> callback) {
        if (FluidBucketItem.isLavaFuel(stack)) {
            callback.setReturnValue(FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS);
        }
    }
}
