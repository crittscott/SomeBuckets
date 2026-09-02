package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Loader-neutral powder-snow cauldron transitions for players and dispensers. */
public final class PowderSnowCauldrons {
    private PowderSnowCauldrons() {}

    /**
     * Moves one powder-snow block from a full powder-snow cauldron at {@code pos} into {@code stack},
     * leaving an empty cauldron.
     *
     * <p>On the server it debits the cauldron, credits the bucket, awards the cauldron-use and
     * item-use stats, fires the filled-bucket criterion for a player, and emits
     * {@link GameEvent#FLUID_PICKUP}; the fill sound plays on both sides.
     *
     * @param level acting level
     * @param pos cauldron position
     * @param face face to authorize against
     * @param stack bucket stack, credited one unit on success
     * @param capacityUnits the bucket tier's powder-snow capacity
     * @param context authorization identity
     * @return {@code true} when the transfer ran; {@code false} without mutation unless the cauldron
     *         is full, the bucket is empty or already in powder-snow mode below {@code capacityUnits},
     *         and protection allows the interaction
     */
    public static boolean take(Level level, BlockPos pos, Direction face, ItemStack stack,
                               int capacityUnits, ProtectionContext context) {
        if (!level.getBlockState(pos).equals(fullPowderState())) return false;

        BucketState.Mode mode = BucketState.getMode(stack);
        int currentUnits = BucketState.getPowderUnits(stack);
        if (mode != BucketState.Mode.NONE
                && (mode != BucketState.Mode.POWDER_SNOW || currentUnits >= capacityUnits)) {
            return false;
        }
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            BucketState.setPowderUnits(stack,
                    (mode == BucketState.Mode.POWDER_SNOW ? currentUnits : 0) + 1);
            complete(level, pos, stack, context, Blocks.CAULDRON.defaultBlockState(), true);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_FILL_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /**
     * Moves one powder-snow block from {@code stack} into an empty cauldron at {@code pos}, filling
     * it to a full powder-snow cauldron.
     *
     * <p>On the server it debits the bucket, sets the cauldron, awards the cauldron-use and item-use
     * stats, and emits {@link GameEvent#FLUID_PLACE}; the empty sound plays on both sides.
     *
     * @param level acting level
     * @param pos cauldron position
     * @param face face to authorize against
     * @param stack bucket stack, debited one unit on success
     * @param context authorization identity
     * @return {@code true} when the transfer ran; {@code false} without mutation unless the target is
     *         an empty cauldron, the bucket is in powder-snow mode with at least one block, and
     *         protection allows the interaction
     */
    public static boolean place(Level level, BlockPos pos, Direction face, ItemStack stack,
                                ProtectionContext context) {
        if (!level.getBlockState(pos).is(Blocks.CAULDRON)) return false;
        if (BucketState.getMode(stack) != BucketState.Mode.POWDER_SNOW
                || BucketState.getPowderUnits(stack) < 1) {
            return false;
        }
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            BucketState.setPowderUnits(stack, BucketState.getPowderUnits(stack) - 1);
            complete(level, pos, stack, context, fullPowderState(), false);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private static boolean mayInteract(Level level, BlockPos pos, Direction face, ItemStack stack,
                                       ProtectionContext context) {
        return Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT,
                pos, face, stack, null);
    }

    private static void complete(Level level, BlockPos pos, ItemStack stack,
                                 ProtectionContext context, BlockState result,
                                 boolean pickup) {
        level.setBlock(pos, result, Block.UPDATE_ALL);
        Player player = context.player();
        if (player != null) {
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            if (pickup && player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, stack);
            }
        }
        level.gameEvent(player, pickup ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, pos);
    }

    private static BlockState fullPowderState() {
        return Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
    }
}
