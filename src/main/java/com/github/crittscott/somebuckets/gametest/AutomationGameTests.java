package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
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
    public static void dispenser_big_bucket_collects_full_cauldron(GameTestHelper helper) {
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(
                helper, DISPENSER, Direction.EAST, GameTestSupport.big8());
        helper.setBlock(FRONT, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertFluid(dispenser.getItem(0), Fluids.WATER, 1000);
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
}
