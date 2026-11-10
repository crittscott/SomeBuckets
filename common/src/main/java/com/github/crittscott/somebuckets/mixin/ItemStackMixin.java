package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.item.SomeBucketItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Admits bucket state after persistent and network codecs construct a stack from its patch. */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "<init>(Lnet/minecraft/core/Holder;ILnet/minecraft/core/component/DataComponentPatch;)V",
            at = @At("RETURN"))
    private void somebuckets$admit(Holder<Item> item, int count, DataComponentPatch patch, CallbackInfo ci) {
        SomeBucketItem.admit((ItemStack) (Object) this);
    }
}
