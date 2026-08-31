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
     * Dynamic-name suffixes appended to a bucket's registered description id, one per content kind.
     * Each has a matching {@code item.somebuckets.<bucket><suffix>} entry in {@code en_us.json}.
     */
    String NAME_SUFFIX_WATER = ".water";
    String NAME_SUFFIX_LAVA = ".lava";
    String NAME_SUFFIX_FLUID = ".fluid";
    String NAME_SUFFIX_MILK = ".milk";
    String NAME_SUFFIX_POWDER_SNOW = ".powder_snow";

    /**
     * Evaluates the {@link #CONTENT_PROPERTY} protocol shared by Big, Huge, and Source Bucket
     * models.
     *
     * @param stack bucket stack to inspect
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

    /**
     * Re-targets a hit at a different block position.
     *
     * @param base the original hit
     * @param pos the position to re-target at
     * @return {@code base} re-targeted at {@code pos}, or {@code base} unchanged when {@code pos}
     *         already matches it
     */
    static BlockHitResult withPos(BlockHitResult base, BlockPos pos) {
        return pos.equals(base.getBlockPos()) ? base
                : new BlockHitResult(base.getLocation(), base.getDirection(), pos, base.isInside());
    }

    /**
     * Builds the dynamic display name for a fluid-mode bucket.
     *
     * @param baseKey the bucket's registered description id
     * @param fluid the stored fluid
     * @return a component using the water, lava, or generic-fluid name suffix
     */
    static Component resolveFluidName(String baseKey, StoredFluid fluid) {
        if (fluid.fluid() == Fluids.WATER) {
            return Component.translatable(baseKey + NAME_SUFFIX_WATER);
        } else if (fluid.fluid() == Fluids.LAVA) {
            return Component.translatable(baseKey + NAME_SUFFIX_LAVA);
        } else {
            return Component.translatable(baseKey + NAME_SUFFIX_FLUID,
                    BucketOperations.get().fluidDisplayName(fluid));
        }
    }

    /**
     * Clears an assigned bucket on a sneak-use against air.
     *
     * @param level acting level; the mutation runs on the server only
     * @param player acting player
     * @param stack the bucket stack
     * @param airHit the caller's own {@code ClipContext.Fluid.NONE} raytrace, shared with
     *               {@link #tryCrossHandTransfer}
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
     * Transfers content with whatever the other hand holds, deliberately restricted to right-clicking
     * air: a targeted block means the player expects the bucket to act on that block instead.
     *
     * @param level acting level
     * @param player acting player
     * @param hand hand holding the bucket
     * @param stack the bucket stack
     * @param airHit the caller's own {@code ClipContext.Fluid.NONE} raytrace
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
