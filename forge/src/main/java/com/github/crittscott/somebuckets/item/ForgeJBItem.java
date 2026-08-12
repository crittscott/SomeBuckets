package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.client.JBRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Forge item shell that attaches the Junk Bucket's custom stored-item renderer. */
public class ForgeJBItem extends JBItem {
    public ForgeJBItem(Properties properties, int capacity) {
        super(properties, capacity);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return variableMaxStackSize(stack);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(JBRenderer.createItemExtensions());
    }
}
