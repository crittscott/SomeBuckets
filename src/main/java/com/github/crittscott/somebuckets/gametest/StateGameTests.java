package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class StateGameTests {
    private StateGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void pristine_bucket_reads_do_not_attach_nbt(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();

        GameTestSupport.check(stack.getTag() == null, "Pristine stack unexpectedly had NBT");
        GameTestSupport.check(NBTUtil.isEmptyBucket(stack), "Pristine bucket was not empty");
        GameTestSupport.check(NBTUtil.getMode(stack) == NBTUtil.Mode.NONE, "Pristine bucket had a content mode");
        GameTestSupport.check(NBTUtil.getAmount(stack) == 0, "Pristine bucket had an amount");
        GameTestSupport.check(NBTUtil.getFluidStack(stack).isEmpty(), "Pristine bucket had fluid");
        GameTestSupport.check(NBTUtil.getPowderUnits(stack) == 0, "Pristine bucket had powder snow");
        GameTestSupport.check(NBTUtil.getEntityCount(stack) == 0, "Pristine bucket had entities");
        GameTestSupport.check(NBTUtil.getCurrentEntityType(stack) == null, "Pristine bucket had an entity type");
        GameTestSupport.check(NBTUtil.getStoredItems(stack).isEmpty(), "Pristine bucket had stored items");
        GameTestSupport.check(stack.getTag() == null, "Reading a pristine bucket attached NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void clear_removes_all_content_and_preserves_unrelated_nbt(GameTestHelper helper) {
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
    public static void zero_content_modes_normalize_to_none(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 0);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 0);
        ItemStack fluid = GameTestSupport.big8();
        NBTUtil.setFluidStack(fluid, FluidStack.EMPTY);

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
    public static void stored_items_round_trip_with_order_counts_and_tags(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.DIAMOND, 3);
        first.getOrCreateTag().putString("Marker", "first");
        ItemStack second = new ItemStack(Items.APPLE, 7);
        ItemStack bucket = GameTestSupport.junk();

        NBTUtil.setStoredItems(bucket, List.of(first, ItemStack.EMPTY, second));

        GameTestSupport.assertStored(bucket, first, second);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void bucket_tooltips_preserve_translatable_components(GameTestHelper helper) {
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
    public static void entity_snapshots_are_fifo_and_final_removal_normalizes(GameTestHelper helper) {
        ItemStack bucket = GameTestSupport.mob();
        CompoundTag first = new CompoundTag();
        first.putString("Marker", "first");
        CompoundTag second = new CompoundTag();
        second.putString("Marker", "second");
        NBTUtil.setEntityHeader(bucket, "minecraft:pig");
        NBTUtil.addEntitySnapshot(bucket, first);
        NBTUtil.addEntitySnapshot(bucket, second);

        GameTestSupport.check("first".equals(NBTUtil.removeFirstEntitySnapshot(bucket).getString("Marker")),
                "First entity snapshot did not leave first");
        GameTestSupport.check("second".equals(NBTUtil.removeFirstEntitySnapshot(bucket).getString("Marker")),
                "Second entity snapshot did not leave second");
        NBTUtil.normalizeEmptyState(bucket);

        GameTestSupport.assertEmpty(bucket);
        GameTestSupport.check(bucket.getTag() == null, "Final entity removal retained empty NBT");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void finite_crafting_remainders_consume_one_unit(GameTestHelper helper) {
        ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 2000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 2000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 2);

        ItemStack fluidRemainder = fluid.getCraftingRemainingItem();
        ItemStack milkRemainder = milk.getCraftingRemainingItem();
        ItemStack powderRemainder = powder.getCraftingRemainingItem();

        GameTestSupport.assertFluid(fluidRemainder, Fluids.WATER, 1000);
        GameTestSupport.assertMilk(milkRemainder, 1000);
        GameTestSupport.assertPowder(powderRemainder, 1);
        GameTestSupport.assertFluid(fluid, Fluids.WATER, 2000);
        GameTestSupport.assertMilk(milk, 2000);
        GameTestSupport.assertPowder(powder, 2);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 1);

        GameTestSupport.assertEmpty(fluid.getCraftingRemainingItem());
        GameTestSupport.assertEmpty(powder.getCraftingRemainingItem());
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void assigned_source_crafting_remainder_is_unchanged(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        ItemStack remainder = source.getCraftingRemainingItem();

        GameTestSupport.assertSameStack(source, remainder, "Source crafting remainder changed assignment");
        GameTestSupport.check(remainder != source, "Crafting remainder returned the original stack instance");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_simulation_does_not_mutate(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();
        IFluidHandlerItem handler = fluidHandler(stack);

        int filled = handler.fill(new FluidStack(Fluids.WATER, 3000), IFluidHandler.FluidAction.SIMULATE);

        GameTestSupport.check(filled == 3000, "Simulated fill reported " + filled + " instead of 3000");
        GameTestSupport.assertEmpty(stack);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_honors_capacity_and_clears_on_final_drain(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.big8();
        IFluidHandlerItem handler = fluidHandler(stack);

        int filled = handler.fill(new FluidStack(Fluids.WATER, 9000), IFluidHandler.FluidAction.EXECUTE);
        FluidStack drained = handler.drain(8000, IFluidHandler.FluidAction.EXECUTE);

        GameTestSupport.check(filled == 8000, "8-unit bucket accepted " + filled + " mB");
        GameTestSupport.check(drained.getFluid() == Fluids.WATER && drained.getAmount() == 8000,
                "Final drain returned " + drained);
        GameTestSupport.assertEmpty(stack);
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void big_bucket_capability_rejects_incompatible_fluid(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.WATER, 1000);
        ItemStack before = stack.copy();
        IFluidHandlerItem handler = fluidHandler(stack);

        int filled = handler.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);

        GameTestSupport.check(filled == 0, "Incompatible fluid fill reported " + filled);
        GameTestSupport.assertSameStack(before, stack, "Incompatible fill mutated Big Bucket");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void nonfluid_modes_are_hidden_from_fluid_capability(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 1);

        GameTestSupport.check(fluidHandler(milk).getFluidInTank(0).isEmpty(),
                "Milk appeared as a Forge fluid");
        GameTestSupport.check(fluidHandler(powder).getFluidInTank(0).isEmpty(),
                "Powder snow appeared as a Forge fluid");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void source_capability_is_an_infinite_source_and_sink(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.source();
        IFluidHandlerItem handler = fluidHandler(stack);

        int assigned = handler.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
        int accepted = handler.fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        FluidStack drained = handler.drain(1000, IFluidHandler.FluidAction.EXECUTE);

        GameTestSupport.check(assigned == 500, "Initial Source fill reported " + assigned);
        GameTestSupport.check(accepted == 1000, "Assigned Source accepted " + accepted + " mB");
        GameTestSupport.check(drained.getFluid() == Fluids.WATER && drained.getAmount() == 1000,
                "Source drain returned " + drained);
        GameTestSupport.assertFluid(stack, Fluids.WATER, 1000);
        helper.succeed();
    }

    private static IFluidHandlerItem fluidHandler(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                .orElseThrow(() -> new GameTestAssertException("Bucket exposed no fluid capability"));
    }

    private static String firstTooltipJson(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, null, tooltip, TooltipFlag.Default.NORMAL);
        if (tooltip.isEmpty()) {
            throw new GameTestAssertException("Bucket produced no tooltip");
        }
        return Component.Serializer.toJson(tooltip.get(0));
    }
}
