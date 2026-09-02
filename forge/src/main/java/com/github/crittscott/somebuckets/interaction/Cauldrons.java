package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Vanilla water and lava cauldron transitions for Big, Huge, and Source Buckets: block-state
 * changes, protection, sound, and stat/criterion accounting. Bucket state is edited through
 * {@link BucketState} directly — a vanilla cauldron is not modded fluid storage, so no fluid
 * capability is involved, matching the loader-neutral {@link PowderSnowCauldrons}. A finite Big or
 * Huge Bucket is credited or debited one unit; a Source Bucket is left unchanged (an empty one is
 * assigned by {@code SBFluidLogic}). The Big Bucket player path dispatches here through
 * {@link #register}; Source Bucket and dispenser paths call the {@code take}/{@code place} methods.
 *
 * <p>Each method simulates before checking protection and mutating, and returns whether the
 * transition happened. Mutation and side effects are skipped on the client; successful server
 * mutation broadcasts the corresponding sound to every nearby player and the acting player.
 */
public final class Cauldrons {
    private Cauldrons() {}

    /**
     * Wires Big and Huge Bucket entries into the vanilla empty, water, lava, and powder-snow
     * cauldron interaction maps. Called once during mod setup.
     */
    public static void register() {
        CauldronInteraction.EMPTY.map().put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onEmptyCauldron);
        CauldronInteraction.EMPTY.map().put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onEmptyCauldron);
        CauldronInteraction.WATER.map().put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onWaterCauldron);
        CauldronInteraction.WATER.map().put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onWaterCauldron);
        CauldronInteraction.LAVA.map().put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onLavaCauldron);
        CauldronInteraction.LAVA.map().put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onLavaCauldron);
        CauldronInteraction.POWDER_SNOW.map().put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onPowderSnowCauldron);
        CauldronInteraction.POWDER_SNOW.map().put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onPowderSnowCauldron);
    }

    /** Drains one bucket-volume of water from a full water cauldron into the bucket, emptying it. */
    public static boolean takeWater(Level level, BlockPos pos, Direction face, ItemStack stack,
                                    ProtectionContext context) {
        return takeFluid(level, pos, face, stack, context, Fluids.WATER,
                fullLayeredState(Blocks.WATER_CAULDRON));
    }

    /** Drains one bucket-volume of lava from a lava cauldron into the bucket, emptying it. */
    public static boolean takeLava(Level level, BlockPos pos, Direction face, ItemStack stack,
                                   ProtectionContext context) {
        return takeFluid(level, pos, face, stack, context, Fluids.LAVA,
                Blocks.LAVA_CAULDRON.defaultBlockState());
    }

    /** Fills an empty cauldron to a full water cauldron from one bucket-volume in the bucket. */
    public static boolean placeWater(Level level, BlockPos pos, Direction face, ItemStack stack,
                                     ProtectionContext context) {
        return placeFluid(level, pos, face, stack, context, Fluids.WATER,
                fullLayeredState(Blocks.WATER_CAULDRON));
    }

    /** Converts an empty cauldron into a lava cauldron from one bucket-volume in the bucket. */
    public static boolean placeLava(Level level, BlockPos pos, Direction face, ItemStack stack,
                                    ProtectionContext context) {
        return placeFluid(level, pos, face, stack, context, Fluids.LAVA,
                Blocks.LAVA_CAULDRON.defaultBlockState());
    }

    /**
     * Source Bucket only: a full cauldron of the assigned fluid accepts nothing, but a normal place
     * gesture still reports success with the empty sound, matching placement onto an existing source
     * block.
     */
    public static boolean placeOntoFullCauldron(Level level, BlockPos pos, Direction face, ItemStack stack,
                                                Fluid fluid, ProtectionContext context) {
        BlockState state = level.getBlockState(pos);
        boolean matching = fluid == Fluids.WATER
                ? state.is(Blocks.WATER_CAULDRON) && state.hasProperty(LayeredCauldronBlock.LEVEL)
                        && state.getValue(LayeredCauldronBlock.LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL
                : fluid == Fluids.LAVA && state.is(Blocks.LAVA_CAULDRON);
        if (!matching) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos, face, stack, null)) return false;
        if (!level.isClientSide) {
            BucketSounds.playBucketSound(level, context, pos, BucketSounds.resolveEmptySound(fluid));
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, pos);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    private static ItemInteractionResult onEmptyCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                         InteractionHand hand, ItemStack stack) {
        ProtectionContext context = ProtectionContext.player(player, hand);
        BucketState.Mode mode = BucketState.getMode(stack);
        boolean acted;
        if (mode == BucketState.Mode.FLUID) {
            Fluid fluid = BucketState.getStoredFluid(stack).fluid();
            acted = fluid == Fluids.WATER ? placeWater(level, pos, Direction.UP, stack, context)
                    : fluid == Fluids.LAVA && placeLava(level, pos, Direction.UP, stack, context);
        } else {
            acted = mode == BucketState.Mode.POWDER_SNOW
                    && PowderSnowCauldrons.place(level, pos, Direction.UP, stack, context);
        }
        return acted ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemInteractionResult onWaterCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                         InteractionHand hand, ItemStack stack) {
        boolean acted = takeWater(level, pos, Direction.UP, stack, ProtectionContext.player(player, hand));
        return acted ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemInteractionResult onLavaCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                        InteractionHand hand, ItemStack stack) {
        boolean acted = takeLava(level, pos, Direction.UP, stack, ProtectionContext.player(player, hand));
        return acted ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemInteractionResult onPowderSnowCauldron(BlockState state, Level level, BlockPos pos,
                                                              Player player, InteractionHand hand, ItemStack stack) {
        int capacityUnits = ((BBItem) stack.getItem()).getCapacityUnits();
        boolean acted = PowderSnowCauldrons.take(level, pos, Direction.UP, stack, capacityUnits,
                ProtectionContext.player(player, hand));
        return acted ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static boolean takeFluid(Level level, BlockPos pos, Direction face, ItemStack stack,
                                     ProtectionContext context, Fluid fluid, BlockState fullState) {
        if (!level.getBlockState(pos).equals(fullState)) return false;
        if (stack.getItem() instanceof BBItem
                && !BBItem.canAcceptFluidUnit(stack, unit(fluid))) return false;
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            if (stack.getItem() instanceof BBItem) creditFinite(stack, fluid);
            complete(level, pos, stack, context, Blocks.CAULDRON.defaultBlockState(), true);
        }
        BucketSounds.playBucketSound(level, context, pos, BucketSounds.resolveFillSound(fluid));
        return true;
    }

    private static boolean placeFluid(Level level, BlockPos pos, Direction face, ItemStack stack,
                                      ProtectionContext context, Fluid fluid, BlockState fullState) {
        if (!level.getBlockState(pos).is(Blocks.CAULDRON)) return false;
        if (stack.getItem() instanceof BBItem && !holdsPlaceableUnit(stack, fluid)) return false;
        if (stack.getItem() instanceof SBItem
                && !BucketState.getStoredFluid(stack).fluid().isSame(fluid)) return false;
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            if (stack.getItem() instanceof BBItem) {
                BucketState.drainFiniteContent(stack, FluidBucketItem.BUCKET_VOLUME_MB);
            }
            complete(level, pos, stack, context, fullState, false);
        }
        BucketSounds.playBucketSound(level, context, pos, BucketSounds.resolveEmptySound(fluid));
        return true;
    }

    private static boolean holdsPlaceableUnit(ItemStack stack, Fluid fluid) {
        StoredFluid current = BucketState.getStoredFluid(stack);
        return BucketState.getMode(stack) == BucketState.Mode.FLUID
                && !current.isEmpty()
                && current.fluid().isSame(fluid)
                && current.amount() >= FluidBucketItem.BUCKET_VOLUME_MB;
    }

    private static void creditFinite(ItemStack stack, Fluid fluid) {
        StoredFluid current = BucketState.getStoredFluid(stack);
        boolean merging = BucketState.getMode(stack) == BucketState.Mode.FLUID && !current.isEmpty();
        BucketState.setStoredFluid(stack, merging
                ? current.withAmount(current.amount() + FluidBucketItem.BUCKET_VOLUME_MB)
                : unit(fluid));
    }

    private static StoredFluid unit(Fluid fluid) {
        return new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB, null);
    }

    private static boolean mayInteract(Level level, BlockPos pos, Direction face, ItemStack stack,
                                       ProtectionContext context) {
        return Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face, stack, null);
    }

    private static void complete(Level level, BlockPos pos, ItemStack stack, ProtectionContext context,
                                 BlockState resultState, boolean pickup) {
        level.setBlock(pos, resultState, Block.UPDATE_ALL);
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

    private static BlockState fullLayeredState(Block block) {
        return block.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
    }
}
