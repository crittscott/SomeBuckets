package com.github.crittscott.somebuckets.mixin.common;

import com.github.crittscott.somebuckets.item.JBItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tempts a mob with food carried in a held Junk or Trash Bucket, as if the food itself were held. */
@Mixin(TemptGoal.class)
abstract class TemptGoalMixin {
    @Shadow @Final private Ingredient items;

    @Inject(method = "shouldFollow", at = @At("RETURN"), cancellable = true)
    private void somebuckets$followStoredFood(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && JBItem.holdsStoredMatch(entity, items)) cir.setReturnValue(true);
    }
}
