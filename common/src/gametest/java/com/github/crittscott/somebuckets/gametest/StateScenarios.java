package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/** Bucket state, stack size, crafting remainder and furnace fuel scenarios shared by both loaders. */
final class StateScenarios {
    private static final int FUEL_SLOT = 1;
    /** Ticks allowed for a furnace block entity to take its fuel and light. */
    private static final int FURNACE_LIGHT_TICKS = 5;
    private static final int INPUT_SLOT = 0;

    private StateScenarios() {}

    /**
     * In game: hover over a Big Bucket of water, a Junk Bucket and a Mob Bucket holding a pig with
     * the game language switched; each tooltip line, including the pig's name, follows the language.
     */
    static void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack junk = GameTestSupport.junk();
        ItemStack mob = GameTestSupport.mob();
        BucketState.addEntitySnapshot(mob, "minecraft:pig", new CompoundTag());

        String bigTooltip = firstTooltipJson(big);
        String junkTooltip = firstTooltipJson(junk);
        String mobTooltip = firstTooltipJson(mob);

        GameTestSupport.check(bigTooltip.contains("\"translate\":\"tooltip.somebuckets.big_bucket.fluid\""),
                "Big Bucket tooltip is not translatable: " + bigTooltip);
        GameTestSupport.check(junkTooltip.contains("\"translate\":\"tooltip.somebuckets.storage_bucket.stacks\""),
                "Storage Bucket tooltip is not translatable: " + junkTooltip);
        GameTestSupport.check(mobTooltip.contains("\"translate\":\"tooltip.somebuckets.mob_bucket.contents\""),
                "Mob Bucket tooltip is not translatable: " + mobTooltip);
        GameTestSupport.check(mobTooltip.contains("\"translate\":\"entity.minecraft.pig\""),
                "Mob Bucket tooltip flattened the entity name: " + mobTooltip);
        helper.succeed();
    }

    /**
     * No in-game form: guards that clearing a bucket removes every content key, including stray ones
     * from another mode, and keeps NBT the mod does not own.
     */
    static void clear_removes_all_content_and_preserves_unrelated_nbt(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");
        stack.getOrCreateTag().putInt(BucketState.POWDER_UNITS, 4);
        stack.getOrCreateTag().putString(BucketState.ENTITY_TYPE, "minecraft:pig");

        BucketState.clearBucket(stack);

        GameTestSupport.assertEmpty(stack);
        CompoundTag remaining = stack.getTag();
        GameTestSupport.check(remaining != null && "preserve-me".equals(remaining.getString("Unrelated")),
                "clearBucket removed unrelated NBT");
        GameTestSupport.check(remaining != null && !remaining.contains(BucketState.FLUID_STACK),
                "FluidStack key survived clear");
        GameTestSupport.check(remaining != null && !remaining.contains(BucketState.POWDER_UNITS),
                "Powder key survived clear");
        GameTestSupport.check(remaining != null && !remaining.contains(BucketState.ENTITY_TYPE),
                "EntityType key survived clear");
        helper.succeed();
    }

    /**
     * In game: put sixteen empty Big Buckets, Huge Buckets, Source Buckets, Junk Buckets, Trash Buckets
     * or Mob Buckets in one slot; a seventeenth starts a new stack. Once a bucket holds something, it
     * no longer stacks: picking up a second Big Bucket of water, holding the same amount as the one in
     * your hand, puts it in another slot.
     */
    static void empty_buckets_stack_to_sixteen_and_filled_ones_do_not_stack(GameTestHelper helper) {
        List<ItemStack> fluidBuckets = List.of(GameTestSupport.big8(), GameTestSupport.big64(), GameTestSupport.source());
        for (ItemStack bucket : fluidBuckets) {
            assertMaxStackSize(bucket, 16, "empty");
            assertMaxStackSize(GameTestSupport.fluid(bucket.copy(), Fluids.WATER, 1000), 1, "water-filled");
        }
        for (ItemStack bucket : List.of(GameTestSupport.junk(), GameTestSupport.trash())) {
            assertMaxStackSize(bucket, 16, "empty");
            ItemStack stored = bucket.copy();
            BucketState.setStoredItems(stored, List.of(new ItemStack(Items.APPLE)));
            assertMaxStackSize(stored, 1, "item-holding");
        }
        ItemStack mob = GameTestSupport.mob();
        assertMaxStackSize(mob, 16, "empty");
        GameTestSupport.addSnapshot(mob, helper.getLevel(), EntityType.PIG);
        assertMaxStackSize(mob, 1, "mob-holding");

        ServerPlayer player = GameTestSupport.serverPlayer(helper, new BlockPos(4, 2, 4));
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));
        GameTestSupport.check(player.getInventory().add(GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000)),
                "Second water Big Bucket did not fit in the inventory");
        GameTestSupport.check(player.getInventory().getItem(0).getCount() == 1,
                "Two water Big Buckets stacked in one slot");
        int waterBuckets = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(GameTestSupport.big8().getItem())) waterBuckets += stack.getCount();
        }
        GameTestSupport.check(waterBuckets == 2, "Expected two water Big Buckets in the inventory, got " + waterBuckets);
        helper.succeed();
    }

    /**
     * No in-game form: guards the crafting remainder a modded recipe that consumes an empty Big
     * Bucket or an unassigned Source Bucket would receive: nothing.
     */
    static void empty_finite_and_source_buckets_have_no_crafting_remainder(GameTestHelper helper) {
        GameTestSupport.check(GameTestSupport.craftingRemainder(GameTestSupport.big8()).isEmpty(),
                "Empty Big Bucket left a crafting remainder");
        GameTestSupport.check(GameTestSupport.craftingRemainder(GameTestSupport.source()).isEmpty(),
                "Empty Source Bucket left a crafting remainder");
        helper.succeed();
    }

    /**
     * No in-game form: guards that removing Mob Bucket snapshots returns them oldest first and that
     * removing the last one leaves an empty bucket with NBT the mod does not own intact.
     */
    static void entity_snapshots_are_fifo_and_final_removal_is_canonical(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        CompoundTag first = new CompoundTag();
        first.putString("Marker", "first");
        CompoundTag second = new CompoundTag();
        second.putString("Marker", "second");
        bucket.getOrCreateTag().putString("Unrelated", "preserve-me");
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", first);
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", second);

        GameTestSupport.check("first".equals(BucketState.copyFirstEntitySnapshot(bucket).getString("Marker")),
                "First entity snapshot is not first");
        BucketState.removeFirstEntitySnapshot(bucket);
        GameTestSupport.check("second".equals(BucketState.copyFirstEntitySnapshot(bucket).getString("Marker")),
                "Second entity snapshot did not follow the first");
        BucketState.removeFirstEntitySnapshot(bucket);

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check("preserve-me".equals(bucket.getOrCreateTag().getString("Unrelated")),
                "Final entity removal discarded unrelated NBT");
        helper.succeed();
    }

    /**
     * No in-game form: guards the crafting remainder a modded recipe that consumes a Big Bucket
     * holding its last unit of water, milk or powder snow would receive: an empty Big Bucket.
     */
    static void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        GameTestSupport.assertEmpty(GameTestSupport.craftingRemainder(
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000)));
        GameTestSupport.assertEmpty(GameTestSupport.craftingRemainder(GameTestSupport.milk(GameTestSupport.big8(), 1000)));
        GameTestSupport.assertEmpty(GameTestSupport.craftingRemainder(GameTestSupport.powder(GameTestSupport.big8(), 1)));
        helper.succeed();
    }

    /**
     * No in-game form: guards that draining part of a Big Bucket's milk keeps the rest, and that
     * draining the rest leaves an empty bucket with NBT the mod does not own intact.
     */
    static void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.milk(GameTestSupport.big8(), 1500);
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");

        int partial = BucketState.drainFiniteContent(stack, 600);

        GameTestSupport.check(partial == 600, "Partial milk drain reported " + partial + " mB");
        GameTestSupport.assertMilk(stack, 900);

        int finalDrain = BucketState.drainFiniteContent(stack, 900);

        GameTestSupport.check(finalDrain == 900, "Final milk drain reported " + finalDrain + " mB");
        GameTestSupport.assertEmpty(stack);
        GameTestSupport.check("preserve-me".equals(stack.getOrCreateTag().getString("Unrelated")),
                "Milk drain removed unrelated NBT");
        helper.succeed();
    }

    /**
     * No in-game form: guards the crafting remainder a modded recipe that consumes a Big Bucket
     * holding two units of water, milk or powder snow would receive: the bucket with one unit left,
     * while the consumed stack itself is unchanged.
     */
    static void finite_crafting_remainders_consume_one_unit(GameTestHelper helper) {
        ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 2000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 2);

        GameTestSupport.assertFluid(GameTestSupport.craftingRemainder(fluid), Fluids.WATER, 1000);
        GameTestSupport.assertMilk(GameTestSupport.craftingRemainder(milk), 1000);
        GameTestSupport.assertPowder(GameTestSupport.craftingRemainder(powder), 1);
        GameTestSupport.assertFluid(fluid, Fluids.WATER, 2000);
        GameTestSupport.assertMilk(milk, 2000);
        GameTestSupport.assertPowder(powder, 2);
        helper.succeed();
    }

    /**
     * In game: fill a Big Bucket with water, then empty it into a water source, and do the same with
     * lava; each plays the matching vanilla bucket fill or empty sound.
     */
    static void fluid_bucket_sounds_follow_the_loader_fluid(GameTestHelper helper) {
        BucketOperations operations = BucketOperations.get();
        GameTestSupport.check(operations.fillSound(new StoredFluid(Fluids.WATER, 1000))
                        .orElse(null) == SoundEvents.BUCKET_FILL,
                "Water fill did not resolve to the vanilla fill sound");
        GameTestSupport.check(operations.emptySound(new StoredFluid(Fluids.LAVA, 1000))
                        .orElse(null) == SoundEvents.BUCKET_EMPTY_LAVA,
                "Lava empty did not resolve to the vanilla lava-empty sound");
        GameTestSupport.check(operations.fillSound(new StoredFluid(Fluids.LAVA, 1000))
                        .orElse(null) == SoundEvents.BUCKET_FILL_LAVA,
                "Lava fill did not resolve to the vanilla lava-fill sound");
        helper.succeed();
    }

    /**
     * In game: put cobblestone in a furnace and a Big Bucket holding two units of lava in the fuel
     * slot; the furnace lights and the fuel slot keeps the Big Bucket with one unit left. With one
     * unit of lava, the fuel slot keeps an empty Big Bucket.
     */
    static void lava_big_bucket_burns_one_unit_in_furnace(GameTestHelper helper) {
        BlockPos twoUnits = new BlockPos(2, 2, 4);
        BlockPos oneUnit = new BlockPos(6, 2, 4);
        AbstractFurnaceBlockEntity twoUnitFurnace = furnace(helper, twoUnits,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 2000));
        AbstractFurnaceBlockEntity oneUnitFurnace = furnace(helper, oneUnit,
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000));

        helper.runAfterDelay(FURNACE_LIGHT_TICKS, () -> {
            assertLit(helper, twoUnits, true);
            assertLit(helper, oneUnit, true);
            ItemStack twoUnitFuel = twoUnitFurnace.getItem(FUEL_SLOT);
            GameTestSupport.check(twoUnitFuel.is(GameTestSupport.big8().getItem()) && twoUnitFuel.getCount() == 1,
                    "Fuel slot did not keep one Big Bucket: " + twoUnitFuel);
            GameTestSupport.assertFluid(twoUnitFuel, Fluids.LAVA, 1000);
            ItemStack oneUnitFuel = oneUnitFurnace.getItem(FUEL_SLOT);
            GameTestSupport.check(oneUnitFuel.is(GameTestSupport.big8().getItem()) && oneUnitFuel.getCount() == 1,
                    "Fuel slot did not keep an empty Big Bucket: " + oneUnitFuel);
            GameTestSupport.assertEmpty(oneUnitFuel);
            helper.succeed();
        });
    }

    /**
     * In game: put cobblestone in a furnace and a lava Source Bucket in the fuel slot; the furnace
     * lights and the fuel slot keeps the same lava Source Bucket.
     */
    static void lava_source_bucket_is_permanent_furnace_fuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 4);
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);
        ItemStack before = source.copy();
        AbstractFurnaceBlockEntity furnace = furnace(helper, pos, source);

        helper.runAfterDelay(FURNACE_LIGHT_TICKS, () -> {
            assertLit(helper, pos, true);
            GameTestSupport.assertSameStack(before, furnace.getItem(FUEL_SLOT), "Furnace consumed the lava Source Bucket");
            helper.succeed();
        });
    }

    /**
     * Loading saved bucket state removes impossible content and keeps unrelated NBT. In game: a
     * world where a Big Bucket's saved NBT was edited to hold zero milk, or more powder snow than it
     * fits, loads the bucket as empty, with the removed state written to the log.
     */
    static void loading_removes_malformed_content_and_keeps_unrelated_nbt(GameTestHelper helper) {
        CompoundTag zeroMilk = new CompoundTag();
        zeroMilk.putString(BucketState.MODE, "milk");
        zeroMilk.putInt(BucketState.AMOUNT, 0);
        zeroMilk.putString("Unrelated", "preserve-me");
        CompoundTag overfull = new CompoundTag();
        overfull.putString(BucketState.MODE, "powder_snow");
        overfull.putInt(BucketState.POWDER_UNITS, 9);
        CompoundTag strayFluid = new CompoundTag();
        strayFluid.putString(BucketState.MODE, "fluid");

        ItemStack milk = load(GameTestSupport.big8(), zeroMilk);
        ItemStack powder = load(GameTestSupport.big8(), overfull);
        ItemStack fluid = load(GameTestSupport.junk(), strayFluid);

        GameTestSupport.assertEmpty(milk);
        GameTestSupport.assertEmpty(powder);
        GameTestSupport.assertEmpty(fluid);
        GameTestSupport.check("preserve-me".equals(milk.getOrCreateTag().getString("Unrelated")),
                "Load-time admission removed unrelated NBT");
        GameTestSupport.check(!powder.getOrCreateTag().contains(BucketState.POWDER_UNITS),
                "Overfull powder snow survived loading");
        helper.succeed();
    }

    /**
     * In game: put cobblestone in three furnaces, with a Big Bucket holding less than a unit of lava,
     * a full Big Bucket of water, and a full Big Bucket of milk in the fuel slots; none lights and each
     * fuel slot keeps its bucket.
     */
    static void non_lava_buckets_do_not_fuel_a_furnace(GameTestHelper helper) {
        List<BlockPos> positions = List.of(new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(6, 2, 4));
        List<ItemStack> fuels = List.of(GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 999),
                GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 8000),
                GameTestSupport.milk(GameTestSupport.big8(), 8000));
        List<AbstractFurnaceBlockEntity> furnaces = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            furnaces.add(furnace(helper, positions.get(i), fuels.get(i).copy()));
        }

        helper.runAfterDelay(FURNACE_LIGHT_TICKS, () -> {
            for (int i = 0; i < positions.size(); i++) {
                assertLit(helper, positions.get(i), false);
                GameTestSupport.assertSameStack(fuels.get(i), furnaces.get(i).getItem(FUEL_SLOT),
                        "Furnace consumed a bucket that is not fuel");
            }
            helper.succeed();
        });
    }

    /**
     * No in-game form: guards that reading a bucket that has never held anything reports it empty
     * without adding NBT, so it keeps stacking with other new buckets.
     */
    static void pristine_bucket_reads_do_not_attach_nbt(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();

        GameTestSupport.check(stack.getTag() == null, "Pristine stack unexpectedly had NBT");
        GameTestSupport.check(BucketState.isEmptyBucket(stack), "Pristine bucket was not empty");
        GameTestSupport.check(BucketState.getMode(stack) == BucketState.Mode.NONE, "Pristine bucket had a content mode");
        GameTestSupport.check(BucketState.getAmount(stack) == 0, "Pristine bucket had an amount");
        GameTestSupport.check(BucketState.getStoredFluid(stack).isEmpty(), "Pristine bucket had fluid");
        GameTestSupport.check(BucketState.getPowderUnits(stack) == 0, "Pristine bucket had powder snow");
        GameTestSupport.check(BucketState.getEntityCount(stack) == 0, "Pristine bucket had entities");
        GameTestSupport.check(BucketState.getCurrentEntityType(stack) == null, "Pristine bucket had an entity type");
        GameTestSupport.check(BucketState.getStoredItems(stack).isEmpty(), "Pristine bucket had stored items");
        GameTestSupport.check(stack.getTag() == null, "Reading a pristine bucket attached NBT");
        helper.succeed();
    }

    /**
     * No in-game form: guards that a stored-item list read from a Junk Bucket is a detached copy, and
     * that writing an empty list removes the list and, when nothing else remains, the whole tag.
     */
    static void stored_item_reads_are_detached_and_empty_writes_clean_tags(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        bucket.getOrCreateTag().putString("Unrelated", "preserve-me");
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 4)));

        List<ItemStack> detached = BucketState.getStoredItems(bucket);
        detached.get(0).grow(10);
        detached.clear();

        GameTestSupport.assertStored(bucket, new ItemStack(Items.APPLE, 4));
        BucketState.setStoredItems(bucket, List.of(ItemStack.EMPTY));
        GameTestSupport.assertStored(bucket);
        GameTestSupport.check("preserve-me".equals(bucket.getOrCreateTag().getString("Unrelated")),
                "Clearing stored items removed unrelated NBT");

        ItemStack cleanBucket = GameTestSupport.junk();
        BucketState.setStoredItems(cleanBucket, List.of(new ItemStack(Items.DIAMOND)));
        BucketState.setStoredItems(cleanBucket, List.of());
        GameTestSupport.check(cleanBucket.getTag() == null,
                "Clearing the only stored-item state retained an empty root tag");
        helper.succeed();
    }

    /**
     * No in-game form: guards that a Junk Bucket's stored stacks keep their order, counts and NBT
     * through a write and read, and that an empty entry is dropped.
     */
    static void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.DIAMOND, 3);
        first.getOrCreateTag().putString("Marker", "first");
        ItemStack second = new ItemStack(Items.APPLE, 7);
        ItemStack bucket = GameTestSupport.junk();

        BucketState.setStoredItems(bucket, List.of(first, ItemStack.EMPTY, second));

        GameTestSupport.assertStored(bucket, first, second);
        helper.succeed();
    }

    /**
     * A saved Junk Bucket entry that no longer loads is set aside rather than lost; the tooltip
     * reports it and the bucket does not count as empty. In game: store an item from another mod in a
     * Junk Bucket, remove that mod, and reload the world.
     */
    static void unloadable_junk_entry_is_set_aside(GameTestHelper helper) {
        CompoundTag missing = new CompoundTag();
        missing.putString("id", "somebuckets_gametest_missing:widget");
        missing.putByte("Count", (byte) 3);
        ListTag entries = new ListTag();
        entries.add(new ItemStack(Items.DIAMOND, 2).save(new CompoundTag()));
        entries.add(missing);
        CompoundTag saved = new CompoundTag();
        saved.put(BucketState.JUNK_ITEMS, entries);

        ItemStack bucket = load(GameTestSupport.junk(), saved);

        GameTestSupport.assertStored(bucket, new ItemStack(Items.DIAMOND, 2));
        GameTestSupport.check(BucketState.getSetAsideCount(bucket) == 1, "Unloadable entry was not set aside");
        GameTestSupport.check(missing.equals(bucket.getOrCreateTag()
                        .getList(BucketState.JUNK_SET_ASIDE, Tag.TAG_COMPOUND).getCompound(0)),
                "Set-aside entry was not kept verbatim");
        GameTestSupport.check(!BucketState.isEmptyBucket(bucket), "A bucket with a set-aside entry counted as empty");
        List<Component> tooltip = new ArrayList<>();
        bucket.getItem().appendHoverText(bucket, null, tooltip, TooltipFlag.Default.NORMAL);
        GameTestSupport.check(tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
                        && "tooltip.somebuckets.storage_bucket.set_aside".equals(contents.getKey())),
                "Tooltip did not report the set-aside entry: " + tooltip);

        ItemStack second = new ItemStack(Items.APPLE, 5);
        ListTag aside = new ListTag();
        aside.add(second.save(new CompoundTag()));
        CompoundTag readable = new CompoundTag();
        readable.put(BucketState.JUNK_SET_ASIDE, aside);
        ItemStack restored = load(GameTestSupport.junk(), readable);
        GameTestSupport.assertStored(restored, second);
        GameTestSupport.check(BucketState.getSetAsideCount(restored) == 0,
                "A set-aside entry that loads again was not restored");
        helper.succeed();
    }

    /**
     * No in-game form: guards that setting zero milk, zero powder snow, or empty fluid leaves a
     * bucket with no NBT at all, so it stacks with a new one.
     */
    static void zero_content_mutators_leave_canonical_empty_state(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 0);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 0);
        ItemStack fluid = GameTestSupport.big8();
        BucketState.setStoredFluid(fluid, StoredFluid.EMPTY);

        GameTestSupport.assertEmpty(milk);
        GameTestSupport.assertEmpty(powder);
        GameTestSupport.assertEmpty(fluid);
        GameTestSupport.check(milk.getTag() == null, "Zero milk setter retained empty NBT");
        GameTestSupport.check(powder.getTag() == null, "Zero powder setter retained empty NBT");
        GameTestSupport.check(fluid.getTag() == null, "Empty fluid setter retained empty NBT");
        helper.succeed();
    }

    private static void assertLit(GameTestHelper helper, BlockPos pos, boolean expected) {
        GameTestSupport.check(helper.getBlockState(pos).getValue(AbstractFurnaceBlock.LIT) == expected,
                "Furnace at " + pos + (expected ? " did not light" : " lit"));
    }

    private static void assertMaxStackSize(ItemStack stack, int expected, String label) {
        GameTestSupport.check(stack.getMaxStackSize() == expected, "A " + label + " " + stack.getItem()
                + " stacked to " + stack.getMaxStackSize() + " instead of " + expected);
    }

    private static String firstTooltipJson(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, null, tooltip, TooltipFlag.Default.NORMAL);
        if (tooltip.isEmpty()) {
            throw new GameTestAssertException("Bucket produced no tooltip");
        }
        return Component.Serializer.toJson(tooltip.get(0));
    }

    /** A furnace at {@code pos} smelting cobblestone with {@code fuel} in its fuel slot. */
    private static AbstractFurnaceBlockEntity furnace(GameTestHelper helper, BlockPos pos, ItemStack fuel) {
        helper.setBlock(pos, Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getBlockEntity(pos);
        GameTestSupport.check(furnace != null, "Furnace block entity was not created");
        furnace.setItem(INPUT_SLOT, new ItemStack(Items.COBBLESTONE));
        furnace.setItem(FUEL_SLOT, fuel);
        return furnace;
    }

    private static ItemStack load(ItemStack bucket, CompoundTag tag) {
        CompoundTag saved = new CompoundTag();
        saved.putString("id", BuiltInRegistries.ITEM.getKey(bucket.getItem()).toString());
        saved.putByte("Count", (byte) 1);
        saved.put("tag", tag);
        return ItemStack.of(saved);
    }
}
