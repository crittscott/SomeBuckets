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
     * One bucket volume of the source fluid at {@code pos}, or {@link StoredFluid#EMPTY} when the
     * block is not a {@link BucketPickup} source. Nothing is changed. No variant payload is carried:
     * a vanilla {@code BucketPickup} block never exposes one.
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
     * Records vanilla bucket-pickup observability after the caller has stored the acquired content:
     * the item-use statistic and the filled-bucket criterion. Client prediction and automation
     * (a {@code null} player) have no player-side accounting.
     */
    public static void completePlayerPickup(Level level, @Nullable Player player, ItemStack filledStack) {
        if (level.isClientSide || player == null) return;
        player.awardStat(Stats.ITEM_USED.get(filledStack.getItem()));
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, filledStack);
        }
    }
}
