package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.client.FluidBucketRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Forge finite-bucket item shell providing stack-aware crafting remainder hooks. */
public final class ForgeBBItem extends BBItem {
    /** Creates a Forge finite bucket with the given whole-bucket capacity. */
    public ForgeBBItem(Properties properties, int capacityUnits) {
        super(properties, capacityUnits);
    }

    @Override
    public ItemStack getCraftingRemainder(ItemStack stack) {
        return getUnitRemainder(stack);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(FluidBucketRenderer.createItemExtensions());
    }
}
