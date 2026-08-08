package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
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

    public static boolean takeWater(Level level, BlockPos pos, Direction face, ItemStack stack,
                                    IFluidHandlerItem handler, ProtectionContext context) {
        return takeFluid(level, pos, face, stack, handler, context, Fluids.WATER,
                fullLayeredState(Blocks.WATER_CAULDRON));
    }

    public static boolean takeLava(Level level, BlockPos pos, Direction face, ItemStack stack,
                                   IFluidHandlerItem handler, ProtectionContext context) {
        return takeFluid(level, pos, face, stack, handler, context, Fluids.LAVA,
                Blocks.LAVA_CAULDRON.defaultBlockState());
    }

    public static boolean placeWater(Level level, BlockPos pos, Direction face, ItemStack stack,
                                     IFluidHandlerItem handler, ProtectionContext context) {
        return placeFluid(level, pos, face, stack, handler, context, Fluids.WATER,
                fullLayeredState(Blocks.WATER_CAULDRON));
    }

    public static boolean placeLava(Level level, BlockPos pos, Direction face, ItemStack stack,
                                    IFluidHandlerItem handler, ProtectionContext context) {
        return placeFluid(level, pos, face, stack, handler, context, Fluids.LAVA,
                Blocks.LAVA_CAULDRON.defaultBlockState());
    }

    public static boolean takePowderSnow(Level level, BlockPos pos, Direction face, ItemStack stack,
                                         int capacityUnits, ProtectionContext context) {
        if (!level.getBlockState(pos).equals(fullLayeredState(Blocks.POWDER_SNOW_CAULDRON))) return false;
        if (capacityUnits < 1) return false;

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

    public static boolean placePowderSnow(Level level, BlockPos pos, Direction face, ItemStack stack,
                                          ProtectionContext context) {
        if (!level.getBlockState(pos).is(Blocks.CAULDRON)) return false;
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.POWDER_SNOW || NBTUtil.getPowderUnits(stack) < 1) {
            return false;
        }
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, NBTUtil.getPowderUnits(stack) - 1);
            NBTUtil.normalizeEmptyState(stack);
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
            FluidStack fluid = NBTUtil.getFluidStack(stack);
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
            if (handler.fill(unit, IFluidHandler.FluidAction.EXECUTE) != FluidType.BUCKET_VOLUME) {
                throw new IllegalStateException("Bucket fluid handler violated its simulated cauldron fill");
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
                throw new IllegalStateException("Bucket fluid handler violated its simulated cauldron drain");
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
