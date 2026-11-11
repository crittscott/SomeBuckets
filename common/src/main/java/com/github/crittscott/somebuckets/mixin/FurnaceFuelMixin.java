package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class FurnaceFuelMixin {
    @Unique private static final int SOMEBUCKETS_FUEL_SLOT = 1;

    /** Resolve the vanilla lava bucket's cooking component in this furnace's own context. */
    @ModifyVariable(method = {"getBurnDuration", "getSpeedMultiplier"}, at = @At("HEAD"), argsOnly = true)
    private ItemStack somebuckets$cookingFuel(ItemStack stack) {
        if (!(stack.getItem() instanceof FluidBucketItem)) return stack;
        return FluidBucketItem.isLavaFuel(stack) ? new ItemStack(Items.LAVA_BUCKET) : ItemStack.EMPTY;
    }

    /** Expose the same stack-dependent eligibility to the sided inventory used by hoppers. */
    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void somebuckets$canInsertFuel(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == SOMEBUCKETS_FUEL_SLOT && stack.getItem() instanceof FluidBucketItem) {
            cir.setReturnValue(FluidBucketItem.isLavaFuel(stack));
        }
    }

    /** Returns the lava bucket's stack-dependent remainder after the furnace accepts it as fuel. */
    @Inject(method = "consumeFuel", at = @At("HEAD"), cancellable = true)
    private static void somebuckets$consumeBucketFuel(ServerLevel level, BlockPos pos,
                                                       NonNullList<ItemStack> items, ItemStack fuel,
                                                       CallbackInfo ci) {
        if (fuel.getCount() != 1 || !(fuel.getItem() instanceof FluidBucketItem bucket)) return;
        items.set(SOMEBUCKETS_FUEL_SLOT, bucket.getUnitRemainder(fuel));
        ci.cancel();
    }
}
