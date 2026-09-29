package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;
import java.util.function.Consumer;

/** Forge storage-bucket GameTests, including shared scenarios and pickup-event coverage. */
@GameTestHolder(SomeBuckets.MODID)
public final class StorageBucketGameTests {
    private static final BlockPos PICKUP_POS = new BlockPos(4, 2, 4);

    private StorageBucketGameTests() {}

    /** See {@link StorageBucketScenarios#junk_bucket_absorbs_and_merges_nearby_items}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_absorbs_and_merges_nearby_items(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_absorbs_and_merges_nearby_items(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_absorbs_multiple_entities_in_one_activation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_absorbs_multiple_entities_in_one_activation(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_absorbs_multiple_entities_in_one_activation(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_world_collect_is_bounded_by_pickup_radius}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_world_collect_is_bounded_by_pickup_radius(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_world_collect_is_bounded_by_pickup_radius(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_honors_pickup_delay}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_honors_pickup_delay(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_honors_pickup_delay(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_respects_item_target_and_records_pickup}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_respects_item_target_and_records_pickup(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_respects_item_target_and_records_pickup(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_splits_large_input_across_entries}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_splits_large_input_across_entries(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_splits_large_input_across_entries(helper);
    }

    /** See {@link StorageBucketScenarios#full_junk_bucket_still_merges_compatible_partial_entry}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void full_junk_bucket_still_merges_compatible_partial_entry(GameTestHelper helper) {
        StorageBucketScenarios.full_junk_bucket_still_merges_compatible_partial_entry(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_world_ejection_is_fifo}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_world_ejection_is_fifo(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_world_ejection_is_fifo(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_sneak_use_in_air_throws_oldest}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_sneak_use_in_air_throws_oldest(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_sneak_use_in_air_throws_oldest(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_feeds_adult_animal}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_feeds_adult_animal(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_feeds_adult_animal(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_feeds_baby_animal}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_feeds_baby_animal(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_feeds_baby_animal(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_heals_tamed_wolf_on_breeding_cooldown}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_heals_tamed_wolf_on_breeding_cooldown(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_heals_tamed_wolf_on_breeding_cooldown(helper);
    }

    /** See {@link StorageBucketScenarios#trash_bucket_replaces_incompatible_world_stack}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_replaces_incompatible_world_stack(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_replaces_incompatible_world_stack(helper);
    }

    /** See {@link StorageBucketScenarios#trash_bucket_compatible_overflow_replaces_instead_of_partially_merging}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_compatible_overflow_replaces_instead_of_partially_merging(helper);
    }

    /** See {@link StorageBucketScenarios#trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_overflow_rule_matches_slot_cursor_and_world_intake(helper);
    }

    /** See {@link StorageBucketScenarios#trash_bucket_world_intake_preserves_excess_entity_items}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_world_intake_preserves_excess_entity_items(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_world_intake_preserves_excess_entity_items(helper);
    }

    /** See {@link StorageBucketScenarios#trash_bucket_processes_only_one_world_entity_per_use}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void trash_bucket_processes_only_one_world_entity_per_use(GameTestHelper helper) {
        StorageBucketScenarios.trash_bucket_processes_only_one_world_entity_per_use(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_screen_insert_and_fifo_extract}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_screen_insert_and_fifo_extract(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_screen_insert_and_fifo_extract(helper);
    }

    /** See {@link StorageBucketScenarios#junk_bucket_inventory_prediction_is_deterministic}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_inventory_prediction_is_deterministic(GameTestHelper helper) {
        StorageBucketScenarios.junk_bucket_inventory_prediction_is_deterministic(helper);
    }

    /** See {@link StorageBucketScenarios#storage_eligibility_rule_accepts_buckets_and_refuses_containers}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void storage_eligibility_rule_accepts_buckets_and_refuses_containers(GameTestHelper helper) {
        StorageBucketScenarios.storage_eligibility_rule_accepts_buckets_and_refuses_containers(helper);
    }

    /** See {@link StorageBucketScenarios#stacked_storage_buckets_refuse_inventory_gestures}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void stacked_storage_buckets_refuse_inventory_gestures(GameTestHelper helper) {
        StorageBucketScenarios.stacked_storage_buckets_refuse_inventory_gestures(helper);
    }

    /** See {@link StorageBucketScenarios#storage_buckets_honor_slot_take_rules}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void storage_buckets_honor_slot_take_rules(GameTestHelper helper) {
        StorageBucketScenarios.storage_buckets_honor_slot_take_rules(helper);
    }

    /** See {@link StorageBucketScenarios#stacked_empty_junk_vacuum_moves_one_filled_bucket_to_inventory}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void stacked_empty_junk_vacuum_moves_one_filled_bucket_to_inventory(GameTestHelper helper) {
        StorageBucketScenarios.stacked_empty_junk_vacuum_moves_one_filled_bucket_to_inventory(helper);
    }

    /**
     * Automation-only: vetoes {@code EntityItemPickupEvent} and verifies a Junk Bucket leaves the item in
     * the world, then collects it once the veto is removed.
     */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void junk_bucket_honors_item_pickup_veto(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        Player player = GameTestSupport.survivalPlayer(helper, PICKUP_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        ItemEntity entity = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND, 2), PICKUP_POS);
        ProtectionContext context = ProtectionContext.player(player, InteractionHand.MAIN_HAND);
        JBItem item = (JBItem) bucket.getItem();

        Consumer<EntityItemPickupEvent> veto = new Consumer<EntityItemPickupEvent>() {
            @Override public void accept(EntityItemPickupEvent event) { event.setCanceled(true); }
        };
        MinecraftForge.EVENT_BUS.addListener(veto);
        boolean vetoed;
        try {
            vetoed = item.absorbItemEntities(helper.getLevel(), bucket, List.of(entity), context);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(veto);
        }

        GameTestSupport.check(!vetoed, "Junk Bucket collected an item whose pickup was vetoed");
        GameTestSupport.check(entity.isAlive() && entity.getItem().getCount() == 2,
                "Vetoed pickup changed the item entity");
        GameTestSupport.assertStored(helper, bucket);
        GameTestSupport.check(item.absorbItemEntities(helper.getLevel(), bucket, List.of(entity), context),
                "Junk Bucket could not collect the item once the veto was removed");
        helper.succeed();
    }
}
