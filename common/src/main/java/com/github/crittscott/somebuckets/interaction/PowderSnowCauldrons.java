package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Loader-neutral powder-snow cauldron transitions for players and dispensers. */
public final class PowderSnowCauldrons {
    private PowderSnowCauldrons() {}

    public static boolean take(Level level, BlockPos pos, Direction face, ItemStack stack,
                               int capacityUnits, ProtectionContext context) {
        if (!level.getBlockState(pos).equals(fullPowderState())) return false;

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int currentUnits = NBTUtil.getPowderUnits(stack);
        if (mode != NBTUtil.Mode.NONE
                && (mode != NBTUtil.Mode.POWDER_SNOW || currentUnits >= capacityUnits)) {
            return false;
        }
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack,
                    (mode == NBTUtil.Mode.POWDER_SNOW ? currentUnits : 0) + 1);
            complete(level, pos, stack, context, Blocks.CAULDRON.defaultBlockState(), true);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_FILL_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    public static boolean place(Level level, BlockPos pos, Direction face, ItemStack stack,
                                ProtectionContext context) {
        if (!level.getBlockState(pos).is(Blocks.CAULDRON)) return false;
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.POWDER_SNOW
                || NBTUtil.getPowderUnits(stack) < 1) {
            return false;
        }
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            NBTUtil.setPowderUnits(stack, NBTUtil.getPowderUnits(stack) - 1);
            complete(level, pos, stack, context, fullPowderState(), false);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private static boolean mayInteract(Level level, BlockPos pos, Direction face, ItemStack stack,
                                       ProtectionContext context) {
        return Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT,
                pos, face, stack, null);
    }

    private static void complete(Level level, BlockPos pos, ItemStack stack,
                                 ProtectionContext context, BlockState result,
                                 boolean pickup) {
        level.setBlock(pos, result, Block.UPDATE_ALL);
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

    private static BlockState fullPowderState() {
        return Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
    }
}
