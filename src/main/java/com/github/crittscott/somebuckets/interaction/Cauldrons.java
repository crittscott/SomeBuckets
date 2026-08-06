package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import com.github.crittscott.somebuckets.register.ModItems;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

public class Cauldrons {

    public static void register() {
        // Empty cauldron: deposit from DB (one unit → full cauldron, mirrors vanilla)
        CauldronInteraction.EMPTY.put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onEmptyCauldron);
        CauldronInteraction.EMPTY.put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onEmptyCauldron);

        // Water cauldron: take one unit when level==3
        CauldronInteraction.WATER.put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onWaterCauldron);
        CauldronInteraction.WATER.put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onWaterCauldron);

        // Lava cauldron: take one unit
        CauldronInteraction.LAVA.put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onLavaCauldron);
        CauldronInteraction.LAVA.put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onLavaCauldron);

        // Powdered snow cauldron: take one unit
        CauldronInteraction.POWDER_SNOW.put(ModItems.BIG_BUCKET_8.get(), Cauldrons::onPowderSnowCauldron);
        CauldronInteraction.POWDER_SNOW.put(ModItems.BIG_BUCKET_64.get(), Cauldrons::onPowderSnowCauldron);
    }

    private static InteractionResult onEmptyCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                     InteractionHand hand, ItemStack stack) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);

        if (mode == NBTUtil.Mode.FLUID) {
            FluidStack fluidStack = NBTUtil.getFluidStack(stack);
            if (!fluidStack.isEmpty() && fluidStack.getAmount() >= 1000) {
                if (fluidStack.getFluid() == Fluids.WATER) {
                    if (!mayInteract(level, pos, player, hand, stack)) return InteractionResult.PASS;
                    if (!level.isClientSide) {
                        level.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 3);
                        NBTUtil.drainFluid(stack, 1000);
                        NBTUtil.normalizeEmptyState(stack);
                        level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                        awardCauldronUse(player, stack, false);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide());
                } else if (fluidStack.getFluid() == Fluids.LAVA) {
                    if (!mayInteract(level, pos, player, hand, stack)) return InteractionResult.PASS;
                    if (!level.isClientSide) {
                        level.setBlock(pos, Blocks.LAVA_CAULDRON.defaultBlockState(), 3);
                        NBTUtil.drainFluid(stack, 1000);
                        NBTUtil.normalizeEmptyState(stack);
                        level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                        awardCauldronUse(player, stack, false);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide());
                }
                // For other fluids, we can't fill vanilla cauldrons
            }
        } else if (mode == NBTUtil.Mode.POWDER_SNOW && NBTUtil.getPowderUnits(stack) >= 1) {
            if (!mayInteract(level, pos, player, hand, stack)) return InteractionResult.PASS;
            if (!level.isClientSide) {
                level.setBlock(pos, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 3);
                NBTUtil.setPowderUnits(stack, NBTUtil.getPowderUnits(stack) - 1);
                NBTUtil.normalizeEmptyState(stack);
                level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                awardCauldronUse(player, stack, false);
            }
            level.playSound(player, pos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult onWaterCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                     InteractionHand hand, ItemStack stack) {
        int lvl = state.getValue(LayeredCauldronBlock.LEVEL);
        if (lvl == 3) {
            NBTUtil.Mode mode = NBTUtil.getMode(stack);
            int capMb = ((BBItem) stack.getItem()).getCapacityMb();

            if (mode == NBTUtil.Mode.NONE) {
                if (!mayInteract(level, pos, player, hand, stack)) return InteractionResult.PASS;
                if (!level.isClientSide) {
                    NBTUtil.setFluidStack(stack, new FluidStack(Fluids.WATER, 1000));
                    level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                    level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                    awardCauldronUse(player, stack, true);
                }
                level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(level.isClientSide());
            } else if (mode == NBTUtil.Mode.FLUID) {
                FluidStack current = NBTUtil.getFluidStack(stack);
                if (current.getFluid() == Fluids.WATER && current.getAmount() + 1000 <= capMb) {
                    if (!mayInteract(level, pos, player, hand, stack)) return InteractionResult.PASS;
                    if (!level.isClientSide) {
                        NBTUtil.setFluidStack(stack, new FluidStack(Fluids.WATER, current.getAmount() + 1000, current.getTag()));
                        level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                        level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                        awardCauldronUse(player, stack, true);
                    }
                    level.playSound(player, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return InteractionResult.sidedSuccess(level.isClientSide());
                }
            }
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult onLavaCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                    InteractionHand hand, ItemStack stack) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int capMb = ((BBItem) stack.getItem()).getCapacityMb();

        if (mode == NBTUtil.Mode.NONE) {
            if (!mayInteract(level, pos, player, hand, stack)) return InteractionResult.PASS;
            if (!level.isClientSide) {
                NBTUtil.setFluidStack(stack, new FluidStack(Fluids.LAVA, 1000));
                level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                awardCauldronUse(player, stack, true);
            }
            level.playSound(player, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide());
        } else if (mode == NBTUtil.Mode.FLUID) {
            FluidStack current = NBTUtil.getFluidStack(stack);
            if (current.getFluid() == Fluids.LAVA && current.getAmount() + 1000 <= capMb) {
                if (!mayInteract(level, pos, player, hand, stack)) return InteractionResult.PASS;
                if (!level.isClientSide) {
                    NBTUtil.setFluidStack(stack, new FluidStack(Fluids.LAVA, current.getAmount() + 1000, current.getTag()));
                    level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                    level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                    awardCauldronUse(player, stack, true);
                }
                level.playSound(player, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult onPowderSnowCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                          InteractionHand hand, ItemStack stack) {
        int lvl = state.getValue(LayeredCauldronBlock.LEVEL);
        if (lvl == 3) {
            NBTUtil.Mode mode = NBTUtil.getMode(stack);
            int units = NBTUtil.getPowderUnits(stack);
            int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();
            if (mode == NBTUtil.Mode.NONE || (mode == NBTUtil.Mode.POWDER_SNOW && units < capUnits)) {
                if (!mayInteract(level, pos, player, hand, stack)) return InteractionResult.PASS;
                if (!level.isClientSide) {
                    NBTUtil.setPowderUnits(stack, (mode == NBTUtil.Mode.POWDER_SNOW ? units : 0) + 1);
                    level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), 3);
                    level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                    awardCauldronUse(player, stack, true);
                }
                level.playSound(player, pos, SoundEvents.BUCKET_FILL_POWDER_SNOW, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return InteractionResult.PASS;
    }

    private static boolean mayInteract(Level level, BlockPos pos, Player player,
                                       InteractionHand hand, ItemStack stack) {
        return Protections.mayAct(level, ProtectionContext.player(player, hand),
                ProtectionAction.BLOCK_INTERACT, pos, Direction.UP, stack, null);
    }

    /** Awards vanilla's usual cauldron-interaction stats, and the filled-bucket criterion on pickup. */
    private static void awardCauldronUse(Player player, ItemStack stack, boolean firePickupCriterion) {
        player.awardStat(Stats.USE_CAULDRON);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (firePickupCriterion && player instanceof ServerPlayer sp) {
            CriteriaTriggers.FILLED_BUCKET.trigger(sp, stack);
        }
    }
}
