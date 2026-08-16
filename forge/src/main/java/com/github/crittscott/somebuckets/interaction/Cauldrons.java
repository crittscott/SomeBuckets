package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.ForgeFluidStacks;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * Shared physical cauldron transitions for water, lava, and powder snow: block-state changes,
 * protection checks, sound, and vanilla-style stat/criterion accounting. Big Bucket cauldron
 * interaction dispatches here directly through {@link #register}; Source Bucket and dispenser
 * selection paths call the {@code take}/{@code place} methods directly after choosing which one
 * applies.
 *
 * <p>Each {@code take}/{@code place} method simulates before checking protection and mutating, and
 * returns whether the transition happened. Mutation and side effects are skipped on the client side,
 * but the returned success/failure and the sound are not, matching {@code FluidPickup}/
 * {@code FluidPlacement} convention.
 */
public final class Cauldrons {
    private Cauldrons() {}

    public static void register() {
        CauldronInteraction.EMPTY.put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onEmptyCauldron);
        CauldronInteraction.EMPTY.put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onEmptyCauldron);
        CauldronInteraction.WATER.put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onWaterCauldron);
        CauldronInteraction.WATER.put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onWaterCauldron);
        CauldronInteraction.LAVA.put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onLavaCauldron);
        CauldronInteraction.LAVA.put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onLavaCauldron);
        CauldronInteraction.POWDER_SNOW.put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onPowderSnowCauldron);
        CauldronInteraction.POWDER_SNOW.put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onPowderSnowCauldron);
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

    /**
     * Collects one powder-snow block from a full powder-snow cauldron at {@code pos} into
     * {@code stack}'s NBT, emptying the cauldron. Returns {@code false} without effect if the
     * cauldron isn't full powder snow, {@code stack} already holds different or full-capacity
     * content, or protection denies the action.
     */
    public static boolean takePowderSnow(Level level, BlockPos pos, Direction face, ItemStack stack,
                                         int capacityUnits, ProtectionContext context) {
        if (!level.getBlockState(pos).equals(fullLayeredState(Blocks.POWDER_SNOW_CAULDRON))) return false;

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int currentUnits = NBTUtil.getPowderUnits(stack);
        if (mode != NBTUtil.Mode.NONE
                && (mode != NBTUtil.Mode.POWDER_SNOW || currentUnits >= capacityUnits)) {
            return false;
        }
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, (mode == NBTUtil.Mode.POWDER_SNOW ? currentUnits : 0) + 1);
            complete(level, pos, stack, context, Blocks.CAULDRON.defaultBlockState(), true);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_FILL_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /**
     * Converts an empty cauldron at {@code pos} into a full powder-snow cauldron by removing one
     * powder-snow block from {@code stack}'s NBT. Returns {@code false} without effect if the
     * block isn't an empty cauldron, {@code stack} doesn't hold at least one powder-snow block, or
     * protection denies the action.
     */
    public static boolean placePowderSnow(Level level, BlockPos pos, Direction face, ItemStack stack,
                                          ProtectionContext context) {
        if (!level.getBlockState(pos).is(Blocks.CAULDRON)) return false;
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.POWDER_SNOW || NBTUtil.getPowderUnits(stack) < 1) {
            return false;
        }
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, NBTUtil.getPowderUnits(stack) - 1);
            complete(level, pos, stack, context, fullLayeredState(Blocks.POWDER_SNOW_CAULDRON), false);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private static InteractionResult onEmptyCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                     InteractionHand hand, ItemStack stack) {
        ProtectionContext context = ProtectionContext.player(player, hand);
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        boolean acted;
        if (mode == NBTUtil.Mode.FLUID) {
            IFluidHandlerItem handler = Transfers.requireBucketHandler(stack);
            FluidStack fluid = ForgeFluidStacks.get(stack);
            acted = fluid.getFluid() == Fluids.WATER
                    ? placeWater(level, pos, Direction.UP, stack, handler, context)
                    : fluid.getFluid() == Fluids.LAVA
                    && placeLava(level, pos, Direction.UP, stack, handler, context);
        } else {
            acted = mode == NBTUtil.Mode.POWDER_SNOW
                    && placePowderSnow(level, pos, Direction.UP, stack, context);
        }
        return acted ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.PASS;
    }

    private static InteractionResult onWaterCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                     InteractionHand hand, ItemStack stack) {
        boolean acted = takeWater(level, pos, Direction.UP, stack, Transfers.requireBucketHandler(stack),
                ProtectionContext.player(player, hand));
        return acted ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.PASS;
    }

    private static InteractionResult onLavaCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                    InteractionHand hand, ItemStack stack) {
        boolean acted = takeLava(level, pos, Direction.UP, stack, Transfers.requireBucketHandler(stack),
                ProtectionContext.player(player, hand));
        return acted ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.PASS;
    }

    private static InteractionResult onPowderSnowCauldron(BlockState state, Level level, BlockPos pos,
                                                          Player player, InteractionHand hand, ItemStack stack) {
        int capacityUnits = ((BBItem) stack.getItem()).getCapacityUnits();
        boolean acted = takePowderSnow(level, pos, Direction.UP, stack, capacityUnits,
                ProtectionContext.player(player, hand));
        return acted ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.PASS;
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
        level.playSound(context.player(), pos, Transfers.resolveFillSound(fluid),
                SoundSource.BLOCKS, 1.0F, 1.0F);
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
        level.playSound(context.player(), pos, Transfers.resolveEmptySound(fluid),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private static boolean isExactFluid(FluidStack actual, FluidStack expected) {
        return !actual.isEmpty() && actual.getAmount() == FluidType.BUCKET_VOLUME
                && actual.isFluidEqual(expected);
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
