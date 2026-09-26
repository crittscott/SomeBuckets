package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.CapturedMobNetworkRegistry;
import com.github.crittscott.somebuckets.util.LegacyBucketMigration;
import com.github.crittscott.somebuckets.util.StoredFluid;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class StateScenarios {
    private StateScenarios() {}
    /**
     * Automation-only: probes fluid sound selection and verifies registered sounds precede lava and
     * generic fallbacks.
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
    /** Automation-only: reads every bucket-state accessor on pristine stacks and verifies no component is attached. */
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
     * Automation-only: clears bucket content and verifies all owned components disappear while unrelated
     * components survive.
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
     * Automation-only: writes zero-valued fluid, milk, powder, and entity state and requires canonical
     * component-free emptiness.
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
     * Automation-only: serializes and reads stored item entries and verifies order, counts, and component
     * data round-trip.
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
     * Automation-only: a partial Big Bucket holding one fluid variant accepts that variant, refuses the
     * same fluid with different or no variant data, and keeps its variant through item persistence.
     */
    static void partial_bucket_refuses_different_fluid_variant(GameTestHelper helper) {
        StoredFluid variantA = new StoredFluid(Fluids.WATER, 1_000, variantPatch("a"));
        StoredFluid variantB = new StoredFluid(Fluids.WATER, 1_000, variantPatch("b"));
        StoredFluid plain = new StoredFluid(Fluids.WATER, 1_000);
        ItemStack bucket = GameTestSupport.big8();
        BucketState.setStoredFluid(bucket, variantA.withAmount(2_000));

        GameTestSupport.check(BBItem.canAcceptFluidUnit(bucket, variantA),
                "Partial bucket refused its own fluid variant");
        GameTestSupport.check(!BBItem.canAcceptFluidUnit(bucket, variantB),
                "Partial bucket accepted the same fluid with different variant data");
        GameTestSupport.check(!BBItem.canAcceptFluidUnit(bucket, plain),
                "Partial bucket accepted the same fluid without its variant data");

        var registries = helper.getLevel().registryAccess();
        ItemStack reloaded = ItemStack.parse(registries, bucket.save(registries)).orElseThrow();
        StoredFluid restored = BucketState.getStoredFluid(reloaded);
        GameTestSupport.check(restored.isSameVariant(variantA) && restored.amount() == 2_000,
                "Fluid variant data did not survive item persistence: " + restored);
        helper.succeed();
    }
    /**
     * Automation-only: mutates returned stored-item copies and writes empties to verify reads are detached
     * and empty state is removed.
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
    /** Automation-only: rejects invalid component bounds and enclosing item types without mutation. */
    static void negative_content_setters_fail_without_mutation(GameTestHelper helper) {
        ItemStack milk = GameTestSupport.big8();
        ItemStack powder = GameTestSupport.big8();
        ItemStack fluid = GameTestSupport.big8();
        ItemStack source = GameTestSupport.source();
        ItemStack wrongItem = new ItemStack(Items.BUCKET);
        ItemStack junk = GameTestSupport.junk();
        ItemStack mob = GameTestSupport.mob();

        expectIllegalArgument(() -> BucketState.setMilkAmount(milk, -1), "Negative milk amount was accepted");
        expectIllegalArgument(() -> BucketState.setMilkAmount(milk, 8_001),
                "Milk above the Big Bucket capacity was accepted");
        expectIllegalArgument(() -> BucketState.setMilkAmount(milk, 1_500),
                "A fractional-bucket milk amount was accepted");
        expectIllegalArgument(() -> BucketState.setPowderUnits(powder, -1),
                "Negative powder-snow count was accepted");
        expectIllegalArgument(() -> BucketState.setPowderUnits(powder, 9),
                "Powder snow above the Big Bucket capacity was accepted");
        expectIllegalArgument(() -> BucketState.setStoredFluid(fluid,
                        new StoredFluid(Fluids.WATER, 8_001)),
                "Fluid above the Big Bucket capacity was accepted");
        ItemStack overflowProbe = GameTestSupport.big8();
        overflowProbe.set(ModDataComponentTypes.FLUID_CONTENT, new ModDataComponentTypes.FluidContent(
                Fluids.WATER, Integer.MAX_VALUE, DataComponentPatch.EMPTY));
        GameTestSupport.check(!BBItem.canAcceptFluidUnit(
                        overflowProbe, new StoredFluid(Fluids.WATER, 1_000)),
                "Overflowing fluid arithmetic reported room in a full bucket");
        expectIllegalArgument(() -> BucketState.setStoredFluid(source,
                        new StoredFluid(Fluids.WATER, 2_000)),
                "A multi-bucket Source Bucket payload was accepted");
        expectIllegalArgument(() -> BucketState.setMilkAmount(wrongItem, 1_000),
                "Milk was accepted on an unrelated item");
        expectIllegalArgument(() -> BucketState.setStoredItems(junk, List.of(GameTestSupport.junk())),
                "A nested Junk Bucket was accepted");

        List<ItemStack> tooMany = new ArrayList<>();
        for (int i = 0; i < 10; i++) tooMany.add(new ItemStack(Items.APPLE));
        expectIllegalArgument(() -> BucketState.setStoredItems(junk, tooMany),
                "An oversized Junk Bucket payload was accepted");

        for (int i = 0; i < 8; i++) {
            BucketState.addEntitySnapshot(mob, "minecraft:pig", new CompoundTag());
        }
        expectIllegalArgument(() -> BucketState.addEntitySnapshot(mob, "minecraft:pig", new CompoundTag()),
                "A ninth Mob Bucket snapshot was accepted");
        GameTestSupport.check(BucketState.getEntityCount(mob) == 8,
                "Rejected ninth snapshot changed the existing Mob Bucket payload");

        ItemStack craftedJunk = GameTestSupport.junk();
        craftedJunk.set(ModDataComponentTypes.JUNK_CONTENTS,
                new ModDataComponentTypes.JunkContents(List.of(GameTestSupport.trash()), 0L));
        craftedJunk.getItem().inventoryTick(craftedJunk, helper.getLevel(),
                GameTestSupport.serverPlayer(helper, BlockPos.ZERO), 0, false);
        GameTestSupport.assertNoBucketState(craftedJunk, "malicious creative-style junk payload");

        ItemStack craftedMob = GameTestSupport.mob();
        craftedMob.set(ModDataComponentTypes.CAPTURED_MOBS, new ModDataComponentTypes.CapturedMobs(
                1L, 2L, ResourceLocation.parse("minecraft:pig"), List.of(), 8));
        craftedMob.getItem().inventoryTick(craftedMob, helper.getLevel(),
                GameTestSupport.serverPlayer(helper, BlockPos.ZERO), 0, false);
        GameTestSupport.assertNoBucketState(craftedMob, "unresolved creative-style Mob Bucket summary");

        GameTestSupport.assertNoBucketState(milk, "rejected milk write");
        GameTestSupport.assertNoBucketState(powder, "rejected powder write");
        GameTestSupport.assertNoBucketState(fluid, "rejected fluid write");
        GameTestSupport.assertNoBucketState(source, "rejected source write");
        GameTestSupport.assertNoBucketState(wrongItem, "rejected wrong-item write");
        GameTestSupport.assertNoBucketState(junk, "rejected junk write");
        helper.succeed();
    }
    /**
     * Manual: inspect bucket tooltips under another language; their labels and content names remain
     * translatable components.
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
     * Automation-only: appends and removes entity snapshots, verifying FIFO order and canonical empty
     * state after the final removal.
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
    /** Automation-only: verifies compact Mob Bucket wire data and authoritative return resolution. */
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
            byte[] wire = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), wire);
            String rawWire = new String(wire, StandardCharsets.ISO_8859_1);
            GameTestSupport.check(!rawWire.contains("Marker") && !rawWire.contains("HealthMarker"),
                    "Mob snapshot NBT appeared in the network payload");

            CapturedMobNetworkRegistry.clear();
            ModDataComponentTypes.CapturedMobs clientSummary =
                    ModDataComponentTypes.CapturedMobs.STREAM_CODEC.decode(buffer);
            GameTestSupport.check(clientSummary.isSummary() && clientSummary.entities().isEmpty()
                            && clientSummary.count() == 2
                            && clientSummary.entityType().equals(original.entityType()),
                    "Client did not receive the expected type/count-only Mob Bucket summary");

            RegistryFriendlyByteBuf returned = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                ModDataComponentTypes.CapturedMobs.STREAM_CODEC.encode(returned, clientSummary);
                CapturedMobNetworkRegistry.publish(original);
                ModDataComponentTypes.CapturedMobs restored =
                        ModDataComponentTypes.CapturedMobs.STREAM_CODEC.decode(returned);
                GameTestSupport.check(restored.equals(original),
                        "Returned Mob Bucket summary did not restore authoritative snapshots");
            } finally {
                returned.release();
            }

            ModDataComponentTypes.CapturedMobs forged = new ModDataComponentTypes.CapturedMobs(
                    original.contentIdMost(), original.contentIdLeast(), original.entityType(), List.of(), 1);
            RegistryFriendlyByteBuf forgedReturn = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                ModDataComponentTypes.CapturedMobs.STREAM_CODEC.encode(forgedReturn, forged);
                ModDataComponentTypes.CapturedMobs rejected =
                        ModDataComponentTypes.CapturedMobs.STREAM_CODEC.decode(forgedReturn);
                GameTestSupport.check(rejected.isSummary(),
                        "A Mob Bucket token with mismatched display hints resolved as authoritative");
            } finally {
                forgedReturn.release();
            }

            CapturedMobNetworkRegistry.clear();
        } finally {
            buffer.release();
        }
        helper.succeed();
    }
    /**
     * Automation-only: requests crafting remainders for finite filled buckets and verifies exactly one
     * unit is consumed.
     */
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
    /**
     * Automation-only: consumes the final finite unit as a crafting remainder and verifies the returned
     * bucket is canonical empty.
     */
    static void final_finite_crafting_remainder_is_empty(GameTestHelper helper) {
        ItemStack fluid = GameTestSupport.fluid(GameTestSupport.big8(), Fluids.LAVA, 1000);
        ItemStack milk = GameTestSupport.milk(GameTestSupport.big8(), 1000);
        ItemStack powder = GameTestSupport.powder(GameTestSupport.big8(), 1);

        GameTestSupport.assertEmpty(((BBItem) fluid.getItem()).getUnitRemainder(fluid));
        GameTestSupport.assertEmpty(((BBItem) milk.getItem()).getUnitRemainder(milk));
        GameTestSupport.assertEmpty(((BBItem) powder.getItem()).getUnitRemainder(powder));
        helper.succeed();
    }
    /** Automation-only: asks empty finite and Source Buckets for crafting remainders and verifies they provide none. */
    static void empty_finite_and_source_buckets_have_no_crafting_remainder(GameTestHelper helper) {
        ItemStack big = GameTestSupport.big8();
        ItemStack source = GameTestSupport.source();

        GameTestSupport.assertEmpty(((BBItem) big.getItem()).getUnitRemainder(big));
        GameTestSupport.assertEmpty(((SBItem) source.getItem()).getUnitRemainder(source));
        helper.succeed();
    }
    /**
     * Automation-only: requests an assigned Source Bucket's crafting remainder and verifies an unchanged
     * assigned copy.
     */
    static void assigned_source_crafting_remainder_is_unchanged(GameTestHelper helper) {
        ItemStack source = GameTestSupport.fluid(GameTestSupport.source(), Fluids.LAVA, 1000);

        ItemStack remainder = ((SBItem) source.getItem()).getUnitRemainder(source);

        GameTestSupport.assertSameStack(source, remainder, "Source crafting remainder changed assignment");
        GameTestSupport.check(remainder != source, "Crafting remainder returned the original stack instance");
        helper.succeed();
    }

    /**
     * Automation-only: exercises successful migration of every legacy mode, including data-fixed entity
     * and item payloads, plus rejected, atomic, and one-shot migration.
     */
    static void legacy_migration_is_atomic_validated_and_one_shot(GameTestHelper helper) {
        ItemStack valid = GameTestSupport.big8();
        GameTestSupport.updateCustomData(valid, tag -> {
            tag.putString("Unrelated", "preserve-me");
            putLegacyFluid(tag, "minecraft:water", 2_000);
        });
        migrate(helper, valid);
        GameTestSupport.assertFluid(valid, Fluids.WATER, 2_000);
        CompoundTag validRemainder = GameTestSupport.copyCustomData(valid);
        GameTestSupport.check(validRemainder != null
                        && "preserve-me".equals(validRemainder.getString("Unrelated"))
                        && !validRemainder.contains("Mode") && !validRemainder.contains("FluidStack"),
                "Successful migration did not preserve unrelated data or remove legacy keys");

        ItemStack milk = GameTestSupport.big8();
        GameTestSupport.updateCustomData(milk, tag -> {
            tag.putString("Mode", "milk");
            tag.putInt("Amount", 3_000);
        });
        migrate(helper, milk);
        GameTestSupport.assertMilk(milk, 3_000);

        ItemStack powder = GameTestSupport.big8();
        GameTestSupport.updateCustomData(powder, tag -> {
            tag.putString("Mode", "powder_snow");
            tag.putInt("Powder", 4);
        });
        migrate(helper, powder);
        GameTestSupport.assertPowder(powder, 4);

        ItemStack variant = GameTestSupport.big8();
        GameTestSupport.updateCustomData(variant, tag -> {
            putLegacyFluid(tag, "minecraft:water", 1_000);
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString("Marker", "legacy-variant");
            tag.getCompound("FluidStack").put("Tag", fluidTag);
        });
        migrate(helper, variant);
        GameTestSupport.assertFluid(variant, Fluids.WATER, 1_000);
        Optional<? extends CustomData> variantEntry = BucketState.getStoredFluid(variant).components()
                .get(DataComponents.CUSTOM_DATA);
        CustomData variantData = variantEntry == null ? null : variantEntry.orElse(null);
        GameTestSupport.check(variantData != null
                        && "legacy-variant".equals(variantData.copyTag().getString("Marker")),
                "Legacy fluid tag did not become fluid variant data");

        ItemStack pigs = GameTestSupport.mob();
        GameTestSupport.updateCustomData(pigs, tag -> putLegacyEntities(tag, "minecraft:pig", 2));
        migrate(helper, pigs);
        GameTestSupport.check(BucketState.getEntityCount(pigs) == 2
                        && BucketState.getCurrentEntityType(pigs) == EntityType.PIG,
                "Legacy pig snapshots did not migrate with their type and count");
        GameTestSupport.check(BucketState.copyFirstEntitySnapshot(pigs).getInt("LegacyMarker") == 0,
                "Legacy entity snapshots lost their data or FIFO order");

        ItemStack junk = GameTestSupport.junk();
        GameTestSupport.updateCustomData(junk, tag -> {
            ListTag items = new ListTag();
            CompoundTag named = legacyItem(new ItemStack(Items.DIAMOND), 2);
            CompoundTag display = new CompoundTag();
            display.putString("Name", "{\"text\":\"Legacy Gem\"}");
            CompoundTag itemTag = new CompoundTag();
            itemTag.put("display", display);
            named.put("tag", itemTag);
            items.add(named);
            items.add(legacyItem(new ItemStack(Items.APPLE), 5));
            tag.put("JunkItems", items);
            tag.putLong("JunkLayoutSeed", 42L);
        });
        migrate(helper, junk);
        List<ItemStack> junkItems = BucketState.getStoredItems(junk);
        GameTestSupport.check(junkItems.size() == 2
                        && junkItems.get(0).is(Items.DIAMOND) && junkItems.get(0).getCount() == 2
                        && junkItems.get(1).is(Items.APPLE) && junkItems.get(1).getCount() == 5,
                "Legacy junk entries did not migrate in order with their counts: " + junkItems);
        Component customName = junkItems.get(0).get(DataComponents.CUSTOM_NAME);
        GameTestSupport.check(customName != null && "Legacy Gem".equals(customName.getString()),
                "Data fixer did not convert the legacy item display name to a component");
        GameTestSupport.check(BucketState.getJunkLayoutSeed(junk) == 42L,
                "Legacy junk layout seed was not preserved");

        ItemStack unresolved = GameTestSupport.mob();
        GameTestSupport.updateCustomData(unresolved, tag -> putLegacyEntities(
                tag, "missingmod:temporarily_absent", 1));
        migrate(helper, unresolved);
        GameTestSupport.check(BucketState.getEntityCount(unresolved) == 1
                        && BucketState.getCurrentEntityType(unresolved) == null,
                "Unresolved but well-formed legacy entity type was not preserved inertly");

        ItemStack invalidEntityId = GameTestSupport.mob();
        GameTestSupport.updateCustomData(invalidEntityId,
                tag -> putLegacyEntities(tag, "not an id", 1));
        assertQuarantinedOnce(helper, invalidEntityId, "invalid entity id");

        ItemStack blacklisted = GameTestSupport.mob();
        GameTestSupport.updateCustomData(blacklisted,
                tag -> putLegacyEntities(tag, "minecraft:wither", 1));
        assertQuarantinedOnce(helper, blacklisted, "blacklisted entity type");

        ItemStack tooManyMobs = GameTestSupport.mob();
        GameTestSupport.updateCustomData(tooManyMobs,
                tag -> putLegacyEntities(tag, "minecraft:pig", 9));
        assertQuarantinedOnce(helper, tooManyMobs, "excessive captured-mob count");

        ItemStack nested = GameTestSupport.junk();
        GameTestSupport.updateCustomData(nested, tag -> {
            ListTag items = new ListTag();
            items.add(legacyItem(GameTestSupport.junk(), 1));
            tag.put("JunkItems", items);
        });
        assertQuarantinedOnce(helper, nested, "nested storage bucket");

        ItemStack tooManyJunkEntries = GameTestSupport.trash();
        GameTestSupport.updateCustomData(tooManyJunkEntries, tag -> {
            ListTag items = new ListTag();
            items.add(legacyItem(new ItemStack(Items.STONE), 1));
            items.add(legacyItem(new ItemStack(Items.DIRT), 1));
            tag.put("JunkItems", items);
        });
        assertQuarantinedOnce(helper, tooManyJunkEntries, "excessive stored-item count");

        ItemStack negative = GameTestSupport.big8();
        GameTestSupport.updateCustomData(negative, tag -> {
            tag.putString("Mode", "milk");
            tag.putInt("Amount", -1);
        });
        assertQuarantinedOnce(helper, negative, "negative content amount");

        ItemStack excessive = GameTestSupport.big8();
        GameTestSupport.updateCustomData(excessive,
                tag -> putLegacyFluid(tag, "minecraft:water", 9_000));
        assertQuarantinedOnce(helper, excessive, "content amount above bucket capacity");

        ItemStack unreadable = GameTestSupport.junk();
        GameTestSupport.updateCustomData(unreadable, tag -> {
            ListTag items = new ListTag();
            items.add(new CompoundTag());
            tag.put("JunkItems", items);
        });
        assertQuarantinedOnce(helper, unreadable, "item data-fix or codec failure");

        ItemStack partial = GameTestSupport.big8();
        GameTestSupport.updateCustomData(partial, tag -> {
            tag.putString("Unrelated", "preserve-me");
            putLegacyFluid(tag, "minecraft:lava", 1_000);
            tag.put("JunkItems", new ListTag());
        });
        assertQuarantinedOnce(helper, partial, "partial migration rollback");
        GameTestSupport.assertEmpty(partial);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(partial).getString("Unrelated")),
                "Failed migration discarded unrelated custom data");

        ItemStack obsoleteMarker = GameTestSupport.big8();
        GameTestSupport.updateCustomData(obsoleteMarker, tag -> {
            tag.putString("Unrelated", "preserve-me");
            tag.putBoolean("SomeBucketsLegacyMigrationFailed", true);
        });
        migrate(helper, obsoleteMarker);
        CompoundTag cleaned = GameTestSupport.copyCustomData(obsoleteMarker);
        GameTestSupport.check(cleaned != null
                        && !cleaned.contains("SomeBucketsLegacyMigrationFailed")
                        && "preserve-me".equals(cleaned.getString("Unrelated")),
                "Obsolete failure marker was not removed cleanly");
        helper.succeed();
    }

    /**
     * Manual: compare empty and filled Some Buckets stacks; empty stacks accept 16 while any content
     * limits the stack to one.
     */
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

    private static DataComponentPatch variantPatch(String marker) {
        CompoundTag tag = new CompoundTag();
        tag.putString("sb_variant_probe", marker);
        return DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build();
    }

    private static void migrate(GameTestHelper helper, ItemStack stack) {
        LegacyBucketMigration.migrate(stack, helper.getLevel(), () -> "GameTest");
    }

    private static void assertQuarantinedOnce(GameTestHelper helper, ItemStack stack, String caseName) {
        migrate(helper, stack);
        GameTestSupport.assertNoBucketState(stack, caseName);
        CompoundTag remaining = GameTestSupport.copyCustomData(stack);
        GameTestSupport.check(remaining != null
                        && remaining.contains("SomeBucketsLegacyMigrationQuarantine", Tag.TAG_COMPOUND)
                        && !remaining.contains("Mode") && !remaining.contains("JunkItems")
                        && !remaining.contains("SomeBucketsLegacyMigrationFailed"),
                caseName + " was not quarantined as a one-shot payload");
        ItemStack afterFirstAttempt = stack.copy();
        migrate(helper, stack);
        GameTestSupport.assertSameStack(afterFirstAttempt, stack,
                caseName + " changed during a second migration check");
    }

    private static void putLegacyFluid(CompoundTag root, String fluidId, int amount) {
        CompoundTag fluid = new CompoundTag();
        fluid.putString("FluidName", fluidId);
        fluid.putInt("Amount", amount);
        root.putString("Mode", "fluid");
        root.put("FluidStack", fluid);
    }

    private static void putLegacyEntities(CompoundTag root, String entityType, int count) {
        ListTag entities = new ListTag();
        for (int i = 0; i < count; i++) {
            CompoundTag entity = new CompoundTag();
            entity.putInt("LegacyMarker", i);
            entities.add(entity);
        }
        root.putString("Mode", "entity");
        root.putString("EntityType", entityType);
        root.put("Entities", entities);
    }

    private static CompoundTag legacyItem(ItemStack stack, int count) {
        CompoundTag item = new CompoundTag();
        item.putString("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        item.putByte("Count", (byte) count);
        return item;
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
