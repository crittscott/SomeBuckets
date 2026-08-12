package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.item.VariableStackItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives {@link VariableStackItem}s the NBT-sensitive max-stack-size Forge gets from IForgeItem. */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "getMaxStackSize", at = @At("HEAD"), cancellable = true)
    private void somebuckets$getMaxStackSize(CallbackInfoReturnable<Integer> callback) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.getItem() instanceof VariableStackItem variableStackItem) {
            callback.setReturnValue(variableStackItem.variableMaxStackSize(self));
        }
    }
}
