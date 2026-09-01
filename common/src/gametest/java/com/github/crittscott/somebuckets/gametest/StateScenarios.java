package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

final class StateScenarios {
    private StateScenarios() {}
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
        GameTestSupport.check(SoundEvents.COW_MILK != null,
                "Automated milking sound constant was missing");
        helper.succeed();
    }
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
    static void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.DIAMOND, 3);
        GameTestSupport.updateCustomData(first, tag -> tag.putString("Marker", "first"));
        ItemStack second = new ItemStack(Items.APPLE, 7);
        ItemStack bucket = GameTestSupport.junk();

        BucketState.setStoredItems(bucket, List.of(first, ItemStack.EMPTY, second));

        GameTestSupport.assertStored(helper, bucket, first, second);
        helper.succeed();
    }
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
    static void negative_content_setters_fail_without_mutation(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.big8();
        ItemStack powder = GameTestSupport.big8();

        expectIllegalArgument(() -> BucketState.setMilkAmount(milk, -1), "Negative milk amount was accepted");
        expectIllegalArgument(() -> BucketState.setPowderUnits(powder, -1),
                "Negative powder-snow count was accepted");

        GameTestSupport.assertNoBucketState(milk, "rejected milk write");
        GameTestSupport.assertNoBucketState(powder, "rejected powder write");
        helper.succeed();
    }
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
    static void entity_snapshots_are_fifo_and_final_removal_is_canonical(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        CompoundTag first = new CompoundTag();
        first.putString("Marker", "first");
        CompoundTag second = new CompoundTag();
        second.putString("Marker", "second");
        GameTestSupport.updateCustomData(bucket, tag -> tag.putString("Unrelated", "preserve-me"));
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", first);
        BucketState.addEntitySnapshot(bucket, "minecraft:pig", second);

        GameTestSupport.check("first".equals(BucketState.removeFirstEntitySnapshot(bucket).getString("Marker")),
                "First entity snapshot did not leave first");
        GameTestSupport.check("second".equals(BucketState.removeFirstEntitySnapshot(bucket).getString("Marker")),
                "Second entity snapshot did not leave second");

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(bucket).getString("Unrelated")),
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

    static void variable_stack_size_tracks_fill_state(GameTestHelper helper) {
        ItemStack big = GameTestSupport.big8();
        GameTestSupport.check(big.getMaxStackSize() == 16,
                "Empty Big Bucket max stack size was " + big.getMaxStackSize());

        GameTestSupport.fluid(big, Fluids.WATER, 1000);
        GameTestSupport.check(big.getMaxStackSize() == 1,
                "Filled Big Bucket max stack size was " + big.getMaxStackSize());

        BucketState.clearBucket(big);
        GameTestSupport.check(big.getMaxStackSize() == 16,
                "Emptied Big Bucket max stack size was " + big.getMaxStackSize());

        ItemStack junk = GameTestSupport.junk();
        GameTestSupport.check(junk.getMaxStackSize() == 16,
                "Empty Junk Bucket max stack size was " + junk.getMaxStackSize());

        BucketState.setStoredItems(junk, List.of(new ItemStack(Items.APPLE)));
        GameTestSupport.check(junk.getMaxStackSize() == 1,
                "Occupied Junk Bucket max stack size was " + junk.getMaxStackSize());
        helper.succeed();
    }

    private static String firstTooltipJson(GameTestHelper helper, ItemStack stack) {
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, null, tooltip, TooltipFlag.Default.NORMAL);
        if (tooltip.isEmpty()) {
            throw new GameTestAssertException("Bucket produced no tooltip");
        }
        return Component.Serializer.toJson(tooltip.get(0), helper.getLevel().registryAccess());
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
