package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.fuel.FabricFuel;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the same stack-sensitive lava-bucket fuel behavior exposed by Forge's fuel event. */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
    private static void somebuckets$isFuel(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (FabricFuel.isLavaFuel(stack)) callback.setReturnValue(true);
    }

    @Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true)
    private void somebuckets$getBurnDuration(ItemStack stack, CallbackInfoReturnable<Integer> callback) {
        if (FabricFuel.isLavaFuel(stack)) {
            callback.setReturnValue(FluidBucketItem.LAVA_BUCKET_BURN_TIME_TICKS);
        }
    }
}
