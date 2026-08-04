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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
            GameTestSupport.check(GameTestSupport.entities(helper, ItemEntity.class, FRONT, 4.0D).size() == 1,
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
    public static void dispenser_junk_bucket_ejects_oldest_stack_into_vacant_front(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        ItemStack first = new ItemStack(Items.DIAMOND, 2);
        ItemStack second = new ItemStack(Items.APPLE, 3);
        NBTUtil.setStoredItems(bucket, List.of(first, second));
        DispenserBlockEntity dispenser = GameTestSupport.dispenser(helper, DISPENSER, Direction.EAST, bucket);

        GameTestSupport.triggerDispenser(helper, DISPENSER);
        helper.runAfterDelay(8L, () -> {
            GameTestSupport.assertStored(dispenser.getItem(0), second);
            List<ItemEntity> drops = GameTestSupport.entities(helper, ItemEntity.class, FRONT, 4.0D);
            GameTestSupport.check(drops.size() == 1, "Expected one ejected stack, got " + drops.size());
            GameTestSupport.assertSameStack(first, drops.get(0).getItem(),
                    "Junk Bucket did not eject its oldest stack");
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
