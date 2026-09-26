package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

final class AutomationScenarios {
    private AutomationScenarios() {}
    private static final BlockPos DISPENSER = new BlockPos(2, 2, 4);
    private static final BlockPos FRONT = DISPENSER.east();
    /** Manual: dispense an empty Big Bucket toward source water; the source is removed and one water unit is stored. */
    static void dispenser_big_bucket_collects_world_source(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.big8());
        helper.setBlock(FRONT, Blocks.WATER);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(GameTestSupport.big8().getItem()),
                    "Registered BB behavior replaced the Big Bucket item");
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.AIR);
            helper.succeed();
        });
    }
    /** Automation-only: malformed component state is discarded before a dispenser can consume it. */
    static void dispenser_malformed_state_is_discarded_before_automation(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big8();
        bucket.set(ModDataComponentTypes.FLUID_CONTENT, new ModDataComponentTypes.FluidContent(
                Fluids.WATER, Integer.MAX_VALUE, DataComponentPatch.EMPTY));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, bucket);
        helper.setBlock(FRONT, Blocks.WATER);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertNoBucketState(
                    dispenser.getItem(0), "malformed dispenser bucket after admission");
            GameTestSupport.assertBlock(helper, FRONT, Blocks.WATER);
            helper.succeed();
        });
    }
    /** Manual: dispense a water-filled Big Bucket toward empty space; a source appears and one unit is consumed. */
    static void dispenser_big_bucket_places_world_fluid_and_consumes_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 2000);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.LAVA, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.LAVA);
            helper.succeed();
        });
    }
    /**
     * Manual: put a solid block directly before dispensers holding a fluid Big Bucket, a powder-snow Big
     * Bucket, and an assigned Source Bucket; nothing is placed beyond it and no bucket changes.
     */
    static void dispensers_do_not_fall_through_solid_front_block(GameTestHelper helper) {
        List<ItemStack> buckets = List.of(
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000),
                GameTestSupport.powder(GameTestSupport.big8(), 2),
                GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000));
        List<BlockPos> positions = List.of(new BlockPos(2, 2, 1), DISPENSER, new BlockPos(2, 2, 7));
        List<ItemStack> before = buckets.stream().map(ItemStack::copy).toList();
        List<DispenserBlockEntity> dispensers = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            BlockPos pos = positions.get(i);
            dispensers.add(GameTestSupport.dispenser(helper, pos, Direction.EAST, buckets.get(i)));
            helper.setBlock(pos.east(), Blocks.STONE);
            GameTestSupport.triggerDispenser(helper, pos);
        }

        helper.runAfterDelay(8L, () -> {
            for (int i = 0; i < buckets.size(); i++) {
                BlockPos front = positions.get(i).east();
                GameTestSupport.assertSameStack(before.get(i), dispensers.get(i).getItem(0),
                        "Blocked dispenser placement changed " + before.get(i));
                GameTestSupport.assertBlock(helper, front, Blocks.STONE);
                GameTestSupport.assertBlock(helper, front.east(), Blocks.AIR);
            }
            helper.succeed();
        });
    }
    /**
     * Manual: pulse an empty Big Bucket dispenser at powder snow, clear the front, and pulse again; it
     * collects then replaces one block.
     */
    static void dispenser_big_bucket_round_trips_powder_snow(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.big8());
        helper.setBlock(FRONT, Blocks.POWDER_SNOW);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertPowder(dispenser.getItem(0), 1);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.AIR);
            GameTestSupport.triggerDispenser(helper, DISPENSER);
        });
        helper.runAfterDelay(16L, () -> {
            GameTestSupport.assertEmpty(dispenser.getItem(0));
            GameTestSupport.assertBlock(helper, FRONT, Blocks.POWDER_SNOW);
            helper.succeed();
        });
    }
    /**
     * Manual: pulse an empty Big Bucket at a full cauldron and then the filled bucket at the empty
     * cauldron; one unit round-trips.
     */
    static void dispenser_big_bucket_round_trips_full_cauldron(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.big8());
        helper.setBlock(FRONT, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.CAULDRON);
            GameTestSupport.triggerDispenser(helper, DISPENSER);
        });
        helper.runAfterDelay(16L, () -> {
            GameTestSupport.assertEmpty(dispenser.getItem(0));
            GameTestSupport.assertBlock(helper, FRONT, Blocks.WATER_CAULDRON);
            GameTestSupport.check(helper.getBlockState(FRONT).getValue(LayeredCauldronBlock.LEVEL)
                            == LayeredCauldronBlock.MAX_FILL_LEVEL,
                    "Dispenser did not refill the water cauldron completely");
            helper.succeed();
        });
    }
    /**
     * Manual: pulse an assigned Source Bucket at matching full and empty cauldrons; it drains and fills
     * without losing assignment.
     */
    static void dispenser_source_round_trips_cauldron_without_consumption(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.source());
        helper.setBlock(FRONT, Blocks.LAVA_CAULDRON);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.LAVA, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.CAULDRON);
            GameTestSupport.triggerDispenser(helper, DISPENSER);
        });
        helper.runAfterDelay(16L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.LAVA, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.LAVA_CAULDRON);
            helper.succeed();
        });
    }
    /**
     * Manual: dispense an empty Big Bucket at a full powder-snow cauldron, then pulse again; it collects
     * but does not refill it.
     */
    static void dispenser_collects_but_does_not_place_powder_cauldron(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.big8());
        helper.setBlock(FRONT, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertPowder(dispenser.getItem(0), 1);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.CAULDRON);
            GameTestSupport.triggerDispenser(helper, DISPENSER);
        });
        helper.runAfterDelay(16L, () -> {
            GameTestSupport.assertPowder(dispenser.getItem(0), 1);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.CAULDRON);
            helper.succeed();
        });
    }
    /**
     * Manual: repeatedly clear and pulse an assigned Source Bucket dispenser; each pulse places fluid
     * without changing assignment.
     */
    static void dispenser_source_places_repeatedly_without_consumption(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, source);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.WATER);
            helper.setBlock(FRONT, Blocks.AIR);
            GameTestSupport.triggerDispenser(helper, DISPENSER);
        });
        helper.runAfterDelay(16L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.WATER);
            helper.succeed();
        });
    }
    /**
     * Manual: dispense an assigned Source Bucket toward matching source fluid; the source is removed and
     * assignment remains.
     */
    static void dispenser_assigned_source_takes_matching_world_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, bucket);
        helper.setBlock(FRONT, Blocks.WATER);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertSameStack(before, dispenser.getItem(0),
                    "Matching dispenser pickup changed Source Bucket assignment");
            GameTestSupport.assertBlock(helper, FRONT, Blocks.AIR);
            helper.succeed();
        });
    }
    /**
     * Manual: dispense a lava-assigned Source Bucket toward a water pool; it places lava instead of
     * taking, the water reaction turns the placed lava into obsidian, and assignment remains.
     */
    static void dispenser_source_places_into_different_world_fluid(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        ItemStack before = bucket.copy();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, bucket);
        helper.setBlock(FRONT, Blocks.WATER);
        helper.setBlock(FRONT.east(), Blocks.WATER);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertSameStack(before, dispenser.getItem(0),
                    "Placing into a different fluid changed Source Bucket assignment");
            GameTestSupport.assertBlock(helper, FRONT, Blocks.OBSIDIAN);
            helper.succeed();
        });
    }
    /**
     * Manual: put an adult cow before a dispenser with an empty Source Bucket and pulse it; the bucket
     * assigns milk.
     */
    static void dispenser_empty_source_milks_adult_cow(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.source());
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(GameTestSupport.source().getItem()),
                    "Registered SB behavior replaced the Source Bucket item");
            GameTestSupport.assertMilk(dispenser.getItem(0), 1000);
            GameTestSupport.check(cow.isAlive(), "Dispenser milking removed cow");
            helper.succeed();
        });
    }
    /** Manual: place one eligible mob before a dispenser with an empty Mob Bucket and pulse it; the mob is captured. */
    static void dispenser_empty_mob_bucket_captures_one_entity(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.mob());
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(GameTestSupport.mob().getItem()),
                    "Registered MB behavior replaced the Mob Bucket item");
            GameTestSupport.check(!pig.isAlive(), "Dispenser-captured pig remained alive");
            GameTestSupport.check(BucketState.getEntityCount(dispenser.getItem(0)) == 1,
                    "Dispenser Mob Bucket did not store one entity");
            GameTestSupport.check(BucketState.getCurrentEntityType(dispenser.getItem(0)) == EntityType.PIG,
                    "Dispenser Mob Bucket stored wrong entity type");
            helper.succeed();
        });
    }
    /**
     * Manual: dispense stacked empty fluid, storage, and Mob Buckets at valid inputs; one filled result
     * separates from each empty stack.
     */
    static void dispenser_stacked_empty_buckets_settle_each_bucket_family(GameTestHelper helper) {
        BlockPos bigPos = new BlockPos(1, 2, 1);
        BlockPos hugePos = new BlockPos(4, 2, 1);
        BlockPos sourcePos = new BlockPos(7, 2, 1);
        BlockPos mobPos = new BlockPos(1, 2, 5);
        BlockPos junkPos = new BlockPos(4, 2, 5);
        BlockPos trashPos = new BlockPos(7, 2, 5);

        DispenserBlockEntity big = stackedDispenser(helper, bigPos, Direction.EAST, GameTestSupport.big8());
        DispenserBlockEntity huge = stackedDispenser(helper, hugePos, Direction.EAST, GameTestSupport.big64());
        DispenserBlockEntity source = stackedDispenser(helper, sourcePos, Direction.WEST, GameTestSupport.source());
        DispenserBlockEntity mob = stackedDispenser(helper, mobPos, Direction.EAST, GameTestSupport.mob());
        DispenserBlockEntity junk = stackedDispenser(helper, junkPos, Direction.EAST, GameTestSupport.junk());
        DispenserBlockEntity trash = stackedDispenser(helper, trashPos, Direction.WEST, GameTestSupport.trash());

        helper.setBlock(bigPos.east(), Blocks.WATER);
        helper.setBlock(hugePos.east(), Blocks.WATER);
        GameTestSupport.spawn(helper, EntityType.COW, sourcePos.west());
        GameTestSupport.spawn(helper, EntityType.PIG, mobPos.east());
        GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND), junkPos.east());
        GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD), trashPos.west());

        GameTestSupport.triggerDispenser(helper, bigPos);
        GameTestSupport.triggerDispenser(helper, hugePos);
        GameTestSupport.triggerDispenser(helper, sourcePos);
        GameTestSupport.triggerDispenser(helper, mobPos);
        GameTestSupport.triggerDispenser(helper, junkPos);
        GameTestSupport.triggerDispenser(helper, trashPos);

        helper.runAfterDelay(8L, () -> {
            assertOneEmptyAndOneFilled(big, GameTestSupport.big8().getItem(), "Big Bucket");
            assertOneEmptyAndOneFilled(huge, GameTestSupport.big64().getItem(), "Huge Bucket");
            assertOneEmptyAndOneFilled(source, GameTestSupport.source().getItem(), "Source Bucket");
            assertOneEmptyAndOneFilled(mob, GameTestSupport.mob().getItem(), "Mob Bucket");
            assertOneEmptyAndOneFilled(junk, GameTestSupport.junk().getItem(), "Junk Bucket");
            assertOneEmptyAndOneFilled(trash, GameTestSupport.trash().getItem(), "Trash Bucket");
            helper.succeed();
        });
    }
    /** Manual: pulse a partly filled Mob Bucket toward a matching mob; the mob is appended to the bucket. */
    static void dispenser_nonempty_mob_bucket_captures_matching_entity(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addPigSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(!pig.isAlive(), "Compatible pig was not captured");
            GameTestSupport.check(BucketState.getEntityCount(dispenser.getItem(0)) == 2,
                    "Nonempty Mob Bucket did not accumulate a second pig");
            GameTestSupport.check(GameTestSupport.entities(helper, Pig.class, FRONT, 0.75D).isEmpty(),
                    "Dispenser released a pig instead of capturing the compatible target");
            helper.succeed();
        });
    }
    /**
     * Manual: pulse a full Mob Bucket while a matching mob occupies the front block; it neither captures
     * nor releases.
     */
    static void dispenser_full_mob_bucket_does_nothing_when_matching_mob_occupies_front(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        for (int i = 0; i < 8; i++) addPigSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(pig.isAlive(), "Full Mob Bucket removed the occupying pig");
            GameTestSupport.check(BucketState.getEntityCount(dispenser.getItem(0)) == 8,
                    "Full Mob Bucket released into an occupied block");
            helper.succeed();
        });
    }
    /** Manual: pulse a nonempty Mob Bucket into a clear front block; its oldest mob is released. */
    static void dispenser_nonempty_mob_bucket_releases_entity(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addPigSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertEmpty(dispenser.getItem(0));
            List<Pig> pigs = GameTestSupport.entities(helper, Pig.class, FRONT, 0.75D);
            GameTestSupport.check(pigs.size() == 1, "Expected one dispenser-released pig, got " + pigs.size());
            helper.succeed();
        });
    }
    /** Manual: pulse an aquatic Mob Bucket into a clear valid block; water is placed and the mob is released. */
    static void dispenser_mob_bucket_releases_aquatic_entity_with_water(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addCodSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertEmpty(dispenser.getItem(0));
            GameTestSupport.assertBlock(helper, FRONT, Blocks.WATER);
            List<Cod> cods = GameTestSupport.entities(helper, Cod.class, FRONT, 0.75D);
            GameTestSupport.check(cods.size() == 1,
                    "Expected one dispenser-released cod, got " + cods.size());
            helper.succeed();
        });
    }
    /**
     * Manual: release one of two aquatic mobs, clear the front block, and pulse again; the second mob and
     * water are released.
     */
    static void dispenser_mob_bucket_releases_second_aquatic_entity_after_front_is_cleared(
            GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addCodSnapshot(helper, bucket);
        addCodSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(BucketState.getEntityCount(dispenser.getItem(0)) == 1,
                    "First dispenser release did not consume exactly one cod snapshot");
            List<Cod> firstRelease = GameTestSupport.entities(helper, Cod.class, FRONT, 0.75D);
            GameTestSupport.check(firstRelease.size() == 1,
                    "Expected one cod after first dispenser release, got " + firstRelease.size());
            firstRelease.forEach(Cod::discard);
            GameTestSupport.check(GameTestSupport.entities(helper, Cod.class, FRONT, 0.75D).isEmpty(),
                    "First released cod did not clear from the dispenser target");

            GameTestSupport.triggerDispenser(helper, DISPENSER);
            helper.runAfterDelay(8L, () -> {
                GameTestSupport.assertEmpty(dispenser.getItem(0));
                List<Cod> secondRelease = GameTestSupport.entities(helper, Cod.class, FRONT, 0.75D);
                GameTestSupport.check(secondRelease.size() == 1,
                        "Expected one cod after second dispenser release, got " + secondRelease.size());
                helper.succeed();
            });
        });
    }
    /** Manual: pulse a nonempty Mob Bucket toward a different mob type; neither capture nor release occurs. */
    static void dispenser_nonempty_mob_bucket_does_not_capture_another_entity(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addPigSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(cow.isAlive(), "Incompatible cow was captured");
            GameTestSupport.check(BucketState.getEntityCount(dispenser.getItem(0)) == 1,
                    "Occupied front did not preserve stored pig");
            List<Pig> pigs = GameTestSupport.entities(helper, Pig.class, FRONT, 0.75D);
            GameTestSupport.check(pigs.isEmpty(), "Dispenser released pig into a mob-occupied block");
            helper.succeed();
        });
    }
    /** Manual: pulse a Junk Bucket toward compatible item entities; they are collected and merged. */
    static void dispenser_junk_bucket_absorbs_and_merges_front_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 20)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(GameTestSupport.junk().getItem()),
                    "Dispenser ejected the Junk Bucket itself");
            GameTestSupport.assertStored(helper, dispenser.getItem(0), new ItemStack(Items.APPLE, 30));
            GameTestSupport.check(!input.isAlive(), "Absorbed item entity remained alive");
            helper.succeed();
        });
    }
    /**
     * Manual: fill a Junk Bucket and leave an uncollectable item before its dispenser; a pulse does not
     * eject stored contents.
     */
    static void dispenser_full_junk_bucket_does_not_eject_when_input_is_blocked(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        List<ItemStack> stored = List.of(
                new ItemStack(Items.APPLE, 64),
                new ItemStack(Items.DIAMOND, 64),
                new ItemStack(Items.EMERALD, 64),
                new ItemStack(Items.IRON_INGOT, 64),
                new ItemStack(Items.GOLD_INGOT, 64),
                new ItemStack(Items.COAL, 64),
                new ItemStack(Items.REDSTONE, 64),
                new ItemStack(Items.LAPIS_LAZULI, 64),
                new ItemStack(Items.QUARTZ, 64));
        BucketState.setStoredItems(bucket, stored);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT), FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertStored(helper, dispenser.getItem(0), stored.toArray(ItemStack[]::new));
            GameTestSupport.check(input.isAlive(), "Full Junk Bucket removed blocked input");
            List<ItemEntity> nearbyItems = GameTestSupport.entities(helper, ItemEntity.class, FRONT, 2.0D);
            GameTestSupport.check(nearbyItems.size() == 1 && nearbyItems.get(0) == input,
                    "Full Junk Bucket ejected a stored stack while input was blocked");
            helper.succeed();
        });
    }
    /**
     * Manual: pulse a Junk Bucket holding two stacks toward an empty front block; only the oldest stack is
     * ejected and the bucket stays in the dispenser.
     */
    static void dispenser_junk_bucket_ejects_oldest_stack(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack first = new ItemStack(Items.DIAMOND, 2);
        ItemStack second = new ItemStack(Items.APPLE, 3);
        BucketState.setStoredItems(bucket, List.of(first, second));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(GameTestSupport.junk().getItem()),
                    "Dispenser ejected the Junk Bucket itself");
            GameTestSupport.assertStored(helper, dispenser.getItem(0), second);
            List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class, FRONT, 8.0D);
            GameTestSupport.check(drops.size() == 1, "Expected one ejected item entity, got " + drops.size());
            GameTestSupport.assertSameStack(first, drops.get(0).getItem(),
                    "Dispenser did not eject the oldest stack");
            helper.succeed();
        });
    }
    /**
     * Manual: pulse a Trash Bucket toward an incompatible item entity; its stored entry is destroyed and
     * replaced by one entity.
     */
    static void dispenser_trash_bucket_replaces_one_front_item(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.DIAMOND, 5)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT, 12), FRONT);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD, 3), FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(GameTestSupport.trash().getItem()),
                    "Registered storage behavior replaced the Trash Bucket item");
            List<ItemStack> contents = BucketState.getStoredItems(dispenser.getItem(0));
            GameTestSupport.check(contents.size() == 1, "Trash Bucket did not retain one stored stack");
            int removed = (first.isAlive() ? 0 : 1) + (second.isAlive() ? 0 : 1);
            GameTestSupport.check(removed == 1, "Trash Bucket processed " + removed + " item entities");
            helper.succeed();
        });
    }
    /**
     * Manual: store suitable food and pulse a Junk Bucket toward an adult animal; one food is consumed and
     * breeding begins.
     */
    static void dispenser_junk_bucket_feeds_one_adult_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(pig.isInLove(), "Dispenser-fed adult pig did not enter love mode");
            GameTestSupport.assertStored(helper, dispenser.getItem(0), new ItemStack(Items.CARROT, 2));
            helper.succeed();
        });
    }
    /**
     * Manual: put a feedable animal and collectable item before a food-holding dispenser; the pulse feeds
     * and leaves the item.
     */
    static void dispenser_feeding_precedes_item_collection(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 2)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND), FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(pig.isInLove(), "Animal feeding did not take priority");
            GameTestSupport.assertStored(helper, dispenser.getItem(0), new ItemStack(Items.CARROT));
            GameTestSupport.check(input.isAlive(), "Item was collected before the animal was fed");
            helper.succeed();
        });
    }
    /** Manual: pulse a food-holding Junk Bucket toward a baby animal; one food is consumed and growth advances. */
    static void dispenser_junk_bucket_grows_one_baby_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 2)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);
        pig.setAge(-1000);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(pig.getAge() > -950, "Dispenser-fed baby pig did not grow enough");
            GameTestSupport.assertStored(helper, dispenser.getItem(0), new ItemStack(Items.CARROT));
            helper.succeed();
        });
    }
    /**
     * Manual: put an animal that cannot currently eat before a nonempty Junk Bucket dispenser; a pulse
     * does not eject contents.
     */
    static void dispenser_animal_blocks_junk_bucket_output_when_it_cannot_be_fed(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack carrots = new ItemStack(Items.CARROT, 2);
        BucketState.setStoredItems(bucket, List.of(carrots));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);
        pig.setAge(100);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertStored(helper, dispenser.getItem(0), carrots);
            GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, FRONT, 4.0D).isEmpty(),
                    "Junk Bucket ejected food beside an ineligible animal");
            helper.succeed();
        });
    }
    private static void addPigSnapshot(GameTestHelper helper, ItemStack bucket) {
        Pig storedPig = EntityType.PIG.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(storedPig != null, "Could not create stored pig fixture");
        CompoundTag snapshot = new CompoundTag();
        storedPig.saveWithoutId(snapshot);
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", snapshot);
    }

    private static void addCodSnapshot(GameTestHelper helper, ItemStack bucket) {
        Cod storedCod = EntityType.COD.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        GameTestSupport.check(storedCod != null, "Could not create stored cod fixture");
        CompoundTag snapshot = new CompoundTag();
        storedCod.saveWithoutId(snapshot);
        BucketState.addEntitySnapshot(bucket, "minecraft:cod", snapshot);
    }

    private static DispenserBlockEntity stackedDispenser(GameTestHelper helper, BlockPos pos,
                                                           Direction direction, ItemStack stack) {
        stack.setCount(2);
        return GameTestSupport.dispenser(helper, pos, direction, stack);
    }

    private static void assertOneEmptyAndOneFilled(DispenserBlockEntity dispenser, Item item,
                                                    String description) {
        int emptyCount = 0;
        int filledCount = 0;
        for (int slot = 0; slot < dispenser.getContainerSize(); slot++) {
            ItemStack stack = dispenser.getItem(slot);
            if (!stack.is(item)) continue;
            if (BucketState.isEmptyBucket(stack)) {
                emptyCount += stack.getCount();
            } else {
                GameTestSupport.check(stack.getCount() == 1,
                        description + " result was an illegal multi-count filled stack");
                filledCount++;
            }
        }
        GameTestSupport.check(emptyCount == 1,
                description + " did not leave exactly one empty bucket");
        GameTestSupport.check(filledCount == 1,
                description + " did not settle exactly one filled bucket");
    }
}
