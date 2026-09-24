package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.client.FluidBucketRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Forge Source Bucket item shell providing stack-aware crafting remainder hooks. */
public final class ForgeSBItem extends SBItem {
    /** Creates a Forge Source Bucket. */
    public ForgeSBItem(Properties properties) {
        super(properties);
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
