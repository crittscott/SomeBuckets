package com.github.crittscott.somebuckets.protection;

import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.PlayerEvent;
import eu.pb4.common.protection.api.CommonProtection;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Posts the protection events a Fabric player's own action would reach, before Some Buckets
 * changes the world.
 *
 * <p>Fabric API covers block breaking and block and entity use. It has no block-place or
 * item-pickup event, so placement and pickup also post Architectury's events, which claim mods
 * built on Architectury (such as FTB Chunks) watch. Patbox's Common Protection API is consulted for
 * breaking, placing, and block and entity use. Both integrations are optional: each is called only
 * when its mod is installed, from a nested class that loads only then.
 */
public final class FabricProtectionEvents {
    private static final boolean ARCHITECTURY = FabricLoader.getInstance().isModLoaded("architectury");
    private static final boolean COMMON_PROTECTION =
            FabricLoader.getInstance().isModLoaded("common-protection-api");

    private FabricProtectionEvents() {}

    public static boolean permitsBlockBreak(ServerLevel level, Player actor, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(
                level, actor, pos, state, level.getBlockEntity(pos))) {
            return false;
        }
        if (ARCHITECTURY && !Architectury.permitsBreak(level, actor, pos, state)) return false;
        return !COMMON_PROTECTION || CommonProtectionApi.permitsBreak(level, actor, pos);
    }

    public static boolean permitsBlockPlace(ServerLevel level, Player actor, BlockPos pos,
                                            BlockState placed) {
        if (ARCHITECTURY && !Architectury.permitsPlace(level, actor, pos, placed)) return false;
        return !COMMON_PROTECTION || CommonProtectionApi.permitsPlace(level, actor, pos);
    }

    public static boolean permitsBlockInteract(ServerLevel level, Player actor, InteractionHand hand,
                                               BlockHitResult hit) {
        if (UseBlockCallback.EVENT.invoker().interact(actor, level, hand, hit) == InteractionResult.FAIL) {
            return false;
        }
        return !COMMON_PROTECTION || CommonProtectionApi.permitsInteract(level, actor, hit.getBlockPos());
    }

    public static boolean permitsEntityInteract(ServerLevel level, Player actor, InteractionHand hand,
                                                Entity target) {
        if (UseEntityCallback.EVENT.invoker().interact(actor, level, hand, target, null)
                == InteractionResult.FAIL) {
            return false;
        }
        return !COMMON_PROTECTION || CommonProtectionApi.permitsInteract(level, actor, target);
    }

    public static boolean permitsItemPickup(Player actor, ItemEntity item) {
        return !ARCHITECTURY || Architectury.permitsPickup(actor, item);
    }

    /** Architectury event posting; loaded only when Architectury API is installed. */
    private static final class Architectury {
        static boolean permitsBreak(ServerLevel level, Player actor, BlockPos pos, BlockState state) {
            return !(actor instanceof ServerPlayer player)
                    || !BlockEvent.BREAK.invoker().breakBlock(level, pos, state, player, null).isFalse();
        }

        static boolean permitsPlace(ServerLevel level, Player actor, BlockPos pos, BlockState placed) {
            return !BlockEvent.PLACE.invoker().placeBlock(level, pos, placed, actor).isFalse();
        }

        static boolean permitsPickup(Player actor, ItemEntity item) {
            return !PlayerEvent.PICKUP_ITEM_PRE.invoker().canPickup(actor, item, item.getItem()).isFalse();
        }
    }

    /** Common Protection API checks; loaded only when that API is installed. */
    private static final class CommonProtectionApi {
        static boolean permitsBreak(ServerLevel level, Player actor, BlockPos pos) {
            return CommonProtection.canBreakBlock(level, pos, actor.getGameProfile(), actor);
        }

        static boolean permitsPlace(ServerLevel level, Player actor, BlockPos pos) {
            return CommonProtection.canPlaceBlock(level, pos, actor.getGameProfile(), actor);
        }

        static boolean permitsInteract(ServerLevel level, Player actor, BlockPos pos) {
            return CommonProtection.canInteractBlock(level, pos, actor.getGameProfile(), actor);
        }

        static boolean permitsInteract(ServerLevel level, Player actor, Entity target) {
            return CommonProtection.canInteractEntity(level, target, actor.getGameProfile(), actor);
        }
    }
}
