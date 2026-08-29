package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.NeoForgeFluidStacks;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * NeoForge fluid-cauldron transitions for water and lava: block-state changes, protection checks,
 * sound, and vanilla-style stat/criterion accounting. Big Bucket cauldron interaction dispatches
 * here directly through {@link #register}; Source Bucket and dispenser selection paths call the
 * {@code take}/{@code place} methods directly after choosing which one applies. Powder-snow
 * registration delegates to the loader-neutral {@link PowderSnowCauldrons} transitions.
 *
 * <p>Each {@code take}/{@code place} method simulates before checking protection and mutating, and
 * returns whether the transition happened. Mutation and side effects are skipped on the client;
 * successful server mutation broadcasts the corresponding sound to every nearby player.
 */
public final class Cauldrons {
    private Cauldrons() {}

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

    /**
     * Drains one bucket-volume of water from a full water cauldron at {@code pos} into
     * {@code handler}, emptying it. Returns {@code false} without effect if the cauldron isn't a
     * full water cauldron, {@code handler} can't accept the water, or protection denies the action.
     */
    public static boolean takeWater(Level level, BlockPos pos, Direction face, ItemStack stack,
                                    IFluidHandlerItem handler, ProtectionContext context) {
        return takeFluid(level, pos, face, stack, handler, context, Fluids.WATER,
                fullLayeredState(Blocks.WATER_CAULDRON));
    }

    /**
     * Drains one bucket-volume of lava from a lava cauldron at {@code pos} into {@code handler},
     * emptying it. Returns {@code false} without effect if the cauldron isn't a lava cauldron,
     * {@code handler} can't accept the lava, or protection denies the action.
     */
    public static boolean takeLava(Level level, BlockPos pos, Direction face, ItemStack stack,
                                   IFluidHandlerItem handler, ProtectionContext context) {
        return takeFluid(level, pos, face, stack, handler, context, Fluids.LAVA,
                Blocks.LAVA_CAULDRON.defaultBlockState());
    }

    /**
     * Fills an empty cauldron at {@code pos} to a full water cauldron by draining one
     * bucket-volume of water from {@code handler}. Returns {@code false} without effect if the
     * block isn't an empty cauldron, {@code handler} doesn't hold exactly one bucket-volume of
     * water, or protection denies the action.
     */
    public static boolean placeWater(Level level, BlockPos pos, Direction face, ItemStack stack,
                                     IFluidHandlerItem handler, ProtectionContext context) {
        return placeFluid(level, pos, face, stack, handler, context, Fluids.WATER,
                fullLayeredState(Blocks.WATER_CAULDRON));
    }

    /**
     * Converts an empty cauldron at {@code pos} into a lava cauldron by draining one
     * bucket-volume of lava from {@code handler}. Returns {@code false} without effect if the
     * block isn't an empty cauldron, {@code handler} doesn't hold exactly one bucket-volume of
     * lava, or protection denies the action.
     */
    public static boolean placeLava(Level level, BlockPos pos, Direction face, ItemStack stack,
                                    IFluidHandlerItem handler, ProtectionContext context) {
        return placeFluid(level, pos, face, stack, handler, context, Fluids.LAVA,
                Blocks.LAVA_CAULDRON.defaultBlockState());
    }

    private static ItemInteractionResult onEmptyCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                         InteractionHand hand, ItemStack stack) {
        ProtectionContext context = ProtectionContext.player(player, hand);
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        boolean acted;
        if (mode == NBTUtil.Mode.FLUID) {
            IFluidHandlerItem handler = Transfers.requireBucketHandler(stack);
            FluidStack fluid = NeoForgeFluidStacks.get(stack);
            acted = fluid.getFluid() == Fluids.WATER
                    ? placeWater(level, pos, Direction.UP, stack, handler, context)
                    : fluid.getFluid() == Fluids.LAVA
                    && placeLava(level, pos, Direction.UP, stack, handler, context);
        } else {
            acted = mode == NBTUtil.Mode.POWDER_SNOW
                    && PowderSnowCauldrons.place(level, pos, Direction.UP, stack, context);
        }
        return acted ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemInteractionResult onWaterCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                         InteractionHand hand, ItemStack stack) {
        boolean acted = takeWater(level, pos, Direction.UP, stack, Transfers.requireBucketHandler(stack),
                ProtectionContext.player(player, hand));
        return acted ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemInteractionResult onLavaCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                        InteractionHand hand, ItemStack stack) {
        boolean acted = takeLava(level, pos, Direction.UP, stack, Transfers.requireBucketHandler(stack),
                ProtectionContext.player(player, hand));
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
                                     IFluidHandlerItem handler, ProtectionContext context, Fluid fluid,
                                     BlockState fullState) {
        if (!level.getBlockState(pos).equals(fullState)) return false;

        FluidStack unit = new FluidStack(fluid, FluidType.BUCKET_VOLUME);
        if (handler.fill(unit, IFluidHandler.FluidAction.SIMULATE) != FluidType.BUCKET_VOLUME) return false;
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            int accepted = handler.fill(unit, IFluidHandler.FluidAction.EXECUTE);
            if (accepted != FluidType.BUCKET_VOLUME) {
                Transfers.reportFluidContractViolation(level, pos, context, "cauldron bucket fill",
                        handler, FluidType.BUCKET_VOLUME, accepted);
                return false;
            }
            complete(level, pos, stack, context, Blocks.CAULDRON.defaultBlockState(), true);
        }
        Transfers.playBucketSound(level, context, pos, Transfers.resolveFillSound(fluid));
        return true;
    }

    private static boolean placeFluid(Level level, BlockPos pos, Direction face, ItemStack stack,
                                      IFluidHandlerItem handler, ProtectionContext context, Fluid fluid,
                                      BlockState fullState) {
        if (!level.getBlockState(pos).is(Blocks.CAULDRON)) return false;

        FluidStack unit = new FluidStack(fluid, FluidType.BUCKET_VOLUME);
        FluidStack available = handler.drain(unit, IFluidHandler.FluidAction.SIMULATE);
        if (!isExactFluid(available, unit)) return false;
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            FluidStack drained = handler.drain(unit, IFluidHandler.FluidAction.EXECUTE);
            if (!isExactFluid(drained, unit)) {
                Transfers.reportFluidContractViolation(level, pos, context, "cauldron bucket drain",
                        handler, unit, drained);
                return false;
            }
            complete(level, pos, stack, context, fullState, false);
        }
        Transfers.playBucketSound(level, context, pos, Transfers.resolveEmptySound(fluid));
        return true;
    }

    private static boolean isExactFluid(FluidStack actual, FluidStack expected) {
        return !actual.isEmpty() && actual.getAmount() == FluidType.BUCKET_VOLUME
                && NeoForgeFluidStacks.sameFluid(actual, expected);
    }

    private static boolean mayInteract(Level level, BlockPos pos, Direction face, ItemStack stack,
                                       ProtectionContext context) {
        return Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT,
                pos, face, stack, null);
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
        level.gameEvent(null, pickup ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, pos);
    }

    private static BlockState fullLayeredState(Block block) {
        return block.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
    }
}
