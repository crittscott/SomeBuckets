package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.client.JBRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Forge item shell that attaches the Trash Bucket's custom stored-item renderer. */
public final class ForgeTBItem extends TBItem {
    public ForgeTBItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(JBRenderer.createItemExtensions());
    }
}
