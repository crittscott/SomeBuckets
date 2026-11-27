package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/** Bucket state, stack size, crafting remainder and furnace fuel scenarios shared by every loader. */
final class StateScenarios {
    private static final int FUEL_SLOT = 1;
    /** Ticks allowed for a furnace block entity to take its fuel and light. */
    private static final int FURNACE_LIGHT_TICKS = 5;
    private static final int INPUT_SLOT = 0;

    private StateScenarios() {}

    /**
     * No in-game form: guards the bucket-sound choice: a fluid's registered fill or empty sound wins,
     * and a fluid without one falls back to vanilla's water or lava bucket sound.
     */
    static void fluid_sound_resolution_prefers_registered_sound_then_fallback(GameTestHelper helper) {
        GameTestSupport.check(FluidPlacement.resolveBucketSound(null, false, true) == SoundEvents.BUCKET_FILL,
                "Water fill did not resolve to the vanilla fill sound");
        GameTestSupport.check(
                FluidPlacement.resolveBucketSound(null, true, false) == SoundEvents.BUCKET_EMPTY_LAVA,
                "Lava empty did not resolve to the vanilla lava-empty sound");

        var custom = SoundEvents.AMETHYST_BLOCK_CHIME;
        GameTestSupport.check(FluidPlacement.resolveBucketSound(custom, true, true) == custom,
                "Registered custom bucket sound did not take precedence");
        GameTestSupport.check(
                FluidPlacement.resolveBucketSound(null, false, true) == SoundEvents.BUCKET_FILL,
                "Missing non-lava fill sound did not use the vanilla fallback");
        GameTestSupport.check(
                FluidPlacement.resolveBucketSound(null, true, false) == SoundEvents.BUCKET_EMPTY_LAVA,
                "Missing lava empty sound did not use the vanilla fallback");
        helper.succeed();
    }

    /**
     * No in-game form: guards that reading a bucket that has never held anything reports it empty
     * without adding a component, so it keeps stacking with other new buckets.
     */
    static void pristine_bucket_reads_do_not_attach_nbt(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();

        GameTestSupport.assertNoBucketState(stack, "Pristine bucket");
        GameTestSupport.check(BucketState.getMode(stack) == BucketState.Mode.NONE, "Pristine bucket had a content mode");
        GameTestSupport.check(BucketState.getAmount(stack) == 0, "Pristine bucket had an amount");
        GameTestSupport.check(BucketState.getStoredFluid(stack).isEmpty(), "Pristine bucket had fluid");
        GameTestSupport.check(BucketState.getPowderUnits(stack) == 0, "Pristine bucket had powder snow");
        GameTestSupport.check(BucketState.getEntityCount(stack) == 0, "Pristine bucket had entities");
        GameTestSupport.check(BucketState.getCurrentEntityType(stack) == null, "Pristine bucket had an entity type");
        GameTestSupport.check(BucketState.getStoredItems(stack).isEmpty(), "Pristine bucket had stored items");
        GameTestSupport.assertNoBucketState(stack, "Pristine bucket after reads");
        helper.succeed();
    }

    /**
     * No in-game form: guards that emptying a bucket removes every bucket-state component and keeps
     * unrelated custom data, such as another mod's tag.
     */
    static void clear_removes_all_content_and_preserves_unrelated_nbt(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        GameTestSupport.updateCustomData(stack, tag -> tag.putString("Unrelated", "preserve-me"));

        BucketState.clearBucket(stack);

        GameTestSupport.assertNoBucketState(stack, "after clearBucket");
        CompoundTag remaining = GameTestSupport.copyCustomData(stack);
        GameTestSupport.check(remaining != null && "preserve-me".equals(remaining.getString("Unrelated")),
                "clearBucket removed unrelated custom data");
        helper.succeed();
    }

    /**
     * No in-game form: guards that setting zero milk, zero powder snow, or no fluid leaves a bucket
     * with no bucket-state component, so it stacks with new empty buckets.
     */
    static void zero_content_mutators_leave_canonical_empty_state(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 0);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 0);
        ItemStack fluid = GameTestSupport.big8();
        BucketState.setStoredFluid(fluid, StoredFluid.EMPTY);

        GameTestSupport.assertNoBucketState(milk, "zero milk setter");
        GameTestSupport.assertNoBucketState(powder, "zero powder setter");
        GameTestSupport.assertNoBucketState(fluid, "empty fluid setter");
        helper.succeed();
    }

    /**
     * No in-game form: guards that a Junk Bucket's stored stacks keep their order, counts and custom
     * data, and that empty entries are dropped.
     */
    static void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.DIAMOND, 3);
        GameTestSupport.updateCustomData(first, tag -> tag.putString("Marker", "first"));
        ItemStack second = new ItemStack(Items.APPLE, 7);
        ItemStack bucket = GameTestSupport.junk();

        BucketState.setStoredItems(bucket, List.of(first, ItemStack.EMPTY, second));

        GameTestSupport.assertStored(helper, bucket, first, second);
        helper.succeed();
    }

    /**
     * No in-game form: guards that changing a read copy of a Junk Bucket's contents does not change
     * the bucket, and that emptying it keeps unrelated custom data and leaves no stored-item component.
     */
    static void stored_item_reads_are_detached_and_empty_writes_clean_tags(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        GameTestSupport.updateCustomData(bucket, tag -> tag.putString("Unrelated", "preserve-me"));
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 4)));

        List<ItemStack> detached = BucketState.getStoredItems(bucket);
        detached.get(0).grow(10);
        detached.clear();

        GameTestSupport.assertStored(helper, bucket, new ItemStack(Items.APPLE, 4));
        BucketState.setStoredItems(bucket, List.of(ItemStack.EMPTY));
        GameTestSupport.assertStored(helper, bucket);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(bucket).getString("Unrelated")),
                "Clearing stored items removed unrelated custom data");

        ItemStack cleanBucket = GameTestSupport.junk();
        BucketState.setStoredItems(cleanBucket, List.of(new ItemStack(Items.DIAMOND)));
        BucketState.setStoredItems(cleanBucket, List.of());
        GameTestSupport.assertNoBucketState(cleanBucket, "after clearing the only stored-item state");
        helper.succeed();
    }

    /**
     * In game: hover over a Big Bucket of water, a Junk Bucket and a Mob Bucket holding a pig with the
     * game language changed; each tooltip, including the pig's name, appears in that language.
     */
    static void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack junk = GameTestSupport.junk();
        ItemStack mob = GameTestSupport.mob();
        BucketState.addEntitySnapshot(mob, "minecraft:pig", new CompoundTag());

        String bigTooltip = firstTooltipJson(helper, big);
        String junkTooltip = firstTooltipJson(helper, junk);
        String mobTooltip = firstTooltipJson(helper, mob);

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
     * Draining part of a Big Bucket's milk leaves the rest, and draining the remainder leaves a
     * canonical empty bucket that keeps unrelated data. No in-game form: it calls the finite-drain
     * state operation that drinking, pouring, and dispensing share.
     */
    static void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.milk(GameTestSupport.big8(), 1500);
        GameTestSupport.updateCustomData(stack, tag -> tag.putString("Unrelated", "preserve-me"));

        int partial = BucketState.drainFiniteContent(stack, 600);

        GameTestSupport.check(partial == 600, "Partial milk drain reported " + partial + " mB");
        GameTestSupport.assertMilk(stack, 900);

        int finalDrain = BucketState.drainFiniteContent(stack, 900);

        GameTestSupport.check(finalDrain == 900, "Final milk drain reported " + finalDrain + " mB");
        GameTestSupport.assertEmpty(stack);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(stack).getString("Unrelated")),
                "Milk drain removed unrelated NBT");
        helper.succeed();
    }

    /**
     * No in-game form: guards that a Mob Bucket releases its stored mobs oldest first and that
     * removing the last one leaves an empty bucket with unrelated custom data kept.
     */
    static void entity_snapshots_are_fifo_and_final_removal_is_canonical(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        CompoundTag first = new CompoundTag();
        first.putString("Marker", "first");
        CompoundTag second = new CompoundTag();
        second.putString("Marker", "second");
        GameTestSupport.updateCustomData(bucket, tag -> tag.putString("Unrelated", "preserve-me"));
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", first);
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", second);

        GameTestSupport.check("first".equals(BucketState.copyFirstEntitySnapshot(bucket).getString("Marker")),
                "First entity snapshot was not first");
        BucketState.removeFirstEntitySnapshot(bucket);
        GameTestSupport.check("second".equals(BucketState.copyFirstEntitySnapshot(bucket).getString("Marker")),
                "Second entity snapshot was not next after the first left");
        BucketState.removeFirstEntitySnapshot(bucket);

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(bucket).getString("Unrelated")),
                "Final entity removal discarded unrelated NBT");
        helper.succeed();
    }

    /**
     * No in-game form: guards that a Mob Bucket's stored mobs survive the trip to the client intact.
     */
    static void entity_snapshot_network_sync_preserves_payloads(GameTestHelper helper) {
        CompoundTag first = new CompoundTag();
        first.putString("Marker", "first");
        CompoundTag second = new CompoundTag();
        second.putInt("HealthMarker", 17);
        ModDataComponentTypes.CapturedMobs original = new ModDataComponentTypes.CapturedMobs(
                ResourceLocation.parse("minecraft:pig"), List.of(first, second));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ModDataComponentTypes.CapturedMobs.STREAM_CODEC.encode(buffer, original);
            ModDataComponentTypes.CapturedMobs decoded =
                    ModDataComponentTypes.CapturedMobs.STREAM_CODEC.decode(buffer);

            GameTestSupport.check(decoded.equals(original),
                    "Mob snapshot network sync discarded or changed entity payloads");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    /**
     * No in-game form: guards the crafting remainder a modded recipe that consumes a filled Big Bucket
     * would receive: the same bucket with one unit of water, milk, or powder snow less.
     */
    static void finite_crafting_remainders_consume_one_unit(GameTestHelper helper) {
        ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 2000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 2);

        ItemStack fluidRemainder = GameTestSupport.craftingRemainder(fluid);
        ItemStack milkRemainder = GameTestSupport.craftingRemainder(milk);
        ItemStack powderRemainder = GameTestSupport.craftingRemainder(powder);

        GameTestSupport.assertFluid(fluidRemainder, Fluids.WATER, 1000);
        GameTestSupport.assertMilk(milkRemainder, 1000);
        GameTestSupport.assertPowder(powderRemainder, 1);
        GameTestSupport.assertFluid(fluid, Fluids.WATER, 2000);
        GameTestSupport.assertMilk(milk, 2000);
        GameTestSupport.assertPowder(powder, 2);
        helper.succeed();
    }

    /**
     * No in-game form: guards that the crafting remainder of a Big Bucket holding its last unit of
     * water, milk, or powder snow is an empty Big Bucket.
     */
    static void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 1);

        GameTestSupport.assertEmpty(GameTestSupport.craftingRemainder(fluid));
        GameTestSupport.assertEmpty(GameTestSupport.craftingRemainder(milk));
        GameTestSupport.assertEmpty(GameTestSupport.craftingRemainder(powder));
        helper.succeed();
    }

    /**
     * No in-game form: guards that an empty Big Bucket or Source Bucket consumed by a modded recipe
     * leaves nothing behind.
     */
    static void empty_finite_and_source_buckets_have_no_crafting_remainder(GameTestHelper helper) {
        ItemStack big = GameTestSupport.big8();
        ItemStack source = GameTestSupport.source();

        GameTestSupport.check(GameTestSupport.craftingRemainder(big).isEmpty(),
                "Empty Big Bucket had a crafting remainder");
        GameTestSupport.check(GameTestSupport.craftingRemainder(source).isEmpty(),
                "Empty Source Bucket had a crafting remainder");
        helper.succeed();
    }

    /**
     * In game: sixteen empty buckets of each kind fit in one slot, while a bucket holding anything
     * stacks alone; picking up a second Big Bucket of water puts it in its own slot even when both hold
     * the same amount.
     */
    static void variable_stack_size_tracks_fill_state(GameTestHelper helper) {
        List<ItemStack> empties = List.of(GameTestSupport.big8(), GameTestSupport.big64(), GameTestSupport.source(),
                GameTestSupport.junk(), GameTestSupport.trash(), GameTestSupport.mob());
        for (ItemStack empty : empties) {
            GameTestSupport.check(empty.getMaxStackSize() == 16,
                    "Empty " + empty.getItem() + " max stack size was " + empty.getMaxStackSize());
        }
        List<ItemStack> filled = List.of(GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000),
                GameTestSupport.fluid(GameTestSupport.big64(), Fluids.WATER, 1000),
                GameTestSupport.fluid(GameTestSupport.source(), Fluids.WATER, 1000),
                stored(GameTestSupport.junk()), stored(GameTestSupport.trash()),
                GameTestSupport.storedMob(helper.getLevel(), EntityType.PIG, null));
        for (ItemStack stack : filled) {
            GameTestSupport.check(stack.getMaxStackSize() == 1,
                    "Filled " + stack.getItem() + " max stack size was " + stack.getMaxStackSize());
        }

        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        BucketState.clearBucket(big);
        GameTestSupport.check(big.getMaxStackSize() == 16,
                "Emptied Big Bucket max stack size was " + big.getMaxStackSize());

        Player player = GameTestSupport.serverPlayer(helper, new BlockPos(4, 2, 4));
        Inventory inventory = player.getInventory();
        inventory.setItem(0, GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000));
        GameTestSupport.check(inventory.add(GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000)),
                "Second water Big Bucket did not fit in the inventory");
        GameTestSupport.check(inventory.getItem(0).getCount() == 1, "Two water Big Buckets stacked");
        int waterBuckets = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(big.getItem())) waterBuckets += inventory.getItem(slot).getCount();
        }
        GameTestSupport.check(waterBuckets == 2, "Expected two separate water Big Buckets, got " + waterBuckets);
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
            GameTestSupport.assertSameStack(before, furnace.getItem(FUEL_SLOT),
                    "Furnace consumed the lava Source Bucket");
            helper.succeed();
        });
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

    private static ItemStack stored(ItemStack bucket) {
        BucketState.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE)));
        return bucket;
    }

    private static AbstractFurnaceBlockEntity furnace(GameTestHelper helper, BlockPos pos, ItemStack fuel) {
        helper.setBlock(pos, Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getBlockEntity(pos);
        GameTestSupport.check(furnace != null, "Furnace block entity was not created");
        furnace.setItem(INPUT_SLOT, new ItemStack(Items.COBBLESTONE));
        furnace.setItem(FUEL_SLOT, fuel);
        return furnace;
    }

    private static void assertLit(GameTestHelper helper, BlockPos pos, boolean lit) {
        GameTestSupport.check(helper.getBlockState(pos).getValue(AbstractFurnaceBlock.LIT) == lit,
                "Furnace at " + pos + (lit ? " did not light" : " lit"));
    }

    private static String firstTooltipJson(GameTestHelper helper, ItemStack stack) {
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, null, tooltip, TooltipFlag.Default.NORMAL);
        if (tooltip.isEmpty()) {
            throw new GameTestAssertException("Bucket produced no tooltip");
        }
        return Component.Serializer.toJson(tooltip.get(0), helper.getLevel().registryAccess());
    }
}
