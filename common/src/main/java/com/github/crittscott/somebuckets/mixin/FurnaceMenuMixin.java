package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the furnace slot and shift-click use the same eligibility rule as ignition. */
@Mixin(AbstractFurnaceMenu.class)
abstract class FurnaceMenuMixin {
    @Inject(method = "isFuel", at = @At("HEAD"), cancellable = true)
    private void somebuckets$isBucketFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof FluidBucketItem) {
            cir.setReturnValue(FluidBucketItem.isLavaFuel(stack));
        }
    }
}
