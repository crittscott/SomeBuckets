package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Marker for the mod's single-fluid-container bucket items (Big, Huge, and Source Bucket) and a
 * shared home for the behavior they have in common.
 */
public interface FluidBucketItem {
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
     * Reports whether a hit targets a block the bucket interacts with as a block, from
     * {@code useOn}: an exposed loader fluid store, or any cauldron when {@code includeCauldrons}.
     * Vanilla dispatch posts the loader's block-interaction event before {@code useOn}, which is
     * where claim mods decide whether a player may use a block.
     *
     * @param hit candidate hit; a miss is never a block target
     * @param includeCauldrons whether cauldrons count as block targets
     * @return {@code true} when the hit's block is served from {@code useOn}
     */
    static boolean isBlockTarget(Level level, HitResult hit, boolean includeCauldrons) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = blockHit.getBlockPos();
        return BucketOperations.get().hasBlockStorage(level, pos, blockHit.getDirection())
                || (includeCauldrons && level.getBlockState(pos).getBlock() instanceof AbstractCauldronBlock);
    }

    /**
     * Converts a hit on a block target (see {@link #isBlockTarget}) into a miss, so {@code use}
     * leaves that block to {@code useOn}.
     *
     * @return {@code hit} unchanged, or a miss at the same location
     */
    static BlockHitResult withoutBlockTarget(Level level, BlockHitResult hit, boolean includeCauldrons) {
        return isBlockTarget(level, hit, includeCauldrons)
                ? BlockHitResult.miss(hit.getLocation(), hit.getDirection(), hit.getBlockPos()) : hit;
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
        if (BucketState.getMode(stack) == BucketState.Mode.NONE) return false;

        if (!level.isClientSide) BucketState.clearBucket(stack);
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

    /**
     * Completes a milk drink of one bucket volume through the stack's vanilla milk
     * {@link DataComponents#CONSUMABLE} component, which {@code BucketState} keeps present exactly in
     * milk mode: its sound and particles, drink game event, statistic, criterion, and effect clearing
     * under the loader's milk-cure rules. The component consumes a detached copy so the bucket itself
     * is never shrunk; a finite bucket instead drains one unit on the server.
     *
     * @param stack the milk-mode bucket
     * @param level acting level; the bucket is drained on the server only
     * @param user the drinking entity
     * @param drain {@code true} to remove one bucket volume (finite Big or Huge Bucket),
     *              {@code false} for an infinite Source Bucket
     */
    static void finishMilkDrink(ItemStack stack, Level level, LivingEntity user, boolean drain) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return;
        consumable.onConsume(level, user, stack.copy());
        if (drain && !level.isClientSide) {
            BucketState.drainFiniteContent(stack, BUCKET_VOLUME_MB);
        }
    }
}
