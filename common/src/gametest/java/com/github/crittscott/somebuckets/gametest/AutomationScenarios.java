package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.ProtectionEvent;
import com.github.crittscott.somebuckets.gametest.SharedGameTestSupport.RefusalScope;
import com.github.crittscott.somebuckets.util.BucketState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluids;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class AutomationScenarios {
    private AutomationScenarios() {}
    private static final BlockPos DISPENSER = new BlockPos(2, 2, 4);
    private static final BlockPos FRONT = DISPENSER.east();
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
    static void dispenser_huge_bucket_collects_world_source(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.big64();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, bucket);
        helper.setBlock(FRONT, Blocks.WATER);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(GameTestSupport.big64().getItem()),
                    "Registered BB behavior replaced the Huge Bucket item");
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.AIR);
            helper.succeed();
        });
    }
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
    static void dispenser_fluid_does_not_fall_through_solid_front_block(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack before = bucket.copy();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        BlockPos beyond = FRONT.east();
        helper.setBlock(FRONT, Blocks.STONE);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertSameStack(before, dispenser.getItem(0),
                    "Blocked dispenser fluid placement drained bucket");
            GameTestSupport.assertBlock(helper, FRONT, Blocks.STONE);
            GameTestSupport.assertBlock(helper, beyond, Blocks.AIR);
            helper.succeed();
        });
    }
    static void dispenser_powder_does_not_fall_through_solid_front_block(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.powder(GameTestSupport.big8(), 2);
        ItemStack before = bucket.copy();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        BlockPos beyond = FRONT.east();
        helper.setBlock(FRONT, Blocks.STONE);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertSameStack(before, dispenser.getItem(0),
                    "Blocked dispenser powder placement drained bucket");
            GameTestSupport.assertBlock(helper, FRONT, Blocks.STONE);
            GameTestSupport.assertBlock(helper, beyond, Blocks.AIR);
            helper.succeed();
        });
    }
    static void dispenser_source_does_not_fall_through_solid_front_block(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000);
        ItemStack before = bucket.copy();
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        BlockPos beyond = FRONT.east();
        helper.setBlock(FRONT, Blocks.STONE);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertSameStack(before, dispenser.getItem(0),
                    "Blocked Source Bucket placement changed assignment");
            GameTestSupport.assertBlock(helper, FRONT, Blocks.STONE);
            GameTestSupport.assertBlock(helper, beyond, Blocks.AIR);
            helper.succeed();
        });
    }
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
     * A Big Bucket in a dispenser empties a full powder-snow cauldron in front, then refills the empty
     * cauldron on the next pulse, as it does with water and lava. In game: put an empty Big Bucket in
     * a dispenser facing a full powder-snow cauldron and pulse it twice.
     */
    static void dispenser_collects_and_refills_powder_cauldron(GameTestHelper helper) {
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
            GameTestSupport.assertEmpty(dispenser.getItem(0));
            GameTestSupport.assertBlock(helper, FRONT, Blocks.POWDER_SNOW_CAULDRON);
            helper.succeed();
        });
    }
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
    static void dispenser_junk_bucket_absorbs_and_merges_front_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 20)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.APPLE, 10), FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).getItem() == bucket.getItem(),
                    "Dispenser ejected the Junk Bucket itself");
            GameTestSupport.assertStored(dispenser.getItem(0), new ItemStack(Items.APPLE, 30));
            GameTestSupport.check(!input.isAlive(), "Absorbed item entity remained alive");
            helper.succeed();
        });
    }
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
            GameTestSupport.assertStored(dispenser.getItem(0), stored.toArray(ItemStack[]::new));
            GameTestSupport.check(input.isAlive(), "Full Junk Bucket removed blocked input");
            List<ItemEntity> nearbyItems = GameTestSupport.entities(helper, ItemEntity.class, FRONT, 2.0D);
            GameTestSupport.check(nearbyItems.size() == 1 && nearbyItems.get(0) == input,
                    "Full Junk Bucket ejected a stored stack while input was blocked");
            helper.succeed();
        });
    }
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
    static void dispenser_junk_bucket_feeds_one_adult_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(pig.isInLove(), "Dispenser-fed adult pig did not enter love mode");
            GameTestSupport.assertStored(dispenser.getItem(0), new ItemStack(Items.CARROT, 2));
            helper.succeed();
        });
    }
    static void dispenser_feeding_precedes_item_collection(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 2)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);
        ItemEntity input = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIAMOND), FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(pig.isInLove(), "Animal feeding did not take priority");
            GameTestSupport.assertStored(dispenser.getItem(0), new ItemStack(Items.CARROT));
            GameTestSupport.check(input.isAlive(), "Item was collected before the animal was fed");
            helper.succeed();
        });
    }
    static void dispenser_junk_bucket_grows_one_baby_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 2)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);
        pig.setAge(-1000);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(pig.getAge() > -950, "Dispenser-fed baby pig did not grow enough");
            GameTestSupport.assertStored(dispenser.getItem(0), new ItemStack(Items.CARROT));
            helper.succeed();
        });
    }
    static void dispenser_animal_blocks_junk_bucket_output_when_it_cannot_be_fed(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack carrots = new ItemStack(Items.CARROT, 2);
        BucketState.setStoredItems(bucket, List.of(carrots));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);
        pig.setAge(100);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertStored(dispenser.getItem(0), carrots);
            GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, FRONT, 4.0D).isEmpty(),
                    "Junk Bucket ejected food beside an ineligible animal");
            helper.succeed();
        });
    }
    /**
     * Every dispenser path posts its loader protection event as the {@code [SomeBuckets]} automation
     * player standing at the dispenser, and a refusal leaves bucket and target unchanged. In game:
     * claim an area with a claim mod that denies fake players, and fire Some Buckets dispensers into
     * it for fluid, cauldron, feeding, ejection, capture, and release.
     */
    static void dispenser_protection_refusal_preserves_every_automation_path(GameTestHelper helper) {
        BlockPos fluidDispenserPos = new BlockPos(1, 2, 1);
        BlockPos cauldronDispenserPos = new BlockPos(4, 2, 1);
        BlockPos feedingDispenserPos = new BlockPos(7, 2, 1);
        BlockPos ejectionDispenserPos = new BlockPos(1, 2, 5);
        BlockPos captureDispenserPos = new BlockPos(4, 2, 5);
        BlockPos releaseDispenserPos = new BlockPos(7, 2, 5);

        BlockPos fluidFront = fluidDispenserPos.east();
        BlockPos cauldronFront = cauldronDispenserPos.east();
        BlockPos feedingFront = feedingDispenserPos.west();
        BlockPos ejectionFront = ejectionDispenserPos.east();
        BlockPos captureFront = captureDispenserPos.east();
        BlockPos releaseFront = releaseDispenserPos.west();

        ItemStack fluidBucket = GameTestSupport.big8();
        ItemStack cauldronBucket = GameTestSupport.source();
        ItemStack feedingBucket = GameTestSupport.junk();
        BucketState.setStoredItems(feedingBucket, List.of(new ItemStack(Items.CARROT, 2)));
        ItemStack ejectionBucket = GameTestSupport.junk();
        BucketState.setStoredItems(ejectionBucket, List.of(new ItemStack(Items.DIAMOND, 2)));
        ItemStack captureBucket = GameTestSupport.mob();
        ItemStack releaseBucket = GameTestSupport.mob();
        addPigSnapshot(helper, releaseBucket);

        ItemStack fluidBefore = fluidBucket.copy();
        ItemStack cauldronBefore = cauldronBucket.copy();
        ItemStack feedingBefore = feedingBucket.copy();
        ItemStack ejectionBefore = ejectionBucket.copy();
        ItemStack captureBefore = captureBucket.copy();
        ItemStack releaseBefore = releaseBucket.copy();

        DispenserBlockEntity fluidDispenser = GameTestSupport.dispenser(
                helper, fluidDispenserPos, Direction.EAST, fluidBucket);
        DispenserBlockEntity cauldronDispenser = GameTestSupport.dispenser(
                helper, cauldronDispenserPos, Direction.EAST, cauldronBucket);
        DispenserBlockEntity feedingDispenser = GameTestSupport.dispenser(
                helper, feedingDispenserPos, Direction.WEST, feedingBucket);
        DispenserBlockEntity ejectionDispenser = GameTestSupport.dispenser(
                helper, ejectionDispenserPos, Direction.EAST, ejectionBucket);
        DispenserBlockEntity captureDispenser = GameTestSupport.dispenser(
                helper, captureDispenserPos, Direction.EAST, captureBucket);
        DispenserBlockEntity releaseDispenser = GameTestSupport.dispenser(
                helper, releaseDispenserPos, Direction.WEST, releaseBucket);

        helper.setBlock(fluidFront, Blocks.WATER);
        helper.setBlock(cauldronFront, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        Pig feedingPig = GameTestSupport.spawn(helper, EntityType.PIG, feedingFront);
        Pig capturePig = GameTestSupport.spawn(helper, EntityType.PIG, captureFront);

        Map<BlockPos, BlockPos> expectedTargets = Map.of(
                helper.absolutePos(fluidDispenserPos), helper.absolutePos(fluidFront),
                helper.absolutePos(cauldronDispenserPos), helper.absolutePos(cauldronFront),
                helper.absolutePos(feedingDispenserPos), helper.absolutePos(feedingFront),
                helper.absolutePos(ejectionDispenserPos), helper.absolutePos(ejectionFront),
                helper.absolutePos(captureDispenserPos), helper.absolutePos(captureFront),
                helper.absolutePos(releaseDispenserPos), helper.absolutePos(releaseFront));
        Map<BlockPos, ProtectionEvent> expectedEvents = Map.of(
                helper.absolutePos(fluidDispenserPos), ProtectionEvent.BREAK,
                helper.absolutePos(cauldronDispenserPos), ProtectionEvent.BLOCK_INTERACT,
                helper.absolutePos(feedingDispenserPos), ProtectionEvent.ENTITY_INTERACT,
                helper.absolutePos(ejectionDispenserPos), ProtectionEvent.BLOCK_INTERACT,
                helper.absolutePos(captureDispenserPos), ProtectionEvent.ENTITY_INTERACT,
                helper.absolutePos(releaseDispenserPos), ProtectionEvent.BLOCK_INTERACT);
        Set<BlockPos> observedSources = new HashSet<>();

        RefusalScope refusal = GameTestSupport.refuseProtection((event, actor, pos) -> {
            if (actor.level() != helper.getLevel()) return false;
            BlockPos source = BlockPos.containing(actor.position());
            if (!expectedTargets.containsKey(source)) return false;

            GameTestSupport.check(GameTestSupport.isDispenserActor(actor, source),
                    "Dispenser event was not posted as the automation player");
            GameTestSupport.check(expectedTargets.get(source).equals(pos),
                    "Dispenser event used the wrong position for " + source);
            GameTestSupport.check(expectedEvents.get(source) == event,
                    "Dispenser posted " + event + " instead of " + expectedEvents.get(source) + " for " + source);
            observedSources.add(source);
            return true;
        });

        GameTestSupport.triggerDispenser(helper, fluidDispenserPos);
        GameTestSupport.triggerDispenser(helper, cauldronDispenserPos);
        GameTestSupport.triggerDispenser(helper, feedingDispenserPos);
        GameTestSupport.triggerDispenser(helper, ejectionDispenserPos);
        GameTestSupport.triggerDispenser(helper, captureDispenserPos);
        GameTestSupport.triggerDispenser(helper, releaseDispenserPos);

        helper.runAfterDelay(8L, () -> {
            try {
                GameTestSupport.assertSameStack(fluidBefore, fluidDispenser.getItem(0),
                        "Claim-denied fluid pickup changed its bucket");
                GameTestSupport.assertSameStack(cauldronBefore, cauldronDispenser.getItem(0),
                        "Claim-denied cauldron pickup changed its bucket");
                GameTestSupport.assertSameStack(feedingBefore, feedingDispenser.getItem(0),
                        "Claim-denied feeding changed its bucket");
                GameTestSupport.assertSameStack(ejectionBefore, ejectionDispenser.getItem(0),
                        "Claim-denied ejection changed its bucket");
                GameTestSupport.assertSameStack(captureBefore, captureDispenser.getItem(0),
                        "Claim-denied capture changed its bucket");
                GameTestSupport.assertSameStack(releaseBefore, releaseDispenser.getItem(0),
                        "Claim-denied release changed its bucket");

                GameTestSupport.assertBlock(helper, fluidFront, Blocks.WATER);
                GameTestSupport.assertBlock(helper, cauldronFront, Blocks.WATER_CAULDRON);
                GameTestSupport.check(feedingPig.isAlive() && !feedingPig.isInLove(),
                        "Claim-denied feeding changed its target animal");
                GameTestSupport.check(capturePig.isAlive(),
                        "Claim-denied capture removed its target mob");
                GameTestSupport.check(GameTestSupport.entities(
                        helper, Pig.class, releaseFront, 0.75D).isEmpty(),
                        "Claim-denied release created a mob");
                GameTestSupport.check(observedSources.equals(expectedTargets.keySet()),
                        "Not every dispenser path posted its protection event: " + observedSources);
            } finally {
                refusal.close();
            }
            helper.succeed();
        });
    }

    /**
     * In game: put a stack of three empty Big Buckets in a dispenser facing water and pulse it; one
     * bucket fills and moves to another dispenser slot, and two empties stay in the first slot.
     */
    static void dispenser_stack_of_empty_buckets_fills_one(GameTestHelper helper) {
        ItemStack empties = GameTestSupport.big8();
        empties.setCount(3);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, empties);
        helper.setBlock(FRONT, Blocks.WATER);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            ItemStack first = dispenser.getItem(0);
            GameTestSupport.check(first.getCount() == 2 && BucketState.isEmptyBucket(first),
                    "Expected two empty Big Buckets in the first slot, got " + first + " " + first.getTag());
            ItemStack filled = ItemStack.EMPTY;
            for (int slot = 1; slot < dispenser.getContainerSize(); slot++) {
                if (!dispenser.getItem(slot).isEmpty()) filled = dispenser.getItem(slot);
            }
            GameTestSupport.assertFluid(filled, Fluids.WATER, 1000);
            GameTestSupport.check(filled.getCount() == 1, "More than one bucket filled from one source");
            GameTestSupport.assertBlock(helper, FRONT, Blocks.AIR);
            helper.succeed();
        });
    }

    /**
     * In game: put a Junk Bucket of cod in a dispenser facing an untamed cat and pulse it; the cat is
     * not fed, so the dispenser cannot tame it, and the cod stays stored.
     */
    static void dispenser_does_not_feed_untamed_tamable_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack cod = new ItemStack(Items.COD, 3);
        BucketState.setStoredItems(bucket, List.of(cod));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Cat cat = GameTestSupport.spawn(helper, EntityType.CAT, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(!cat.isTame(), "Dispenser tamed a cat");
            GameTestSupport.assertStored(dispenser.getItem(0), cod);
            helper.succeed();
        });
    }

    /**
     * In game: put an empty Mob Bucket in a dispenser facing someone's tamed wolf and pulse it; the
     * wolf is not captured.
     */
    static void dispenser_does_not_capture_owned_mob(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.mob());
        Wolf wolf = GameTestSupport.spawn(helper, EntityType.WOLF, FRONT);
        wolf.setTame(true);
        wolf.setOwnerUUID(UUID.randomUUID());

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(wolf.isAlive(), "Dispenser captured an owned wolf");
            GameTestSupport.assertEmpty(dispenser.getItem(0));
            helper.succeed();
        });
    }

    private static void addPigSnapshot(GameTestHelper helper, ItemStack bucket) {
        Pig storedPig = EntityType.PIG.create(helper.getLevel());
        GameTestSupport.check(storedPig != null, "Could not create stored pig fixture");
        CompoundTag snapshot = new CompoundTag();
        storedPig.saveWithoutId(snapshot);
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", snapshot);
    }

    private static void addCodSnapshot(GameTestHelper helper, ItemStack bucket) {
        Cod storedCod = EntityType.COD.create(helper.getLevel());
        GameTestSupport.check(storedCod != null, "Could not create stored cod fixture");
        CompoundTag snapshot = new CompoundTag();
        storedCod.saveWithoutId(snapshot);
        BucketState.addEntitySnapshot(bucket, "minecraft:cod", snapshot);
    }
}
