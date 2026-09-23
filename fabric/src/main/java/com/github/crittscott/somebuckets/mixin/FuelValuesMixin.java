package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.fuel.BucketFuel;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies stack-sensitive fuel admission and burn duration for fluid bucket items. */
@Mixin(FuelValues.class)
abstract class FuelValuesMixin {
    @Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
    private void somebuckets$isFuel(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stack.getItem() instanceof FluidBucketItem) {
            callback.setReturnValue(BucketFuel.isLavaFuel(stack));
        }
    }

    @Inject(method = "burnDuration", at = @At("HEAD"), cancellable = true)
    private void somebuckets$burnDuration(ItemStack stack,
                                          CallbackInfoReturnable<Integer> callback) {
        if (stack.getItem() instanceof FluidBucketItem) {
            callback.setReturnValue(BucketFuel.isLavaFuel(stack)
                    ? FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS : 0);
        }
    }
}
