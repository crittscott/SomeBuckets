package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.BucketStateMigration;
import com.github.crittscott.somebuckets.util.StoredFluid;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
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

import java.util.ArrayList;
import java.util.List;

/** Cross-loader bucket-state scenarios; each loader wraps every method as a GameTest. */
final class StateScenarios {
    private StateScenarios() {}
    /**
     * Automation-only: probes fluid sound selection and verifies registered sounds precede lava and
     * generic fallbacks.
     */
    static void fluid_sound_resolution_prefers_registered_sound_then_fallback(GameTestHelper helper) {
        GameTestSupport.check(FluidTransactions.resolveBucketSound(null, false, true) == SoundEvents.BUCKET_FILL,
                "Water fill did not resolve to the vanilla fill sound");
        GameTestSupport.check(
                FluidTransactions.resolveBucketSound(null, true, false) == SoundEvents.BUCKET_EMPTY_LAVA,
                "Lava empty did not resolve to the vanilla lava-empty sound");

        var custom = SoundEvents.AMETHYST_BLOCK_CHIME;
        GameTestSupport.check(FluidTransactions.resolveBucketSound(custom, true, true) == custom,
                "Registered custom bucket sound did not take precedence");
        helper.succeed();
    }
    /** Automation-only: reads every bucket-state accessor on pristine stacks and verifies no component is attached. */
    static void pristine_bucket_reads_do_not_attach_components(GameTestHelper helper) {
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
    static void clear_removes_all_content_and_preserves_unrelated_components(GameTestHelper helper) {
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
    static void stored_item_reads_are_detached_and_empty_writes_clean_components(GameTestHelper helper) {
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
        overflowProbe.set(ModDataComponentTypes.FLUID_CONTENT,
                new StoredFluid(Fluids.WATER, Integer.MAX_VALUE, DataComponentPatch.EMPTY));
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
                new ModDataComponentTypes.JunkContents(
                        List.of(GameTestSupport.trash()), 0L, List.of()));
        craftedJunk.getItem().verifyComponentsAfterLoad(craftedJunk);
        GameTestSupport.assertNoBucketState(craftedJunk, "malicious creative-style junk payload");

        GameTestSupport.assertNoBucketState(milk, "rejected milk write");
        GameTestSupport.assertNoBucketState(powder, "rejected powder write");
        GameTestSupport.assertNoBucketState(fluid, "rejected fluid write");
        GameTestSupport.assertNoBucketState(source, "rejected source write");
        GameTestSupport.assertNoBucketState(wrongItem, "rejected wrong-item write");
        GameTestSupport.assertNoBucketState(junk, "rejected junk write");
        helper.succeed();
    }
    /**
     * Manual: install a resource pack that translates the Big, Junk, and Mob Bucket tooltip keys,
     * switch to that language, and inspect filled examples; all three lines and the stored mob name
     * use the translated text.
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
                "Final entity removal discarded unrelated components");
        helper.succeed();
    }
    /** Automation-only: round-trips Mob Bucket state through its network codec. */
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
                    "Mob Bucket network round-trip changed the stored snapshots");
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
     * Manual: on 1.21.1, save a world holding a Big Bucket of water (on Forge, a modded fluid with
     * variant data), a Mob Bucket of pigs, and a Junk Bucket holding items and a filled Big Bucket;
     * open it on this version and confirm every content survives. Automated with hand-built 1.21.1
     * component data, including vanilla data that the data fixer must upgrade.
     */
    static void released_bucket_state_loads_in_current_form(GameTestHelper helper) {
        CompoundTag probe = new CompoundTag();
        probe.putString("sb_variant_probe", "released");
        CompoundTag variant;
        if (BucketOperations.get().releasedFluidVariantIsRawTag()) {
            variant = probe;
        } else {
            variant = new CompoundTag();
            variant.put("minecraft:custom_data", probe);
        }
        CompoundTag fluid = releasedFluid("minecraft:water", 2_000);
        fluid.put("variant", variant);
        ItemStack big = load(helper, savedStack(GameTestSupport.big8(), ModDataComponentTypes.FLUID_CONTENT_ID, fluid));
        GameTestSupport.assertFluid(big, Fluids.WATER, 2_000);
        GameTestSupport.check(BucketState.getStoredFluid(big).components().equals(variantPatch("released")),
                "Released fluid variant did not load as custom-data variant components");

        CompoundTag attribute = new CompoundTag();
        attribute.putString("id", "minecraft:generic.movement_speed");
        attribute.putDouble("base", 0.25D);
        ListTag attributes = new ListTag();
        attributes.add(attribute);
        CompoundTag pig = new CompoundTag();
        pig.put("attributes", attributes);
        ListTag entities = new ListTag();
        entities.add(pig);
        CompoundTag mobs = new CompoundTag();
        mobs.putString("entity_type", "minecraft:pig");
        mobs.put("entities", entities);
        ItemStack mobBucket = load(helper,
                savedStack(GameTestSupport.mob(), ModDataComponentTypes.CAPTURED_MOBS_ID, mobs));
        CompoundTag snapshot = BucketState.copyFirstEntitySnapshot(mobBucket);
        GameTestSupport.check(BucketState.getCurrentEntityType(mobBucket) == EntityType.PIG
                        && "minecraft:movement_speed".equals(
                                snapshot.getList("attributes", Tag.TAG_COMPOUND).getCompound(0).getString("id"))
                        && !snapshot.contains("id"),
                "Released mob snapshot was not data-fixed to the current version: " + snapshot);

        CompoundTag fireproof = new CompoundTag();
        fireproof.put("minecraft:fire_resistant", new CompoundTag());
        CompoundTag diamond = new CompoundTag();
        diamond.putString("id", "minecraft:diamond");
        diamond.putInt("count", 2);
        diamond.put("components", fireproof);
        ListTag items = new ListTag();
        items.add(diamond);
        items.add(savedStack(GameTestSupport.big8(), ModDataComponentTypes.FLUID_CONTENT_ID,
                releasedFluid("minecraft:water", 1_000)));
        CompoundTag junkContents = new CompoundTag();
        junkContents.put("items", items);
        junkContents.putLong("layout_seed", 42L);
        ItemStack junk = load(helper,
                savedStack(GameTestSupport.junk(), ModDataComponentTypes.JUNK_CONTENTS_ID, junkContents));
        List<ItemStack> stored = BucketState.getStoredItems(junk);
        GameTestSupport.check(stored.size() == 2 && stored.get(0).is(Items.DIAMOND) && stored.get(0).getCount() == 2
                        && stored.get(0).has(DataComponents.DAMAGE_RESISTANT),
                "Released junk item was not data-fixed to the current version: " + stored);
        GameTestSupport.assertFluid(stored.get(1), Fluids.WATER, 1_000);
        GameTestSupport.check(BucketState.getJunkLayoutSeed(junk) == 42L && BucketState.getSetAsideCount(junk) == 0,
                "Released junk layout seed was lost or an entry was set aside");

        CompoundTag saved = save(helper, junk).getCompound("components")
                .getCompound(ModDataComponentTypes.JUNK_CONTENTS_ID.toString());
        GameTestSupport.check(saved.getInt(BucketStateMigration.SCHEMA) == 1
                        && saved.getInt(BucketStateMigration.DATA_VERSION)
                                == SharedConstants.getCurrentVersion().getDataVersion().getVersion(),
                "Loaded junk contents were not saved with current stamps: " + saved);
        GameTestSupport.assertSameStack(junk, load(helper, save(helper, junk)),
                "Current junk contents changed across a save and load");
        GameTestSupport.assertSameStack(big, load(helper, save(helper, big)),
                "Current fluid content changed across a save and load");
        GameTestSupport.assertSameStack(mobBucket, load(helper, save(helper, mobBucket)),
                "Current captured mobs changed across a save and load");
        helper.succeed();
    }

    /**
     * Manual: put an item from another mod into a Junk Bucket, remove that mod, and reload; the rest
     * of the bucket loads, its tooltip reports the entry set aside, and restoring the mod and
     * reloading returns the item to the bucket when there is room.
     */
    static void unreadable_storage_entries_are_set_aside_and_restored(GameTestHelper helper) {
        ListTag items = new ListTag();
        items.add(savedItem("missingmod:gem", 1));
        items.add(savedItem("minecraft:apple", 5));
        CompoundTag junkContents = new CompoundTag();
        junkContents.put("items", items);
        junkContents.putLong("layout_seed", 7L);
        ItemStack junk = load(helper,
                savedStack(GameTestSupport.junk(), ModDataComponentTypes.JUNK_CONTENTS_ID, junkContents));
        List<ItemStack> stored = BucketState.getStoredItems(junk);
        GameTestSupport.check(stored.size() == 1 && stored.get(0).is(Items.APPLE)
                        && BucketState.getSetAsideCount(junk) == 1,
                "Unreadable junk entry was not set aside beside the readable one: " + stored);

        BucketState.setStoredItems(junk, List.of());
        GameTestSupport.check(!BucketState.isEmptyBucket(junk) && BucketState.getSetAsideCount(junk) == 1,
                "Emptying the stored items dropped the set-aside entry or made the bucket empty");
        ItemStack reloaded = load(helper, save(helper, junk));
        ModDataComponentTypes.JunkContents contents = BucketState.getStoredItemsComponent(reloaded);
        GameTestSupport.check(contents != null && contents.setAside().size() == 1
                        && contents.setAside().get(0) instanceof ModDataComponentTypes.SetAside.Raw raw
                        && raw.data() instanceof CompoundTag data && "missingmod:gem".equals(data.getString("id")),
                "Set-aside entry did not survive a save and load unchanged");

        GameTestSupport.check(restoredCount(helper, GameTestSupport.junk()) == 2,
                "Readable set-aside entry was not restored to a Junk Bucket with room");
        GameTestSupport.check(restoredCount(helper, GameTestSupport.trash()) == 1,
                "Readable set-aside entry was restored past the Trash Bucket's capacity");
        helper.succeed();
    }

    private static int restoredCount(GameTestHelper helper, ItemStack bucket) {
        ListTag items = new ListTag();
        items.add(savedItem("minecraft:apple", 1));
        ListTag setAside = new ListTag();
        setAside.add(savedItem("minecraft:diamond", 1));
        CompoundTag junkContents = new CompoundTag();
        junkContents.putInt(BucketStateMigration.SCHEMA, 1);
        junkContents.putInt(BucketStateMigration.DATA_VERSION,
                SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        junkContents.put("items", items);
        junkContents.putLong("layout_seed", 0L);
        junkContents.put("set_aside", setAside);
        ItemStack loaded = load(helper, savedStack(bucket, ModDataComponentTypes.JUNK_CONTENTS_ID, junkContents));
        GameTestSupport.check(BucketState.getStoredItemCount(loaded) + BucketState.getSetAsideCount(loaded) == 2,
                "A storage-bucket entry was lost while restoring set-aside entries");
        return BucketState.getStoredItemCount(loaded);
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

    /**
     * Automation-only: drains finite milk in partial and final steps and verifies exact arithmetic and
     * canonical empty state.
     */
    static void finite_content_drain_handles_partial_and_final_milk(GameTestHelper helper) {
        ItemStack stack = GameTestSupport.milk(GameTestSupport.big8(), 3000);
        GameTestSupport.updateCustomData(stack, tag -> tag.putString("Unrelated", "preserve-me"));

        int partial = BucketState.drainFiniteContent(stack, 1000);

        GameTestSupport.check(partial == 1000, "Partial milk drain reported " + partial + " mB");
        GameTestSupport.assertMilk(stack, 2000);

        int finalDrain = BucketState.drainFiniteContent(stack, 2000);

        GameTestSupport.check(finalDrain == 2000, "Final milk drain reported " + finalDrain + " mB");
        GameTestSupport.assertEmpty(stack);
        GameTestSupport.check("preserve-me".equals(
                        GameTestSupport.copyCustomData(stack).getString("Unrelated")),
                "Milk drain removed unrelated components");
        helper.succeed();
    }

    private static ItemStack load(GameTestHelper helper, CompoundTag saved) {
        return ItemStack.parse(helper.getLevel().registryAccess(), saved)
                .orElseThrow(() -> new GameTestAssertException("Saved stack did not load: " + saved));
    }

    private static CompoundTag save(GameTestHelper helper, ItemStack stack) {
        return (CompoundTag) stack.save(helper.getLevel().registryAccess());
    }

    private static CompoundTag savedItem(String id, int count) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id);
        item.putInt("count", count);
        return item;
    }

    private static CompoundTag savedStack(ItemStack bucket, ResourceLocation componentId, CompoundTag component) {
        CompoundTag item = savedItem(BuiltInRegistries.ITEM.getKey(bucket.getItem()).toString(), 1);
        CompoundTag components = new CompoundTag();
        components.put(componentId.toString(), component);
        item.put("components", components);
        return item;
    }

    private static CompoundTag releasedFluid(String fluidId, int amount) {
        CompoundTag fluid = new CompoundTag();
        fluid.putString("id", fluidId);
        fluid.putInt("amount", amount);
        return fluid;
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
