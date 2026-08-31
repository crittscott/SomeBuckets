package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * Loader-neutral world pickup of one bucket volume through vanilla's {@link BucketPickup} contract:
 * a source block is removed, a waterlogged block keeps itself and loses only its fluid, and a block
 * that refuses pickup keeps its fluid. Modern modded source blocks implement {@link BucketPickup}
 * directly, so no loader fluid-capability wrapper is involved.
 *
 * <p>{@link #sourceAt} is a read-only query of what one unit would yield. A caller decides whether
 * that content is acceptable, checks protection, and only then calls {@link #take}. {@code take}
 * owns the world transaction, the fill sound, and the {@link GameEvent#FLUID_PICKUP} event; the
 * caller supplies the loader-resolved {@code fillSound} and records the acquired content itself.
 */
public final class WorldFluidPickup {
    private WorldFluidPickup() {}

    /**
     * Reports what one bucket volume of world pickup at {@code pos} would yield, without changing
     * anything. No variant payload is carried: a vanilla {@code BucketPickup} block never exposes
     * one.
     *
     * @param level level to query
     * @param pos block position to inspect
     * @return one bucket volume of the source fluid at {@code pos}, or {@link StoredFluid#EMPTY} when
     *         the block is not a {@link BucketPickup} source
     */
    public static StoredFluid sourceAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup) || !state.getFluidState().isSource()) {
            return StoredFluid.EMPTY;
        }
        Fluid fluid = state.getFluidState().getType();
        return fluid == Fluids.EMPTY ? StoredFluid.EMPTY
                : new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB, null);
    }

    /**
     * Removes one bucket volume of {@code expected} from the block at {@code pos} through its own
     * {@link BucketPickup#pickupBlock} contract, then plays {@code fillSound} and emits the
     * fluid-pickup game event. The world changes on the server only; the client predicts acceptance.
     *
     * @param level acting level
     * @param pos block position to draw from
     * @param expected fluid the caller requires; a different world fluid is rejected
     * @param player acting player, or {@code null} for automation
     * @param fillSound loader-resolved fill sound to play on success
     * @return {@code true} when the block gave up a unit, or the client predicted it; {@code false}
     *         leaves the world unchanged
     */
    public static boolean take(Level level, BlockPos pos, StoredFluid expected, @Nullable Player player,
                               SoundEvent fillSound) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup pickup) || !state.getFluidState().isSource()
                || !state.getFluidState().getType().isSame(expected.fluid())) return false;
        if (!level.isClientSide && pickup.pickupBlock(player, level, pos, state).isEmpty()) return false;
        if (!level.isClientSide) {
            level.playSound(null, pos, fillSound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        return true;
    }

    /**
     * Removes the {@link BucketPickup} block at {@code pos} through its own
     * {@link BucketPickup#pickupBlock} contract, then plays {@code fillSound} and emits the
     * fluid-pickup game event. This is the non-fluid counterpart of {@link #take}: powder snow is a
     * {@code BucketPickup} block with no fluid state. The block is removed on the server only; the
     * client predicts acceptance and still plays the predicted sound and game event.
     *
     * @param level acting level
     * @param pos block position to remove
     * @param player acting player, or {@code null} for automation
     * @param fillSound fill sound to play on success
     * @return {@code true} when the block gave up its pickup stack, or the client predicted it;
     *         {@code false} leaves the world unchanged
     */
    public static boolean takeBlock(Level level, BlockPos pos, @Nullable Player player, SoundEvent fillSound) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup pickup)) return false;
        if (!level.isClientSide && pickup.pickupBlock(player, level, pos, state).isEmpty()) return false;
        level.playSound(player, pos, fillSound, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        return true;
    }

    /**
     * Records vanilla bucket-pickup observability after the caller has stored the acquired content:
     * the item-use statistic and the filled-bucket criterion.
     *
     * @param level acting level; client prediction is a no-op
     * @param player acting player; a {@code null} player (automation) is a no-op
     * @param filledStack the now-filled bucket stack, used to key the statistic and criterion
     */
    public static void completePlayerPickup(Level level, @Nullable Player player, ItemStack filledStack) {
        if (level.isClientSide || player == null) return;
        player.awardStat(Stats.ITEM_USED.get(filledStack.getItem()));
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, filledStack);
        }
    }
}
