package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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
        return PowderSnowCauldrons.place(level, pos, Direction.UP, stack,
                ProtectionContext.player(player, hand))
                ? InteractionResult.sidedSuccess(level.isClientSide)
                : InteractionResult.PASS;
    }

    private static InteractionResult takePowder(BlockState state, Level level, BlockPos pos,
                                                 Player player, InteractionHand hand, ItemStack stack) {
        BBItem bucket = (BBItem) stack.getItem();
        return PowderSnowCauldrons.take(level, pos, Direction.UP, stack,
                bucket.getCapacityUnits(), ProtectionContext.player(player, hand))
                ? InteractionResult.sidedSuccess(level.isClientSide)
                : InteractionResult.PASS;
    }
}
