package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

public final class StateGameTests {
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void fluid_sound_resolution_prefers_registered_sound_then_fallback(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void pristine_bucket_reads_do_not_attach_nbt(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void clear_removes_all_content_and_preserves_unrelated_nbt(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void zero_content_modes_normalize_to_none(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 0);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 0);
        ItemStack fluid = GameTestSupport.big8();
        NBTUtil.setStoredFluid(fluid, StoredFluid.EMPTY);

        NBTUtil.normalizeEmptyState(milk);
        NBTUtil.normalizeEmptyState(powder);
        NBTUtil.normalizeEmptyState(fluid);

        GameTestSupport.assertEmpty(milk);
        GameTestSupport.assertEmpty(powder);
        GameTestSupport.assertEmpty(fluid);
        GameTestSupport.check(milk.getTag() == null, "Normalized milk bucket retained empty NBT");
        GameTestSupport.check(powder.getTag() == null, "Normalized powder bucket retained empty NBT");
        GameTestSupport.check(fluid.getTag() == null, "Normalized fluid bucket retained empty NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.DIAMOND, 3);
        first.getOrCreateTag().putString("Marker", "first");
        ItemStack second = new ItemStack(Items.APPLE, 7);
        ItemStack bucket = GameTestSupport.junk();

        NBTUtil.setStoredItems(bucket, List.of(first, ItemStack.EMPTY, second));

        GameTestSupport.assertStored(bucket, first, second);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void stored_item_reads_are_detached_and_empty_writes_clean_tags(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void negative_content_setters_fail_without_mutation(GameTestHelper helper) {
        ItemStack amount = GameTestSupport.big8();
        ItemStack powder = GameTestSupport.big8();

        expectIllegalArgument(() -> NBTUtil.setAmount(amount, -1), "Negative amount was accepted");
        expectIllegalArgument(() -> NBTUtil.setPowderUnits(powder, -1),
                "Negative powder-snow count was accepted");

        GameTestSupport.check(amount.getTag() == null, "Rejected amount write attached NBT");
        GameTestSupport.check(powder.getTag() == null, "Rejected powder write attached NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
        ItemStack big = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack junk = GameTestSupport.junk();
        ItemStack mob = GameTestSupport.mob();
        NBTUtil.setEntityHeader(mob, "minecraft:pig");
        NBTUtil.addEntitySnapshot(mob, new CompoundTag());

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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void entity_snapshots_are_fifo_and_final_removal_normalizes(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        CompoundTag first = new CompoundTag();
        first.putString("Marker", "first");
        CompoundTag second = new CompoundTag();
        second.putString("Marker", "second");
        bucket.getOrCreateTag().putString("Unrelated", "preserve-me");
        NBTUtil.setEntityHeader(bucket, "minecraft:pig");
        NBTUtil.addEntitySnapshot(bucket, first);
        NBTUtil.addEntitySnapshot(bucket, second);

        GameTestSupport.check("first".equals(NBTUtil.removeFirstEntitySnapshot(bucket).getString("Marker")),
                "First entity snapshot did not leave first");
        GameTestSupport.check("second".equals(NBTUtil.removeFirstEntitySnapshot(bucket).getString("Marker")),
                "Second entity snapshot did not leave second");
        NBTUtil.normalizeEmptyState(bucket);

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check("preserve-me".equals(bucket.getOrCreateTag().getString("Unrelated")),
                "Final entity removal discarded unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void finite_crafting_remainders_consume_one_unit(GameTestHelper helper) {
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

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 1);

        GameTestSupport.assertEmpty(((BBItem) fluid.getItem()).getUnitRemainder(fluid));
        GameTestSupport.assertEmpty(((BBItem) milk.getItem()).getUnitRemainder(milk));
        GameTestSupport.assertEmpty(((BBItem) powder.getItem()).getUnitRemainder(powder));
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void empty_finite_and_source_buckets_have_no_crafting_remainder(GameTestHelper helper) {
        ItemStack big = GameTestSupport.big8();
        ItemStack source = GameTestSupport.source();

        GameTestSupport.assertEmpty(((BBItem) big.getItem()).getUnitRemainder(big));
        GameTestSupport.assertEmpty(((SBItem) source.getItem()).getUnitRemainder(source));
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void assigned_source_crafting_remainder_is_unchanged(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        ItemStack remainder = ((SBItem) source.getItem()).getUnitRemainder(source);

        GameTestSupport.assertSameStack(source, remainder, "Source crafting remainder changed assignment");
        GameTestSupport.check(remainder != source, "Crafting remainder returned the original stack instance");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_simulation_does_not_mutate(GameTestHelper helper) {
        SimpleContainer container = GameTestSupport.containerOf(GameTestSupport.big8());
        Storage<FluidVariant> storage = GameTestSupport.fluidStorage(container);

        long filled = GameTestSupport.insert(storage, FluidVariant.of(Fluids.WATER),
                3000L * GameTestSupport.DROPLETS_PER_MB, false);

        GameTestSupport.check(filled == 3000L * GameTestSupport.DROPLETS_PER_MB,
                "Simulated fill moved " + filled + " droplets instead of 3000 mB worth");
        GameTestSupport.assertEmpty(container.getItem(0));
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_partial_drain_and_simulation_preserve_state(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2500);
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");
        ItemStack beforeSimulation = stack.copy();
        SimpleContainer container = GameTestSupport.containerOf(stack);

        long simulated = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 750L * GameTestSupport.DROPLETS_PER_MB, false);

        GameTestSupport.check(simulated == 750L * GameTestSupport.DROPLETS_PER_MB,
                "Simulated partial drain moved " + simulated + " droplets instead of 750 mB worth");
        GameTestSupport.assertSameStack(beforeSimulation, container.getItem(0), "Simulated drain mutated Big Bucket");

        long executed = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 750L * GameTestSupport.DROPLETS_PER_MB, true);

        GameTestSupport.check(executed == 750L * GameTestSupport.DROPLETS_PER_MB,
                "Executed partial drain moved " + executed + " droplets instead of 750 mB worth");
        ItemStack after = container.getItem(0);
        GameTestSupport.assertFluid(after, Fluids.WATER, 1750);
        GameTestSupport.check("preserve-me".equals(after.getOrCreateTag().getString("Unrelated")),
                "Partial drain removed unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_honors_capacity_and_clears_on_final_drain(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");
        SimpleContainer container = GameTestSupport.containerOf(stack);

        long filled = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 9000L * GameTestSupport.DROPLETS_PER_MB, true);
        long drained = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 8000L * GameTestSupport.DROPLETS_PER_MB, true);

        GameTestSupport.check(filled == 8000L * GameTestSupport.DROPLETS_PER_MB,
                "8-unit bucket accepted " + filled + " droplets instead of 8000 mB worth");
        GameTestSupport.check(drained == 8000L * GameTestSupport.DROPLETS_PER_MB,
                "Final drain moved " + drained + " droplets instead of 8000 mB worth");
        ItemStack after = container.getItem(0);
        GameTestSupport.assertEmpty(after);
        GameTestSupport.check("preserve-me".equals(after.getOrCreateTag().getString("Unrelated")),
                "Final drain removed unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.milk(GameTestSupport.big8(), 1500);
        stack.getOrCreateTag().putString("Unrelated", "preserve-me");

        int partial = NBTUtil.drainFiniteContent(stack, 600);

        GameTestSupport.check(partial == 600, "Partial milk drain reported " + partial + " mB");
        GameTestSupport.assertMilk(stack, 900);

        int finalDrain = NBTUtil.drainFiniteContent(stack, 900);

        GameTestSupport.check(finalDrain == 900, "Final milk drain reported " + finalDrain + " mB");
        GameTestSupport.assertEmpty(stack);
        GameTestSupport.check("preserve-me".equals(stack.getOrCreateTag().getString("Unrelated")),
                "Milk drain removed unrelated NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void big_bucket_capability_rejects_incompatible_fluid(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        ItemStack before = stack.copy();
        SimpleContainer container = GameTestSupport.containerOf(stack);

        long filled = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.LAVA), 1000L * GameTestSupport.DROPLETS_PER_MB, true);

        GameTestSupport.check(filled == 0, "Incompatible fluid fill moved " + filled + " droplets");
        GameTestSupport.assertSameStack(before, container.getItem(0), "Incompatible fill mutated Big Bucket");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void nonfluid_modes_are_hidden_from_fluid_capability(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 1);

        GameTestSupport.check(
                GameTestSupport.isEmpty(GameTestSupport.fluidStorage(GameTestSupport.containerOf(milk))),
                "Milk appeared as a Fabric fluid");
        GameTestSupport.check(
                GameTestSupport.isEmpty(GameTestSupport.fluidStorage(GameTestSupport.containerOf(powder))),
                "Powder snow appeared as a Fabric fluid");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_capability_is_an_infinite_source_and_sink(GameTestHelper helper) {
        SimpleContainer container = GameTestSupport.containerOf(GameTestSupport.source());

        long assigned = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 500L * GameTestSupport.DROPLETS_PER_MB, true);
        long accepted = GameTestSupport.insert(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 4000L * GameTestSupport.DROPLETS_PER_MB, true);
        long drained = GameTestSupport.extract(GameTestSupport.fluidStorage(container),
                FluidVariant.of(Fluids.WATER), 1000L * GameTestSupport.DROPLETS_PER_MB, true);

        GameTestSupport.check(assigned == 500L * GameTestSupport.DROPLETS_PER_MB,
                "Initial Source fill moved " + assigned + " droplets instead of 500 mB worth");
        GameTestSupport.check(accepted == 1000L * GameTestSupport.DROPLETS_PER_MB,
                "Assigned Source accepted " + accepted + " droplets instead of 1000 mB worth");
        GameTestSupport.check(drained == 1000L * GameTestSupport.DROPLETS_PER_MB,
                "Source drain moved " + drained + " droplets instead of 1000 mB worth");
        GameTestSupport.assertFluid(container.getItem(0), Fluids.WATER, 1000);
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
