package com.github.crittscott.somebuckets.mixin.common;

import com.github.crittscott.somebuckets.item.JBItem;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tempts a brain-driven mob, such as a goat or an axolotl, with food carried in a held Junk or
 * Trash Bucket, as if the food itself were held. Neither loader has an event or API for what a
 * {@link TemptingSensor} accepts, and its ingredient is fixed at sensor construction.
 */
@Mixin(TemptingSensor.class)
abstract class TemptingSensorMixin {
    @Shadow @Final private Ingredient temptations;

    @Inject(method = "playerHoldingTemptation", at = @At("RETURN"), cancellable = true)
    private void somebuckets$followStoredFood(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && JBItem.holdsStoredMatch(player, temptations)) cir.setReturnValue(true);
    }
}
