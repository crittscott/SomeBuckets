package com.github.crittscott.somebuckets.mixin.common;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;
import java.util.UUID;

/** Reads the player an item entity was dropped for, which vanilla pickup alone may collect. */
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
    @Nullable
    @Accessor("target")
    UUID somebuckets$getTarget();
}
