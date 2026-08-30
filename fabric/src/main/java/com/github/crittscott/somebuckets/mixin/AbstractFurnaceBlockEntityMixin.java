package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.fuel.BucketFuel;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the same stack-sensitive lava-bucket fuel behavior that Forge exposes through
 * {@code FurnaceFuelBurnTimeEvent} and NeoForge through {@code IItemExtension#getBurnTime}.
 *
 * <p>Fabric API's first-party {@code FuelRegistry} is item- and tag-keyed in 1.21.1 and cannot
 * inspect the stack, so it cannot express "only a Big or Huge Bucket that is full of lava burns."
 * A HEAD injection on {@code isFuel} and {@code getBurnDuration} is the narrowest way to gate on
 * {@link BucketFuel#isLavaFuel}. If that per-stack condition were ever dropped,
 * {@code FuelRegistry.INSTANCE.add(item, ticks)} would replace this mixin outright.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
    private static void somebuckets$isFuel(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (BucketFuel.isLavaFuel(stack)) callback.setReturnValue(true);
    }

    @Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true)
    private void somebuckets$getBurnDuration(ItemStack stack, CallbackInfoReturnable<Integer> callback) {
        if (BucketFuel.isLavaFuel(stack)) {
            callback.setReturnValue(FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS);
        }
    }
}
