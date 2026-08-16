package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
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

        GameTestSupport.check(stack.getTag() == null, "Pristine stack unexpectedly had NBT");
        GameTestSupport.check(NBTUtil.isEmptyBucket(stack), "Pristine bucket was not empty");
        GameTestSupport.check(NBTUtil.getMode(stack) == NBTUtil.Mode.NONE, "Pristine bucket had a content mode");
        GameTestSupport.check(NBTUtil.getAmount(stack) == 0, "Pristine bucket had an amount");
        GameTestSupport.check(NBTUtil.getStoredFluid(stack).isEmpty(), "Pristine bucket had fluid");
        GameTestSupport.check(NBTUtil.getPowderUnits(stack) == 0, "Pristine bucket had powder snow");
        GameTestSupport.check(NBTUtil.getEntityCount(stack) == 0, "Pristine bucket had entities");
        GameTestSupport.check(NBTUtil.getCurrentEntityType(stack) == null, "Pristine bucket had an entity type");
        GameTestSupport.check(NBTUtil.getStoredItems(stack).isEmpty(), "Pristine bucket had stored items");
        GameTestSupport.check(stack.getTag() == null, "Reading a pristine bucket attached NBT");
        helper.succeed();
    }
    static void clear_removes_all_content_and_preserves_unrelated_nbt(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");
        stack.getOrCreateTag().putInt(NBTUtil.POWDER_UNITS, 4);
        stack.getOrCreateTag().putString(NBTUtil.ENTITY_TYPE, "minecraft:pig");

        NBTUtil.clearBucket(stack);

        GameTestSupport.assertEmpty(stack);
        CompoundTag remaining = stack.getTag();
        GameTestSupport.check(remaining != null && "preserve-me".equals(remaining.getString("Unrelated")),
                "clearBucket removed unrelated NBT");
        GameTestSupport.check(remaining != null && !remaining.contains(NBTUtil.FLUID_STACK),
                "FluidStack key survived clear");
        GameTestSupport.check(remaining != null && !remaining.contains(NBTUtil.POWDER_UNITS),
                "Powder key survived clear");
        GameTestSupport.check(remaining != null && !remaining.contains(NBTUtil.ENTITY_TYPE),
                "EntityType key survived clear");
        helper.succeed();
    }
    static void zero_content_mutators_leave_canonical_empty_state(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 0);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 0);
        ItemStack fluid = GameTestSupport.big8();
        NBTUtil.setStoredFluid(fluid, StoredFluid.EMPTY);

        GameTestSupport.assertEmpty(milk);
        GameTestSupport.assertEmpty(powder);
        GameTestSupport.assertEmpty(fluid);
        GameTestSupport.check(milk.getTag() == null, "Zero milk setter retained empty NBT");
        GameTestSupport.check(powder.getTag() == null, "Zero powder setter retained empty NBT");
        GameTestSupport.check(fluid.getTag() == null, "Empty fluid setter retained empty NBT");
        GameTestSupport.check(NBTUtil.createPowderSnowTag(0).isEmpty(),
                "Zero powder tag factory created a content mode");
        helper.succeed();
    }
    static void malformed_zero_content_modes_normalize_to_none(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.big8();
        milk.getOrCreateTag().putString(NBTUtil.MODE, "milk");
        milk.getOrCreateTag().putInt(NBTUtil.AMOUNT, 0);
        ItemStack powder = GameTestSupport.big8();
        powder.getOrCreateTag().putString(NBTUtil.MODE, "powder_snow");
        powder.getOrCreateTag().putInt(NBTUtil.POWDER_UNITS, 0);
        ItemStack fluid = GameTestSupport.big8();
        fluid.getOrCreateTag().putString(NBTUtil.MODE, "fluid");

        NBTUtil.normalizeEmptyState(milk);
        NBTUtil.normalizeEmptyState(powder);
        NBTUtil.normalizeEmptyState(fluid);

        GameTestSupport.assertEmpty(milk);
        GameTestSupport.assertEmpty(powder);
        GameTestSupport.assertEmpty(fluid);
        GameTestSupport.check(milk.getTag() == null, "Malformed milk state retained empty NBT");
        GameTestSupport.check(powder.getTag() == null, "Malformed powder state retained empty NBT");
        GameTestSupport.check(fluid.getTag() == null, "Malformed fluid state retained empty NBT");
        helper.succeed();
    }
    static void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.DIAMOND, 3);
        first.getOrCreateTag().putString("Marker", "first");
        ItemStack second = new ItemStack(Items.APPLE, 7);
        ItemStack bucket = GameTestSupport.junk();

        NBTUtil.setStoredItems(bucket, List.of(first, ItemStack.EMPTY, second));

        GameTestSupport.assertStored(bucket, first, second);
        helper.succeed();
    }
    static void stored_item_reads_are_detached_and_empty_writes_clean_tags(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.junk();
        bucket.getOrCreateTag().putString("Unrelated", "preserve-me");
        NBTUtil.setStoredItems(bucket, List.of(new ItemStack(Items.APPLE, 4)));

        List<ItemStack> detached = NBTUtil.getStoredItems(bucket);
        detached.get(0).grow(10);
        detached.clear();

        GameTestSupport.assertStored(bucket, new ItemStack(Items.APPLE, 4));
        NBTUtil.setStoredItems(bucket, List.of(ItemStack.EMPTY));
        GameTestSupport.assertStored(bucket);
        GameTestSupport.check("preserve-me".equals(bucket.getOrCreateTag().getString("Unrelated")),
                "Clearing stored items removed unrelated NBT");

        ItemStack cleanBucket = GameTestSupport.junk();
        NBTUtil.setStoredItems(cleanBucket, List.of(new ItemStack(Items.DIAMOND)));
        NBTUtil.setStoredItems(cleanBucket, List.of());
        GameTestSupport.check(cleanBucket.getTag() == null,
                "Clearing the only stored-item state retained an empty root tag");
        helper.succeed();
    }
    static void negative_content_setters_fail_without_mutation(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.big8();
        ItemStack powder = GameTestSupport.big8();

        expectIllegalArgument(() -> NBTUtil.setMilkAmount(milk, -1), "Negative milk amount was accepted");
        expectIllegalArgument(() -> NBTUtil.setPowderUnits(powder, -1),
                "Negative powder-snow count was accepted");

        GameTestSupport.check(milk.getTag() == null, "Rejected milk write attached NBT");
        GameTestSupport.check(powder.getTag() == null, "Rejected powder write attached NBT");
        helper.succeed();
    }
    static void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack junk = GameTestSupport.junk();
        ItemStack mob = GameTestSupport.mob();
        NBTUtil.addEntitySnapshot(mob, "minecraft:pig", new CompoundTag());

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
        NBTUtil.addEntitySnapshot(bucket, "minecraft:pig", first);
        NBTUtil.addEntitySnapshot(bucket, "minecraft:pig", second);

        GameTestSupport.check("first".equals(NBTUtil.removeFirstEntitySnapshot(bucket).getString("Marker")),
                "First entity snapshot did not leave first");
        GameTestSupport.check("second".equals(NBTUtil.removeFirstEntitySnapshot(bucket).getString("Marker")),
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
