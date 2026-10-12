package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

final class StateScenarios {
    private StateScenarios() {}
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

    private static ItemStack load(ItemStack bucket, CompoundTag tag) {
        CompoundTag saved = new CompoundTag();
        saved.putString("id", BuiltInRegistries.ITEM.getKey(bucket.getItem()).toString());
        saved.putByte("Count", (byte) 1);
        saved.put("tag", tag);
        return ItemStack.of(saved);
    }
    static void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.DIAMOND, 3);
        first.getOrCreateTag().putString("Marker", "first");
        ItemStack second = new ItemStack(Items.APPLE, 7);
        ItemStack bucket = GameTestSupport.junk();

        BucketState.setStoredItems(bucket, List.of(first, ItemStack.EMPTY, second));

        GameTestSupport.assertStored(bucket, first, second);
        helper.succeed();
    }
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
    static void negative_content_setters_fail_without_mutation(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.big8();
        ItemStack powder = GameTestSupport.big8();

        expectIllegalArgument(() -> BucketState.setMilkAmount(milk, -1), "Negative milk amount was accepted");
        expectIllegalArgument(() -> BucketState.setPowderUnits(powder, -1),
                "Negative powder-snow count was accepted");

        GameTestSupport.check(milk.getTag() == null, "Rejected milk write attached NBT");
        GameTestSupport.check(powder.getTag() == null, "Rejected powder write attached NBT");
        helper.succeed();
    }
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
    static void entity_snapshots_are_fifo_and_final_removal_is_canonical(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        CompoundTag first = new CompoundTag();
        first.putString("Marker", "first");
        CompoundTag second = new CompoundTag();
        second.putString("Marker", "second");
        bucket.getOrCreateTag().putString("Unrelated", "preserve-me");
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", first);
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", second);

        GameTestSupport.check("first".equals(BucketState.removeFirstEntitySnapshot(bucket).getString("Marker")),
                "First entity snapshot did not leave first");
        GameTestSupport.check("second".equals(BucketState.removeFirstEntitySnapshot(bucket).getString("Marker")),
                "Second entity snapshot did not leave second");

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check("preserve-me".equals(bucket.getOrCreateTag().getString("Unrelated")),
                "Final entity removal discarded unrelated NBT");
        helper.succeed();
    }
    static void finite_crafting_remainders_consume_one_unit(GameTestHelper helper) {
        ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 2000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 2);

        ItemStack fluidRemainder = ((BBItem) fluid.getItem()).getUnitRemainder(fluid);
        ItemStack milkRemainder = ((BBItem) milk.getItem()).getUnitRemainder(milk);
        ItemStack powderRemainder = ((BBItem) powder.getItem()).getUnitRemainder(powder);

        GameTestSupport.assertFluid(fluidRemainder, Fluids.WATER, 1000);
        GameTestSupport.assertMilk(milkRemainder, 1000);
        GameTestSupport.assertPowder(powderRemainder, 1);
        GameTestSupport.assertFluid(fluid, Fluids.WATER, 2000);
        GameTestSupport.assertMilk(milk, 2000);
        GameTestSupport.assertPowder(powder, 2);
        helper.succeed();
    }
    static void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 1);

        GameTestSupport.assertEmpty(((BBItem) fluid.getItem()).getUnitRemainder(fluid));
        GameTestSupport.assertEmpty(((BBItem) milk.getItem()).getUnitRemainder(milk));
        GameTestSupport.assertEmpty(((BBItem) powder.getItem()).getUnitRemainder(powder));
        helper.succeed();
    }
    static void empty_finite_and_source_buckets_have_no_crafting_remainder(GameTestHelper helper) {
        ItemStack big = GameTestSupport.big8();
        ItemStack source = GameTestSupport.source();

        GameTestSupport.assertEmpty(((BBItem) big.getItem()).getUnitRemainder(big));
        GameTestSupport.assertEmpty(((SBItem) source.getItem()).getUnitRemainder(source));
        helper.succeed();
    }
    static void assigned_source_crafting_remainder_is_unchanged(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        ItemStack remainder = ((SBItem) source.getItem()).getUnitRemainder(source);

        GameTestSupport.assertSameStack(source, remainder, "Source crafting remainder changed assignment");
        GameTestSupport.check(remainder != source, "Crafting remainder returned the original stack instance");
        helper.succeed();
    }

    private static String firstTooltipJson(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, null, tooltip, TooltipFlag.Default.NORMAL);
        if (tooltip.isEmpty()) {
            throw new GameTestAssertException("Bucket produced no tooltip");
        }
        return Component.Serializer.toJson(tooltip.get(0));
    }

    private static void expectIllegalArgument(Runnable action, String failureMessage) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new GameTestAssertException(failureMessage);
    }
}
