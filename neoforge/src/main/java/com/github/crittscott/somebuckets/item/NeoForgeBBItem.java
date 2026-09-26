package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.fuel.BucketFuel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.FuelValues;

import javax.annotation.Nullable;

/**
 * NeoForge finite-bucket item shell providing stack-aware crafting remainder hooks and the
 * lava-fuel burn time.
 */
public final class NeoForgeBBItem extends BBItem {
    /** Creates a NeoForge finite bucket with the given whole-bucket capacity. */
    public NeoForgeBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    public ItemStack getCraftingRemainder(ItemStack stack) {
        return getUnitRemainder(stack);
    }

    // NeoForge's item burn-time hook must return a non-negative value; 0 means "not fuel". There is
    // no vanilla furnace-fuel entry for this item to fall back to, so 0 is the correct non-lava case.
    // Lava burns as long as the current fuel values say a vanilla lava bucket does.
    @Override
    public int getBurnTime(ItemStack itemStack, @Nullable RecipeType<?> recipeType, FuelValues fuelValues) {
        return BucketFuel.isLavaFuel(itemStack) ? fuelValues.burnDuration(new ItemStack(Items.LAVA_BUCKET)) : 0;
    }
}
