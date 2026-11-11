package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class FurnaceFuelMixin {
    @Shadow @Final protected static int SLOT_FUEL;

    /** Returns the lava bucket's stack-dependent remainder after the furnace accepts it as fuel. */
    @Inject(method = "consumeFuel", at = @At("HEAD"), cancellable = true)
    private static void somebuckets$consumeBucketFuel(NonNullList<ItemStack> items, ItemStack fuel, CallbackInfo ci) {
        if (fuel.getCount() != 1 || !(fuel.getItem() instanceof FluidBucketItem bucket)
                || !FluidBucketItem.isLavaFuel(fuel)) {
            return;
        }
        items.set(SLOT_FUEL, bucket.getUnitRemainder(fuel));
        ci.cancel();
    }
}
