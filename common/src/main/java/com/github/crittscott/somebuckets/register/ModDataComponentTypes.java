package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.github.crittscott.somebuckets.util.BucketStateMigration;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link DataComponentType}s that carry every bucket family's persistent per-stack state.
 * Runtime bucket behavior accesses these components through {@code BucketState}; loader registration
 * code enters the instances into {@link Registries#DATA_COMPONENT_TYPE}, and data-driven item
 * construction may write a component directly. Each persistent codec reads saved data through
 * {@link BucketStateMigration}, which brings older formats up to the current one first; the codecs
 * here describe only the current format.
 *
 * <p>{@link #FLUID_CONTENT}, {@link #MILK_AMOUNT}, {@link #POWDER_UNITS}, and {@link #CAPTURED_MOBS}
 * are the mutually exclusive content group a bucket write clears before selecting one;
 * {@link #JUNK_CONTENTS} is independent and coexists with any of them.
 */
public final class ModDataComponentTypes {
    private ModDataComponentTypes() {}

    /** Largest finite amount represented by any Some Buckets fluid or milk component. */
    public static final int MAX_FINITE_AMOUNT_MB =
            BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS * FluidBucketItem.BUCKET_VOLUME_MB;

    private static final Codec<Integer> FINITE_AMOUNT_CODEC =
            Codec.intRange(1, MAX_FINITE_AMOUNT_MB);
    private static final Codec<Integer> MILK_AMOUNT_CODEC =
            FINITE_AMOUNT_CODEC.validate(ModDataComponentTypes::validateMilkAmount);
    private static final Codec<Integer> POWDER_UNITS_CODEC =
            Codec.intRange(1, BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS);

    /** Registry id for {@link #FLUID_CONTENT}. */
    public static final ResourceLocation FLUID_CONTENT_ID = id("fluid_content");
    /** Registry id for {@link #MILK_AMOUNT}. */
    public static final ResourceLocation MILK_AMOUNT_ID = id("milk_amount");
    /** Registry id for {@link #POWDER_UNITS}. */
    public static final ResourceLocation POWDER_UNITS_ID = id("powder_units");
    /** Registry id for {@link #CAPTURED_MOBS}. */
    public static final ResourceLocation CAPTURED_MOBS_ID = id("captured_mobs");
    /** Registry id for {@link #JUNK_CONTENTS}. */
    public static final ResourceLocation JUNK_CONTENTS_ID = id("junk_contents");

    /** Persistent codec for stored fluid content in the current format. */
    private static final Codec<StoredFluid> FLUID_CONTENT_CODEC =
            RecordCodecBuilder.<StoredFluid>create(instance -> instance.group(
                    BucketStateMigration.<StoredFluid>schemaField(),
                    BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(StoredFluid::fluid),
                    FINITE_AMOUNT_CODEC.fieldOf("amount").forGetter(StoredFluid::amount),
                    DataComponentPatch.CODEC.optionalFieldOf("variant", DataComponentPatch.EMPTY)
                            .forGetter(StoredFluid::components)
            ).apply(instance, (schema, fluid, amount, variant) -> new StoredFluid(fluid, amount, variant)))
                    .validate(ModDataComponentTypes::validateStoredFluid);

    /** Network codec for stored fluid content. */
    private static final StreamCodec<RegistryFriendlyByteBuf, StoredFluid> FLUID_CONTENT_STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.FLUID), StoredFluid::fluid,
                    boundedVarInt(1, MAX_FINITE_AMOUNT_MB, "fluid amount"), StoredFluid::amount,
                    DataComponentPatch.STREAM_CODEC, StoredFluid::components,
                    StoredFluid::new).map(
                            content -> validateStoredFluid(content).getOrThrow(IllegalArgumentException::new),
                            content -> content);

    /** Captured entity type and FIFO entity snapshots. */
    public record CapturedMobs(ResourceLocation entityType, List<CompoundTag> entities) {
        public CapturedMobs {
            entities = List.copyOf(entities);
        }

        /** Persistent codec for captured-mob state in the current format. */
        public static final Codec<CapturedMobs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BucketStateMigration.<CapturedMobs>schemaField(),
                BucketStateMigration.<CapturedMobs>dataVersionField(),
                ResourceLocation.CODEC.fieldOf("entity_type").forGetter(CapturedMobs::entityType),
                CompoundTag.CODEC.listOf().validate(CapturedMobs::validateEntities)
                        .fieldOf("entities").forGetter(CapturedMobs::entities)
        ).apply(instance, (schema, dataVersion, entityType, entities) -> new CapturedMobs(entityType, entities)));

        /** Network codec for captured-mob state. */
        public static final StreamCodec<RegistryFriendlyByteBuf, CapturedMobs> STREAM_CODEC = StreamCodec
                .<RegistryFriendlyByteBuf, CapturedMobs, ResourceLocation, List<CompoundTag>>composite(
                ResourceLocation.STREAM_CODEC, CapturedMobs::entityType,
                ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list(MBItem.MAX_MOBS)), CapturedMobs::entities,
                CapturedMobs::new).map(
                        mobs -> validateEntities(mobs.entities())
                                .map(entities -> mobs).getOrThrow(IllegalArgumentException::new),
                        mobs -> mobs);

        private static DataResult<List<CompoundTag>> validateEntities(List<CompoundTag> entities) {
            if (entities.isEmpty()) return DataResult.error(() -> "Captured mob list may not be empty");
            if (entities.size() > MBItem.MAX_MOBS) {
                return DataResult.error(() -> "Too many captured mobs: " + entities.size());
            }
            return DataResult.success(entities);
        }
    }

    /**
     * The Junk/Trash Bucket stack list together with the render-layout seed it lives and dies with,
     * plus saved entries that cannot currently be stored. Set-aside entries take no part in capacity,
     * FIFO order, rendering, intake, or ejection; they only keep the bucket from counting as empty.
     */
    public record JunkContents(List<ItemStack> items, long layoutSeed, List<SetAside> setAside) {
        public JunkContents {
            items = List.copyOf(items);
            setAside = List.copyOf(setAside);
        }

        /** ItemStack does not provide value equality, so component equality must compare stack state. */
        @Override
        public boolean equals(Object value) {
            return this == value || value instanceof JunkContents other
                    && layoutSeed == other.layoutSeed && ItemStack.listMatches(items, other.items)
                    && setAside.equals(other.setAside);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Long.hashCode(layoutSeed) + ItemStack.hashStackList(items)) + setAside.hashCode();
        }

        /**
         * Persistent codec for stored junk contents in the current format. An entry that does not
         * decode to a storable stack, or that exceeds the largest storage-bucket capacity, is set
         * aside rather than failing the component. Set-aside entries are retried on every decode; one
         * that decodes again becomes {@link SetAside.Restorable} and is restored when the enclosing
         * stack is next loaded with room for it.
         */
        public static final Codec<JunkContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BucketStateMigration.<JunkContents>schemaField(),
                BucketStateMigration.<JunkContents>dataVersionField(),
                SetAside.CODEC.listOf().fieldOf("items")
                        .forGetter(junk -> junk.items().stream().<SetAside>map(SetAside.Restorable::new).toList()),
                Codec.LONG.fieldOf("layout_seed").forGetter(JunkContents::layoutSeed),
                SetAside.CODEC.listOf().optionalFieldOf("set_aside", List.of()).forGetter(JunkContents::setAside)
        ).apply(instance, (schema, dataVersion, entries, layoutSeed, setAside) ->
                fromDecoded(entries, layoutSeed, setAside)));

        /** Network codec for stored junk contents, their layout seed, and set-aside entries. */
        public static final StreamCodec<RegistryFriendlyByteBuf, JunkContents> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS)),
                JunkContents::items,
                ByteBufCodecs.VAR_LONG, JunkContents::layoutSeed,
                SetAside.STREAM_CODEC.apply(ByteBufCodecs.list()), JunkContents::setAside,
                JunkContents::new);

        private static JunkContents fromDecoded(List<SetAside> entries, long layoutSeed, List<SetAside> setAside) {
            List<ItemStack> items = new ArrayList<>();
            List<SetAside> heldBack = new ArrayList<>();
            for (SetAside entry : entries) {
                if (entry instanceof SetAside.Restorable restorable
                        && items.size() < BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS) {
                    items.add(restorable.stack());
                } else {
                    if (entry instanceof SetAside.Raw raw) {
                        SomeBuckets.LOGGER.warn("Set aside an unreadable storage-bucket entry: {}", raw.data());
                    }
                    heldBack.add(entry);
                }
            }
            heldBack.addAll(setAside);
            return new JunkContents(items, layoutSeed, heldBack);
        }
    }

    /** A saved storage-bucket entry that cannot currently take part in the bucket's contents. */
    public sealed interface SetAside permits SetAside.Raw, SetAside.Restorable {
        /**
         * Persistent codec for one entry, shaped as a saved item stack. Decoding never fails: data
         * that does not decode to a storable stack is kept as {@link Raw}.
         */
        Codec<SetAside> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<SetAside, T>> decode(DynamicOps<T> ops, T input) {
                Dynamic<T> data = new Dynamic<>(ops, input);
                SetAside entry = ItemStack.CODEC.parse(data).result()
                        .filter(SetAside::storable)
                        .<SetAside>map(Restorable::new)
                        .orElseGet(() -> new Raw(data.convert(NbtOps.INSTANCE).getValue()));
                return DataResult.success(Pair.of(entry, ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(SetAside entry, DynamicOps<T> ops, T prefix) {
                return switch (entry) {
                    case Restorable restorable -> ItemStack.CODEC.encode(restorable.stack(), ops, prefix);
                    case Raw raw -> DataResult.success(
                            new Dynamic<>(NbtOps.INSTANCE, raw.data()).convert(ops).getValue());
                };
            }
        };

        /** Network codec for one entry. */
        StreamCodec<RegistryFriendlyByteBuf, SetAside> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SetAside decode(RegistryFriendlyByteBuf buffer) {
                return buffer.readBoolean()
                        ? new Restorable(ItemStack.STREAM_CODEC.decode(buffer))
                        : new Raw(ByteBufCodecs.TAG.decode(buffer));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, SetAside entry) {
                switch (entry) {
                    case Restorable restorable -> {
                        buffer.writeBoolean(true);
                        ItemStack.STREAM_CODEC.encode(buffer, restorable.stack());
                    }
                    case Raw raw -> {
                        buffer.writeBoolean(false);
                        ByteBufCodecs.TAG.encode(buffer, raw.data());
                    }
                }
            }
        };

        private static boolean storable(ItemStack stack) {
            return JBItem.canStoreByVanillaRules(stack) && stack.getCount() <= stack.getMaxStackSize();
        }

        /**
         * Saved item data that does not decode to a storable stack, kept as written.
         *
         * @param data the saved item-stack data
         */
        record Raw(Tag data) implements SetAside {}

        /**
         * A set-aside entry that decodes to a storable stack again and waits for room in the bucket.
         *
         * @param stack the decoded stack
         */
        record Restorable(ItemStack stack) implements SetAside {
            /** ItemStack does not provide value equality, so entry equality must compare stack state. */
            @Override
            public boolean equals(Object value) {
                return this == value || value instanceof Restorable other && ItemStack.matches(stack, other.stack);
            }

            @Override
            public int hashCode() {
                return 31 * ItemStack.hashItemAndComponents(stack) + stack.getCount();
            }
        }
    }

    /** Component type for loader-neutral fluid identity, amount, and variant data. */
    public static final DataComponentType<StoredFluid> FLUID_CONTENT =
            DataComponentType.<StoredFluid>builder()
                    .persistent(BucketStateMigration.fluidContent(FLUID_CONTENT_CODEC))
                    .networkSynchronized(FLUID_CONTENT_STREAM_CODEC)
                    .build();

    /** Component type for milk amount in millibuckets; always a whole number of buckets. */
    public static final DataComponentType<Integer> MILK_AMOUNT =
            DataComponentType.<Integer>builder()
                    .persistent(MILK_AMOUNT_CODEC)
                    .networkSynchronized(boundedVarInt(1, MAX_FINITE_AMOUNT_MB, "milk amount").map(
                            amount -> validateMilkAmount(amount).getOrThrow(IllegalArgumentException::new),
                            amount -> amount))
                    .build();

    /** Component type for powder-snow block count. */
    public static final DataComponentType<Integer> POWDER_UNITS =
            DataComponentType.<Integer>builder()
                    .persistent(POWDER_UNITS_CODEC)
                    .networkSynchronized(boundedVarInt(1, BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS,
                            "powder-snow units"))
                    .build();

    /** Component type for captured entity type and FIFO snapshots. */
    public static final DataComponentType<CapturedMobs> CAPTURED_MOBS =
            DataComponentType.<CapturedMobs>builder()
                    .persistent(BucketStateMigration.capturedMobs(CapturedMobs.CODEC))
                    .networkSynchronized(CapturedMobs.STREAM_CODEC)
                    .build();

    /** Component type for Junk/Trash Bucket item stacks, render-layout seed, and set-aside entries. */
    public static final DataComponentType<JunkContents> JUNK_CONTENTS =
            DataComponentType.<JunkContents>builder()
                    .persistent(BucketStateMigration.junkContents(JunkContents.CODEC))
                    .networkSynchronized(JunkContents.STREAM_CODEC)
                    .build();

    /**
     * Feeds every (id, type) pair to {@code registrar} in a stable order.
     *
     * @param registrar sink each loader adapts to its own {@code Registries.DATA_COMPONENT_TYPE}
     *                  registration call
     */
    public static void forEach(Registrar registrar) {
        registrar.accept(FLUID_CONTENT_ID, FLUID_CONTENT);
        registrar.accept(MILK_AMOUNT_ID, MILK_AMOUNT);
        registrar.accept(POWDER_UNITS_ID, POWDER_UNITS);
        registrar.accept(CAPTURED_MOBS_ID, CAPTURED_MOBS);
        registrar.accept(JUNK_CONTENTS_ID, JUNK_CONTENTS);
    }

    /** Sink for {@link #forEach}; a loader adapts its own registry call to this shape. */
    @FunctionalInterface
    public interface Registrar {
        /**
         * Registers one component type under its id.
         *
         * @param id the component's registry id
         * @param type the component type instance
         */
        void accept(ResourceLocation id, DataComponentType<?> type);
    }

    /**
     * Checks that a milk amount is a whole number of buckets. Milk never passes through a loader
     * fluid API, so it only moves in bucket-volume units.
     *
     * @param amount milk amount in millibuckets
     * @return the amount, or an error when it is not a positive multiple of one bucket-volume
     */
    public static DataResult<Integer> validateMilkAmount(int amount) {
        return amount > 0 && amount % FluidBucketItem.BUCKET_VOLUME_MB == 0
                ? DataResult.success(amount)
                : DataResult.error(() -> "Milk amount must be a whole number of buckets: " + amount);
    }

    private static DataResult<StoredFluid> validateStoredFluid(StoredFluid content) {
        if (content.fluid() == Fluids.EMPTY) {
            return DataResult.error(() -> "Stored fluid content may not use the empty fluid");
        }
        return DataResult.success(content);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, path);
    }

    private static StreamCodec<RegistryFriendlyByteBuf, Integer> boundedVarInt(int minimum, int maximum,
                                                                                String name) {
        return new StreamCodec<>() {
            @Override
            public Integer decode(RegistryFriendlyByteBuf buffer) {
                int value = ByteBufCodecs.VAR_INT.decode(buffer);
                if (value < minimum || value > maximum) {
                    throw new IllegalArgumentException(
                            "Invalid " + name + ": " + value + " (expected " + minimum + "–" + maximum + ")");
                }
                return value;
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Integer value) {
                ByteBufCodecs.VAR_INT.encode(buffer, value);
            }
        };
    }
}
