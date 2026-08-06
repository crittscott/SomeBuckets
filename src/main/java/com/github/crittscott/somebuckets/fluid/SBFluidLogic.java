package com.github.crittscott.somebuckets.fluid;

import com.github.crittscott.somebuckets.config.SourceBucketPolicy;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.List;

public class SBFluidLogic implements IFluidLogic {
    private static final SBFluidLogic INSTANCE = new SBFluidLogic();

    private SBFluidLogic() {}

    public static SBFluidLogic getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean tryTake(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        return tryTakeWithContext(level, hit, stack, player == null
                ? ProtectionContext.unownedAutomation()
                : ProtectionContext.player(player, stack));
    }

    public boolean tryTakeWithContext(Level level, BlockHitResult hit, ItemStack stack,
                                      ProtectionContext context) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) return false;

        BlockPos pos = hit.getBlockPos();

        // First try block entity capability
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            IFluidHandler blockHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, hit.getDirection()).orElse(null);
            if (blockHandler != null) {
                IFluidHandlerItem itemHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (itemHandler != null) {
                    return tryTakeFromBlock(level, pos, hit.getDirection(), blockHandler, itemHandler,
                            context, stack);
                }
            }
        }

        // Fall back to cauldron and world interactions
        BlockState state = level.getBlockState(pos);

        // Full water cauldron -> empty cauldron, SB becomes water
        if (state.is(Blocks.WATER_CAULDRON) && state.hasProperty(LayeredCauldronBlock.LEVEL)
                && state.getValue(LayeredCauldronBlock.LEVEL) == 3) {
            if (!SourceBucketPolicy.allows(Fluids.WATER)) return false;
            if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos,
                    hit.getDirection(), stack, null)) return false;
            if (!level.isClientSide) {
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                NBTUtil.setFluidStack(stack, new FluidStack(Fluids.WATER, 1000));
                if (context.player() != null) {
                    context.player().awardStat(Stats.USE_CAULDRON);
                    context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
                }
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                awardPickup(context, stack);
            }
            level.playSound(context.player(), pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }

        // Lava cauldron -> empty cauldron, SB becomes lava
        if (state.is(Blocks.LAVA_CAULDRON)) {
            if (!SourceBucketPolicy.allows(Fluids.LAVA)) return false;
            if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos,
                    hit.getDirection(), stack, null)) return false;
            if (!level.isClientSide) {
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                NBTUtil.setFluidStack(stack, new FluidStack(Fluids.LAVA, 1000));
                if (context.player() != null) {
                    context.player().awardStat(Stats.USE_CAULDRON);
                    context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
                }
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                awardPickup(context, stack);
            }
            level.playSound(context.player(), pos, SoundEvents.BUCKET_FILL_LAVA,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }

        // Generic world fluid, taken through the block's own pickup contract
        FluidStack available = FluidPickup.available(level, pos);
        if (available.isEmpty() || !SourceBucketPolicy.allows(available.getFluid())) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos,
                hit.getDirection(), stack, null)) return false;

        FluidStack taken = FluidPickup.take(level, pos, available, context.player());
        if (taken.isEmpty()) return false;

        if (!level.isClientSide) {
            NBTUtil.setFluidStack(stack, new FluidStack(taken.getFluid(), 1000, taken.getTag()));
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
            awardPickup(context, stack);
        }
        return true;
    }

    @Override
    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        return tryPlace(level, hit, stack, player == null
                ? ProtectionContext.unownedAutomation()
                : ProtectionContext.player(player, stack), true);
    }

    public boolean tryPlace(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                            boolean allowFaceOffset) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.FLUID) return false;

        FluidStack fluidStack = NBTUtil.getFluidStack(stack);
        if (!SourceBucketPolicy.allows(fluidStack)) return false;

        BlockPos clicked = hit.getBlockPos();

        // First try block entity capability
        BlockEntity blockEntity = level.getBlockEntity(clicked);
        if (blockEntity != null) {
            IFluidHandler blockHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, hit.getDirection()).orElse(null);
            if (blockHandler != null) {
                IFluidHandlerItem itemHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (itemHandler != null) {
                    return tryPlaceToBlock(level, clicked, hit.getDirection(), blockHandler, itemHandler,
                            context, stack, fluidStack);
                }
            }
        }

        // Fall back to cauldron and world placement
        return tryPlaceInWorld(level, hit, stack, context, fluidStack, allowFaceOffset);
    }

    private boolean tryTakeFromBlock(Level level, BlockPos pos, net.minecraft.core.Direction face,
                                     IFluidHandler blockHandler, IFluidHandlerItem itemHandler,
                                     ProtectionContext context, ItemStack stack) {
        // Try to drain 1000mB from block
        FluidStack drained = blockHandler.drain(1000, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty() || drained.getAmount() < 1000) return false;

        int filled = itemHandler.fill(drained, IFluidHandler.FluidAction.SIMULATE);
        if (filled < 1000) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face, stack, null)) {
            return false;
        }

        if (!level.isClientSide) {
            blockHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            itemHandler.fill(new FluidStack(drained.getFluid(), 1000, drained.getTag()), IFluidHandler.FluidAction.EXECUTE);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }

        level.playSound(context.player(), pos, fillSound(drained.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private boolean tryPlaceToBlock(Level level, BlockPos pos, net.minecraft.core.Direction face,
                                    IFluidHandler blockHandler, IFluidHandlerItem itemHandler,
                                    ProtectionContext context,
                                    ItemStack stack, FluidStack fluidStack) {
        // SB is infinite source, so always try to fill 1000mB
        FluidStack toTransfer = new FluidStack(fluidStack.getFluid(), 1000, fluidStack.getTag());
        int filled = blockHandler.fill(toTransfer, IFluidHandler.FluidAction.SIMULATE);
        if (filled < 1000) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face, stack, null)) {
            return false;
        }

        if (!level.isClientSide) {
            blockHandler.fill(toTransfer, IFluidHandler.FluidAction.EXECUTE);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }

        level.playSound(context.player(), pos, emptySound(fluidStack.getFluid()), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private static SoundEvent fillSound(Fluid fluid) {
        SoundEvent sound = fluid.getFluidType().getSound(SoundActions.BUCKET_FILL);
        if (sound != null) return sound;
        return fluid.defaultFluidState().is(FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL;
    }

    private static SoundEvent emptySound(Fluid fluid) {
        SoundEvent sound = fluid.getFluidType().getSound(SoundActions.BUCKET_EMPTY);
        if (sound != null) return sound;
        return fluid.defaultFluidState().is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
    }

    private boolean tryPlaceInWorld(Level level, BlockHitResult hit, ItemStack stack,
                                    ProtectionContext context, FluidStack fluidStack,
                                    boolean allowFaceOffset) {
        BlockPos clicked = hit.getBlockPos();
        BlockState clickedState = level.getBlockState(clicked);
        Fluid fluid = fluidStack.getFluid();

        // Cauldron interactions: fill empty cauldron if this fluid has a cauldron block
        if (clickedState.is(Blocks.CAULDRON)) {
            if (fluid == Fluids.WATER) {
                if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, clicked,
                        hit.getDirection(), stack, null)) return false;
                if (!level.isClientSide) {
                    level.setBlock(clicked, Blocks.WATER_CAULDRON.defaultBlockState()
                            .setValue(LayeredCauldronBlock.LEVEL, 3), 3);
                    if (context.player() != null) {
                        context.player().awardStat(Stats.USE_CAULDRON);
                        context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
                    }
                    level.gameEvent(null, GameEvent.FLUID_PLACE, clicked);
                }
                level.playSound(context.player(), clicked, SoundEvents.BUCKET_EMPTY,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                return true;
            } else if (fluid == Fluids.LAVA) {
                if (!Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, clicked,
                        hit.getDirection(), stack, null)) return false;
                if (!level.isClientSide) {
                    level.setBlock(clicked, Blocks.LAVA_CAULDRON.defaultBlockState(), 3);
                    if (context.player() != null) {
                        context.player().awardStat(Stats.USE_CAULDRON);
                        context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
                    }
                    level.gameEvent(null, GameEvent.FLUID_PLACE, clicked);
                }
                level.playSound(context.player(), clicked, SoundEvents.BUCKET_EMPTY_LAVA,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                return true;
            }
        }

        // Check if trying to fill already-full cauldron of same type - do nothing
        if (fluid == Fluids.WATER && clickedState.is(Blocks.WATER_CAULDRON) &&
                clickedState.hasProperty(LayeredCauldronBlock.LEVEL) &&
                clickedState.getValue(LayeredCauldronBlock.LEVEL) == 3) {
            return false;
        }
        if (fluid == Fluids.LAVA && clickedState.is(Blocks.LAVA_CAULDRON)) {
            return false;
        }

        // World placement; the Source Bucket is infinite, so nothing is drained
        if (!FluidPlacement.emptyContents(level, context, stack, clicked, hit, fluid, allowFaceOffset)) return false;

        if (!level.isClientSide && context.player() != null) {
            context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    @Override
    public boolean tryTakePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        // SB does not support powder snow
        return false;
    }

    @Override
    public boolean tryPlacePowder(Level level, BlockHitResult hit, ItemStack stack, @Nullable Player player) {
        // SB does not support powder snow
        return false;
    }

    @Override
    public boolean tryMilkDispenser(Level level, BlockPos front, ItemStack stack) {
        return tryMilkDispenser(level, front, stack, ProtectionContext.unownedAutomation());
    }

    public boolean tryMilkDispenser(Level level, BlockPos front, ItemStack stack, ProtectionContext context) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) return false;
        if (!SourceBucketPolicy.allowsMilk()) return false;
        AABB box = new AABB(front);
        List<Cow> cows = level.getEntitiesOfClass(Cow.class, box, cow -> !cow.isBaby());
        if (cows.isEmpty()) return false;
        Cow cow = cows.get(0);
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_INTERACT, cow.blockPosition(),
                net.minecraft.core.Direction.UP, stack, cow)) return false;

        if (!level.isClientSide) {
            NBTUtil.setMilkAmount(stack, 1000);
        }
        level.playSound(context.player(), front, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Fires the filled-bucket criterion for a real player completing a pickup. */
    private static void awardPickup(ProtectionContext context, ItemStack stack) {
        if (context.player() instanceof ServerPlayer sp) {
            CriteriaTriggers.FILLED_BUCKET.trigger(sp, stack);
        }
    }
}
