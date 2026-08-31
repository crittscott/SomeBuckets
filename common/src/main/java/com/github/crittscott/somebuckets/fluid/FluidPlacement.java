package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;

/**
 * Vanilla-style placement retained for the fixed water output required by aquatic Mob Bucket
 * release, plus shared bucket-sound and evaporation-pitch helpers. Arbitrary Big, Huge, and Source
 * Bucket fluid output is loader-owned so Forge and Fabric fluid metadata remains authoritative.
 *
 * <p>The position that would actually be changed is checked as a fluid edit, so a neighbor reached
 * by fall-through is authorized in its own right rather than on the strength of the clicked block.
 *
 * <p>This owns the world transaction only. A {@code true} return means the world accepted one
 * unit; the caller decides whether to charge the bucket for it.
 */
public final class FluidPlacement {
    private static final int EVAPORATION_PARTICLE_COUNT = 8;

    private static final float HISS_PITCH_BASE = 2.6F;
    private static final float HISS_PITCH_VARIANCE = 0.8F;

    private FluidPlacement() {}

    /**
     * Reports whether placing {@code fluid} in {@code level} evaporates instead of forming a block,
     * matching vanilla's ultra-warm-dimension water rule. Loaders with a fluid-specific vaporization
     * policy (Forge's {@code FluidType#isVaporizedOnPlacement}) should defer to it instead of this
     * fallback.
     *
     * @param level level the placement would occur in
     * @param fluid fluid being placed
     * @return {@code true} when the dimension is ultra-warm and the fluid is water
     */
    public static boolean evaporatesInUltraWarm(Level level, Fluid fluid) {
        return level.dimensionType().ultraWarm() && fluid.defaultFluidState().is(FluidTags.WATER);
    }

    /**
     * Resolves the position that would actually be written by placing {@code fluid} at {@code pos}
     * along {@code face}.
     *
     * <p>Read-only: does not check protection or touch the world.
     *
     * @param level level to inspect
     * @param player player used for liquid-container placement checks, or {@code null}
     * @param pos clicked position
     * @param face clicked face, used for fall-through to the neighbor
     * @param mayFallThrough whether an invalid clicked position may resolve to the neighbor
     * @param fluid fluid being placed
     * @return {@code pos} itself when it is air, replaceable, or a liquid-container block that accepts
     *         the fluid; the neighbor along {@code face} when fall-through is allowed and the neighbor
     *         qualifies; otherwise {@code pos} unchanged, so the caller always gets a single position
     *         to report even when the eventual placement attempt will fail there
     */
    public static BlockPos resolveTarget(Level level, @Nullable Player player, BlockPos pos,
                                         Direction face, boolean mayFallThrough, Fluid fluid) {
        BlockState state = level.getBlockState(pos);
        boolean replaceable = state.canBeReplaced(fluid);
        boolean container = state.getBlock() instanceof LiquidBlockContainer lbc
                && lbc.canPlaceLiquid(player, level, pos, state, fluid);

        if (!state.isAir() && !replaceable && !container) {
            if (!mayFallThrough) return pos;
            BlockPos neighbor = pos.relative(face);
            BlockState neighborState = level.getBlockState(neighbor);
            boolean neighborReplaceable = neighborState.canBeReplaced(fluid);
            boolean neighborContainer = neighborState.getBlock() instanceof LiquidBlockContainer nlbc
                    && nlbc.canPlaceLiquid(player, level, neighbor, neighborState, fluid);
            return neighborState.isAir() || neighborReplaceable || neighborContainer ? neighbor : pos;
        }
        return pos;
    }

    /**
     * Places one bucket volume of {@code fluid} — always {@link Fluids#WATER}, the only fluid this
     * fixed-output path serves — at {@code pos} along {@code face} using vanilla bucket target and
     * replacement rules.
     *
     * <p>If {@code mayFallThrough} is true, an invalid clicked position may resolve once to the
     * neighbor along {@code face}; it does not make an otherwise invalid destination placeable. The
     * resolved position is protected as a fluid edit before mutation, and additionally as a block
     * edit when placement would destroy an existing replaceable block, so a claim that grants fluid
     * editing but withholds block breaking still stops the destruction. Ultra-warm evaporation is
     * handled here; every other outcome — placing, waterlogging, or destroying a replaceable block
     * with drops, plus the empty sound — is delegated to {@link net.minecraft.world.item.BucketItem
     * BucketItem}'s own {@code emptyContents}. The fluid-place game event is emitted here to match
     * {@code BucketItem#use}. The caller remains responsible for debiting any finite container and
     * awarding item-use accounting.
     *
     * @param level acting level
     * @param context authorization identity
     * @param stack the bucket stack driving the placement
     * @param pos clicked position
     * @param face clicked face
     * @param mayFallThrough whether an invalid clicked position may resolve once to the neighbor
     * @param fluid fluid to place; only {@link Fluids#WATER} is served
     * @return {@code true} when the world transaction completed; {@code false} leaves the world
     *         unchanged
     */
    public static boolean emptyContents(Level level, ProtectionContext context, ItemStack stack, BlockPos pos,
                                        Direction face, boolean mayFallThrough, Fluid fluid) {
        if (fluid != Fluids.WATER) return false;

        pos = resolveTarget(level, context.player(), pos, face, mayFallThrough, fluid);
        BlockState state = level.getBlockState(pos);
        boolean replaceable = state.canBeReplaced(fluid);
        boolean container = state.getBlock() instanceof LiquidBlockContainer lbc
                && lbc.canPlaceLiquid(context.player(), level, pos, state, fluid);

        if (!state.isAir() && !replaceable && !container) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos, face, stack, null)) return false;

        boolean evaporates = evaporatesInUltraWarm(level, fluid);
        boolean destroysBlock = !container && !evaporates
                && !state.isAir() && replaceable && !state.liquid();
        if (destroysBlock
                && !Protections.mayAct(level, context, ProtectionAction.BLOCK_EDIT, pos, face, stack, null)) {
            return false;
        }

        if (evaporates) {
            evaporate(level, context.player(), pos);
            return true;
        }

        if (!((BucketItem) Items.WATER_BUCKET).emptyContents(context.player(), level, pos, null)) return false;
        level.gameEvent(context.player(), GameEvent.FLUID_PLACE, pos);
        return true;
    }

    /**
     * Plays vanilla's ultra-warm evaporation feedback at {@code pos}: the extinguish hiss (server
     * authoritative, so a client-predicting caller stays silent) and a burst of large smoke from
     * {@link ServerLevel}.
     *
     * @param level acting level
     * @param player player the sound is attributed to, or {@code null}
     * @param pos position the feedback plays at
     */
    public static void evaporate(Level level, @Nullable Player player, BlockPos pos) {
        if (!level.isClientSide) {
            level.playSound(player, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                    hissPitch(level.random));
        }
        if (level instanceof ServerLevel serverLevel) {
            for (int i = 0; i < EVAPORATION_PARTICLE_COUNT; i++) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        pos.getX() + level.random.nextDouble(),
                        pos.getY() + level.random.nextDouble(),
                        pos.getZ() + level.random.nextDouble(),
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /**
     * Selects a bucket sound, preferring a registered loader-specific sound over the vanilla
     * water/lava fallback for the requested direction.
     *
     * @param registeredSound custom sound supplied by the loader, or {@code null} to use a fallback
     * @param lava whether the fallback is the lava-specific sound
     * @param filling whether the operation fills rather than empties a bucket
     * @return {@code registeredSound} when present, otherwise the matching vanilla bucket sound
     */
    public static SoundEvent resolveBucketSound(@Nullable SoundEvent registeredSound,
                                                boolean lava, boolean filling) {
        if (registeredSound != null) return registeredSound;
        if (filling) return lava ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL;
        return lava ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
    }

    /**
     * Computes the shared "raspy hiss" pitch for the vanilla evaporation sound and its Trash Bucket
     * reuse.
     *
     * @param random randomness source for the pitch variance
     * @return a randomized pitch around the evaporation-hiss base
     */
    public static float hissPitch(RandomSource random) {
        return HISS_PITCH_BASE + (random.nextFloat() - random.nextFloat()) * HISS_PITCH_VARIANCE;
    }
}
