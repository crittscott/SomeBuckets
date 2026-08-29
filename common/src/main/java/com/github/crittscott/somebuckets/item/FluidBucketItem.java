package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Marker for the mod's single-fluid-container bucket items (Big, Huge, and Source Bucket) and a
 * shared home for the behavior and item-model state protocol they have in common.
 */
public interface FluidBucketItem {
    ResourceLocation CONTENT_PROPERTY =
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "bb_content");
    float CONTENT_EMPTY = 0.0F;
    float CONTENT_FLUID = 0.1F;
    float CONTENT_MILK = 0.2F;
    float CONTENT_POWDER_SNOW = 0.3F;
    int DRINK_DURATION_TICKS = 32;
    int BUCKET_VOLUME_MB = 1_000;
    int LAVA_BUCKET_BURN_TIME_TICKS = 20_000;

    /**
     * Evaluates the {@link #CONTENT_PROPERTY} protocol shared by Big, Huge, and Source Bucket
     * models.
     *
     * @return exactly one of {@link #CONTENT_EMPTY}, {@link #CONTENT_FLUID},
     *         {@link #CONTENT_MILK}, or {@link #CONTENT_POWDER_SNOW}
     */
    static float getContentProperty(ItemStack stack) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        switch (mode) {
            case FLUID -> {
                return NBTUtil.getStoredFluid(stack).isEmpty() ? CONTENT_EMPTY : CONTENT_FLUID;
            }
            case MILK -> {
                return CONTENT_MILK;
            }
            case POWDER_SNOW -> {
                return CONTENT_POWDER_SNOW;
            }
        }
        return CONTENT_EMPTY;
    }

    /** {@code base} re-targeted at {@code pos}, or {@code base} unchanged if {@code pos} matches it. */
    static BlockHitResult withPos(BlockHitResult base, BlockPos pos) {
        return pos.equals(base.getBlockPos()) ? base
                : new BlockHitResult(base.getLocation(), base.getDirection(), pos, base.isInside());
    }

    /** The water/lava/generic-fluid dynamic-name suffix shared by every fluid-mode bucket. */
    static Component resolveFluidName(String baseKey, StoredFluid fluid) {
        if (fluid.fluid() == Fluids.WATER) {
            return Component.translatable(baseKey + ".water");
        } else if (fluid.fluid() == Fluids.LAVA) {
            return Component.translatable(baseKey + ".lava");
        } else {
            return Component.translatable(baseKey + ".fluid", BucketOperations.get().fluidDisplayName(fluid));
        }
    }

    /**
     * Shift-RC on air clears an assigned bucket. {@code airHit} is the caller's own
     * {@code ClipContext.Fluid.NONE} raytrace so this shares one raytrace with
     * {@link #tryCrossHandTransfer}.
     *
     * @return {@code true} iff the interaction was handled (the bucket had content to clear)
     */
    static boolean tryShiftClear(Level level, Player player, ItemStack stack, HitResult airHit) {
        if (!player.isShiftKeyDown()) return false;
        if (airHit.getType() != HitResult.Type.MISS) return false;
        if (NBTUtil.getMode(stack) == NBTUtil.Mode.NONE) return false;

        if (!level.isClientSide) NBTUtil.clearBucket(stack);
        level.playSound(player, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS,
                1.0f, 1.0f);
        return true;
    }

    /**
     * Cross-hand transfer with whatever the other hand holds, deliberately restricted to
     * right-clicking air: a targeted block means the player expects the bucket to act on that
     * block instead. {@code airHit} is the caller's own {@code ClipContext.Fluid.NONE} raytrace.
     *
     * @return {@code true} iff a transfer occurred
     */
    static boolean tryCrossHandTransfer(Level level, Player player, InteractionHand hand, ItemStack stack,
                                         HitResult airHit) {
        if (airHit.getType() != HitResult.Type.MISS) return false;
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        if (otherStack.isEmpty()) return false;
        return BucketOperations.get().tryHeldTransfer(
                level, player, hand, stack, otherHand, otherStack);
    }
}
