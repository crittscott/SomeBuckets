package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.item.SomeBucketItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives Some Buckets items a max stack size that depends on the stack's contents. Vanilla's stack
 * size is fixed per item, and Fabric has no stack-sensitive hook for it.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void somebuckets$getMaxStackSize(CallbackInfoReturnable<Integer> callback) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.getItem() instanceof SomeBucketItem bucket) {
            callback.setReturnValue(bucket.variableMaxStackSize(self));
        }
    }
}
