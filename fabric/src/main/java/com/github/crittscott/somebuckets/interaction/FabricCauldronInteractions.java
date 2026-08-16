package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Powder snow cauldron entries not represented by Fabric's fluid Transfer API. */
public final class FabricCauldronInteractions {
    private FabricCauldronInteractions() {}

    public static void register(Item big8, Item big64) {
        register(big8);
        register(big64);
    }

    private static void register(Item item) {
        CauldronInteraction.EMPTY.put(item, FabricCauldronInteractions::placePowder);
        CauldronInteraction.POWDER_SNOW.put(item, FabricCauldronInteractions::takePowder);
    }

    private static InteractionResult placePowder(BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, ItemStack stack) {
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.POWDER_SNOW
                || NBTUtil.getPowderUnits(stack) <= 0) return InteractionResult.PASS;
        if (!Protections.mayAct(level, ProtectionContext.player(player, hand),
                ProtectionAction.BLOCK_INTERACT, pos, Direction.UP, stack, null)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                    .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL),
                    Block.UPDATE_ALL);
            NBTUtil.setPowderUnits(stack, NBTUtil.getPowderUnits(stack) - 1);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        level.playSound(player, pos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static InteractionResult takePowder(BlockState state, Level level, BlockPos pos,
                                                 Player player, InteractionHand hand, ItemStack stack) {
        if (state.getValue(LayeredCauldronBlock.LEVEL) != LayeredCauldronBlock.MAX_FILL_LEVEL) {
            return InteractionResult.PASS;
        }
        BBItem bucket = (BBItem) stack.getItem();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int units = NBTUtil.getPowderUnits(stack);
        if (mode != NBTUtil.Mode.NONE && mode != NBTUtil.Mode.POWDER_SNOW
                || units >= bucket.getCapacityUnits()) return InteractionResult.PASS;
        if (!Protections.mayAct(level, ProtectionContext.player(player, hand),
                ProtectionAction.BLOCK_INTERACT, pos, Direction.UP, stack, null)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, units + 1);
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, stack);
            }
        }
        level.playSound(player, pos, SoundEvents.BUCKET_FILL_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
