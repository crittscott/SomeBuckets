package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.ProtectionEvent;
import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.RefusalScope;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private ProtectionGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_fluid_pickup_posts_break_for_each_hand(GameTestHelper helper) {
        ProtectionScenarios.player_fluid_pickup_posts_break_for_each_hand(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void handled_block_interaction_denies_dispenser_cauldron_fill(GameTestHelper helper) {
        ProtectionScenarios.handled_block_interaction_denies_dispenser_cauldron_fill(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void unhandled_block_interaction_lets_dispenser_fill_cauldron(GameTestHelper helper) {
        ProtectionScenarios.unhandled_block_interaction_lets_dispenser_fill_cauldron(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_without_placement_permission_cannot_collect(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adventure_player_cannot_eject_stored_items(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_cannot_eject_stored_items(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_pickup_denies_dispenser_absorption(GameTestHelper helper) {
        ProtectionScenarios.refused_pickup_denies_dispenser_absorption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_place_denies_aquatic_release_water(GameTestHelper helper) {
        ProtectionScenarios.refused_place_denies_aquatic_release_water(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_place_checks_fallthrough_destination(GameTestHelper helper) {
        ProtectionScenarios.refused_place_checks_fallthrough_destination(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_pickup_denies_player_junk_absorption(GameTestHelper helper) {
        ProtectionScenarios.refused_pickup_denies_player_junk_absorption(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_pickup_denies_player_trash_absorption(GameTestHelper helper) {
        ProtectionScenarios.refused_pickup_denies_player_trash_absorption(helper);
    }

    /**
     * Forge only: Fabric has no placement recording around {@code useOn}. A Source Bucket using a
     * cauldron is block use, which the click event already authorized, not building. In game: in a
     * claim that allows using blocks but forbids building, right-click a full water cauldron with a
     * stack of empty Source Buckets: one bucket is assigned and the cauldron empties; no extra bucket
     * appears.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_bucket_stack_on_cauldron_is_block_use_not_placement(GameTestHelper helper) {
        useSourceStackOnFullCauldron(helper, ProtectionEvent.PLACE, true);
    }

    /**
     * Control for {@link #source_bucket_stack_on_cauldron_is_block_use_not_placement}: a claim that
     * forbids using blocks leaves the cauldron and the stack unchanged. In game: in a claim that
     * forbids using blocks, right-click a full water cauldron with empty Source Buckets: nothing
     * happens.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void refused_block_use_leaves_cauldron_and_source_stack(GameTestHelper helper) {
        useSourceStackOnFullCauldron(helper, ProtectionEvent.BLOCK_INTERACT, false);
    }

    /*
     * Drives a server player's real right-click on a full water cauldron with four empty Source
     * Buckets while the protection listener refuses one event kind at the cauldron.
     */
    private static void useSourceStackOnFullCauldron(GameTestHelper helper, ProtectionEvent refused,
                                                     boolean expectAssigned) {
        BlockPos cauldron = new BlockPos(4, 2, 4);
        helper.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        ServerPlayer player = GameTestSupport.serverPlayer(helper, cauldron.north(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.source().copyWithCount(4));
        BlockPos absolute = helper.absolutePos(cauldron);

        try (RefusalScope ignored = GameTestSupport.refuseProtection((event, actor, pos) ->
                event == refused && actor == player && absolute.equals(pos))) {
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND, GameTestSupport.hit(helper, cauldron, Direction.UP));
        }

        ItemStack held = player.getMainHandItem();
        GameTestSupport.check(held.getCount() == (expectAssigned ? 3 : 4),
                "Expected " + (expectAssigned ? 3 : 4) + " empty Source Buckets in hand, got " + held.getCount());
        GameTestSupport.assertEmpty(held);
        int assigned = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(held.getItem()) && BucketState.getMode(stack) == BucketState.Mode.FLUID) {
                assigned += stack.getCount();
            }
        }
        GameTestSupport.check(assigned == (expectAssigned ? 1 : 0),
                "Expected " + (expectAssigned ? 1 : 0) + " assigned Source Buckets, found " + assigned);
        GameTestSupport.assertBlock(helper, cauldron, expectAssigned ? Blocks.CAULDRON : Blocks.WATER_CAULDRON);
        helper.succeed();
    }
}
