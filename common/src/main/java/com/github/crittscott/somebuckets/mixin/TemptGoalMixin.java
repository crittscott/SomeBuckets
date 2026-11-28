package com.github.crittscott.somebuckets.mixin;

import com.github.crittscott.somebuckets.item.JBItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Tempts a goal-driven mob, such as a pig or a cow, with food stored in a held Junk or Trash
 * Bucket, as if the food itself were held. No loader has an event or API for what a
 * {@link TemptGoal} accepts, and its item test is fixed when the goal is built.
 */
@Mixin(TemptGoal.class)
abstract class TemptGoalMixin {
    @Shadow @Final private Predicate<ItemStack> items;

    @Inject(method = "shouldFollow", at = @At("RETURN"), cancellable = true)
    private void somebuckets$followStoredFood(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && JBItem.holdsStoredMatch(entity, items)) cir.setReturnValue(true);
    }
}
