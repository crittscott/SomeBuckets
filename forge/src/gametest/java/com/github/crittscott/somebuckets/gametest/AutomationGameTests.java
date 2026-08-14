package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.protection.ClaimProtections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class AutomationGameTests {
    private static final BlockPos DISPENSER = new BlockPos(2, 2, 4);
    private static final BlockPos FRONT = DISPENSER.east();

    private AutomationGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_big_bucket_collects_world_source(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.big8());
        helper.setBlock(FRONT, Blocks.WATER);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(ModItems.BIG_BUCKET_8.get()),
                    "Registered BB behavior replaced the Big Bucket item");
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.AIR);
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_huge_bucket_collects_world_source(GameTestHelper helper) {
        ItemStack bucket = new ItemStack(ModItems.BIG_BUCKET_64.get());
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, bucket);
        helper.setBlock(FRONT, Blocks.WATER);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(ModItems.BIG_BUCKET_64.get()),
                    "Registered BB behavior replaced the Huge Bucket item");
            GameTestSupport.assertFluid(
                    dispenser.getItem(0), Fluids.WATER, FluidType.BUCKET_VOLUME);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.AIR);
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_big_bucket_places_world_fluid_and_consumes_unit(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 2000);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.LAVA, 1000);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.LAVA);
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_fluid_does_not_fall_through_solid_front_block(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_powder_does_not_fall_through_solid_front_block(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_source_does_not_fall_through_solid_front_block(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_big_bucket_round_trips_powder_snow(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_big_bucket_round_trips_full_cauldron(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.big8());
        helper.setBlock(FRONT, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, FluidType.BUCKET_VOLUME);
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_source_round_trips_cauldron_without_consumption(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.source());
        helper.setBlock(FRONT, Blocks.LAVA_CAULDRON);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.LAVA, FluidType.BUCKET_VOLUME);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.CAULDRON);
            GameTestSupport.triggerDispenser(helper, DISPENSER);
        });
        helper.runAfterDelay(16L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.LAVA, FluidType.BUCKET_VOLUME);
            GameTestSupport.assertBlock(helper, FRONT, Blocks.LAVA_CAULDRON);
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_collects_but_does_not_place_powder_cauldron(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_source_places_repeatedly_without_consumption(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_empty_source_milks_adult_cow(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.source());
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(ModItems.SOURCE_BUCKET.get()),
                    "Registered SB behavior replaced the Source Bucket item");
            GameTestSupport.assertMilk(dispenser.getItem(0), 1000);
            GameTestSupport.check(cow.isAlive(), "Dispenser milking removed cow");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_empty_mob_bucket_captures_one_entity(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.mob());
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(ModItems.MOB_BUCKET.get()),
                    "Registered MB behavior replaced the Mob Bucket item");
            GameTestSupport.check(!pig.isAlive(), "Dispenser-captured pig remained alive");
            GameTestSupport.check(NBTUtil.getEntityCount(dispenser.getItem(0)) == 1,
                    "Dispenser Mob Bucket did not store one entity");
            GameTestSupport.check(NBTUtil.getCurrentEntityType(dispenser.getItem(0)) == EntityType.PIG,
                    "Dispenser Mob Bucket stored wrong entity type");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_nonempty_mob_bucket_captures_matching_entity(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addPigSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(!pig.isAlive(), "Compatible pig was not captured");
            GameTestSupport.check(NBTUtil.getEntityCount(dispenser.getItem(0)) == 2,
                    "Nonempty Mob Bucket did not accumulate a second pig");
            GameTestSupport.check(GameTestSupport.entities(helper, Pig.class, FRONT, 0.75D).isEmpty(),
                    "Dispenser released a pig instead of capturing the compatible target");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_full_mob_bucket_does_nothing_when_matching_mob_occupies_front(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        for (int i = 0; i < 8; i++) addPigSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(pig.isAlive(), "Full Mob Bucket removed the occupying pig");
            GameTestSupport.check(NBTUtil.getEntityCount(dispenser.getItem(0)) == 8,
                    "Full Mob Bucket released into an occupied block");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_nonempty_mob_bucket_releases_entity(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_mob_bucket_releases_aquatic_entity_with_water(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_mob_bucket_releases_second_aquatic_entity_after_front_is_cleared(
            GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addCodSnapshot(helper, bucket);
        addCodSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(NBTUtil.getEntityCount(dispenser.getItem(0)) == 1,
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_nonempty_mob_bucket_does_not_capture_another_entity(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        addPigSnapshot(helper, bucket);
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Cow cow = GameTestSupport.spawn(helper, EntityType.COW, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(cow.isAlive(), "Incompatible cow was captured");
            GameTestSupport.check(NBTUtil.getEntityCount(dispenser.getItem(0)) == 1,
                    "Occupied front did not preserve stored pig");
            List<Pig> pigs = GameTestSupport.entities(helper, Pig.class, FRONT, 0.75D);
            GameTestSupport.check(pigs.isEmpty(), "Dispenser released pig into a mob-occupied block");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_junk_bucket_absorbs_and_merges_front_items(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 20)));
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_full_junk_bucket_does_not_eject_when_input_is_blocked(GameTestHelper helper) {
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
        NBTUtil.setStoredItems(bucket, stored);
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_trash_bucket_replaces_one_front_item(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.trash();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.DIAMOND, 5)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        ItemEntity first = GameTestSupport.spawnItem(helper, new ItemStack(Items.DIRT, 12), FRONT);
        ItemEntity second = GameTestSupport.spawnItem(helper, new ItemStack(Items.EMERALD, 3), FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(dispenser.getItem(0).is(ModItems.TRASH_BUCKET.get()),
                    "Registered storage behavior replaced the Trash Bucket item");
            List<ItemStack> contents = NBTUtil.getStoredItems(dispenser.getItem(0));
            GameTestSupport.check(contents.size() == 1, "Trash Bucket did not retain one stored stack");
            int removed = (first.isAlive() ? 0 : 1) + (second.isAlive() ? 0 : 1);
            GameTestSupport.check(removed == 1, "Trash Bucket processed " + removed + " item entities");
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_junk_bucket_feeds_one_adult_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 3)));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);
        Pig pig = GameTestSupport.spawn(helper, EntityType.PIG, FRONT);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.check(pig.isInLove(), "Dispenser-fed adult pig did not enter love mode");
            GameTestSupport.assertStored(dispenser.getItem(0), new ItemStack(Items.CARROT, 2));
            helper.succeed();
        });
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_feeding_precedes_item_collection(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 2)));
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_junk_bucket_grows_one_baby_animal(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.CARROT, 2)));
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_animal_blocks_junk_bucket_output_when_it_cannot_be_fed(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack carrots = new ItemStack(Items.CARROT, 2);
        NBTUtil.setStoredItems(bucket, List.of(carrots));
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_claim_denial_preserves_every_automation_path(GameTestHelper helper) {
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
        NBTUtil.setStoredItems(feedingBucket, List.of(new ItemStack(Items.CARROT, 2)));
        ItemStack ejectionBucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(ejectionBucket, List.of(new ItemStack(Items.DIAMOND, 2)));
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
        Map<BlockPos, Direction> expectedFaces = Map.of(
                helper.absolutePos(fluidDispenserPos), Direction.WEST,
                helper.absolutePos(cauldronDispenserPos), Direction.WEST,
                helper.absolutePos(feedingDispenserPos), Direction.EAST,
                helper.absolutePos(ejectionDispenserPos), Direction.WEST,
                helper.absolutePos(captureDispenserPos), Direction.WEST,
                helper.absolutePos(releaseDispenserPos), Direction.EAST);
        Map<BlockPos, ProtectionAction> expectedActions = Map.of(
                helper.absolutePos(fluidDispenserPos), ProtectionAction.FLUID_EDIT,
                helper.absolutePos(cauldronDispenserPos), ProtectionAction.BLOCK_INTERACT,
                helper.absolutePos(feedingDispenserPos), ProtectionAction.ENTITY_INTERACT,
                helper.absolutePos(ejectionDispenserPos), ProtectionAction.ENTITY_RELEASE,
                helper.absolutePos(captureDispenserPos), ProtectionAction.ENTITY_INTERACT,
                helper.absolutePos(releaseDispenserPos), ProtectionAction.ENTITY_RELEASE);
        Set<BlockPos> observedSources = new HashSet<>();

        ClaimProtections.Registration registration = ClaimProtections.register(
                (level, actor, action, target, face, held, entity) -> {
                    if (level != helper.getLevel()) return true;
                    BlockPos source = actor.automationSource();
                    if (source == null || !expectedTargets.containsKey(source)) return true;

                    GameTestSupport.check(actor.isAutomation() && actor.player() == null,
                            "Dispenser mutation reached the provider as a player action");
                    GameTestSupport.check(expectedTargets.get(source).equals(target),
                            "Dispenser protection used the wrong target for " + source);
                    GameTestSupport.check(expectedFaces.get(source) == face,
                            "Dispenser protection used the outward face for " + source);
                    GameTestSupport.check(expectedActions.get(source) == action,
                            "Dispenser protection used the wrong action for " + source);
                    observedSources.add(source);
                    return false;
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
                        "Not every dispenser path reached claim protection: " + observedSources);
            } finally {
                registration.close();
            }
            helper.succeed();
        });
    }

    private static void addPigSnapshot(GameTestHelper helper, ItemStack bucket) {
        Pig storedPig = EntityType.PIG.create(helper.getLevel());
        GameTestSupport.check(storedPig != null, "Could not create stored pig fixture");
        CompoundTag snapshot = new CompoundTag();
        storedPig.saveWithoutId(snapshot);
        if (NBTUtil.getEntityCount(bucket) == 0) {
            NBTUtil.setEntityHeader(bucket, "minecraft:pig");
        }
        NBTUtil.addEntitySnapshot(bucket, snapshot);
    }

    private static void addCodSnapshot(GameTestHelper helper, ItemStack bucket) {
        Cod storedCod = EntityType.COD.create(helper.getLevel());
        GameTestSupport.check(storedCod != null, "Could not create stored cod fixture");
        CompoundTag snapshot = new CompoundTag();
        storedCod.saveWithoutId(snapshot);
        if (NBTUtil.getEntityCount(bucket) == 0) {
            NBTUtil.setEntityHeader(bucket, "minecraft:cod");
        }
        NBTUtil.addEntitySnapshot(bucket, snapshot);
    }
}
