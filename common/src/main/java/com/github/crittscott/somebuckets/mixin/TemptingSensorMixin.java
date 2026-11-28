package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.item.JBItem;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiPredicate;

/**
 * Tempts a brain-driven mob, such as a goat or an axolotl, with food stored in a held Junk or Trash
 * Bucket, as if the food itself were held. No loader has an event or API for what a
 * {@link TemptingSensor} accepts, and its item test is fixed when the sensor is built.
 */
@Mixin(TemptingSensor.class)
abstract class TemptingSensorMixin {
    @Shadow @Final private BiPredicate<PathfinderMob, ItemStack> temptations;

    @Inject(method = "playerHoldingTemptation", at = @At("RETURN"), cancellable = true)
    private void somebuckets$followStoredFood(PathfinderMob mob, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && JBItem.holdsStoredMatch(player, stack -> temptations.test(mob, stack))) {
            cir.setReturnValue(true);
        }
    }
}
