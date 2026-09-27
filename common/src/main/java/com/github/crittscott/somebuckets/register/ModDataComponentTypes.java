package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.MBItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/**
 * The {@link DataComponentType}s that carry every bucket family's persistent per-stack state.
 * Runtime bucket behavior accesses these components through {@code BucketState}; loader registration
 * code enters the instances into {@link Registries#DATA_COMPONENT_TYPE}, and data-driven item
 * construction may write a component directly.
 *
 * <p>{@link #FLUID_CONTENT}, {@link #MILK_AMOUNT}, {@link #POWDER_UNITS}, and {@link #CAPTURED_MOBS}
 * are the mutually exclusive content group a bucket write clears before selecting one;
 * {@link #JUNK_CONTENTS} is independent and coexists with any of them.
 */
public final class ModDataComponentTypes {
    private ModDataComponentTypes() {}

    /** Largest finite amount represented by any Some Buckets fluid or milk component. */
    public static final int MAX_FINITE_AMOUNT_MB =
            BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS * 1_000;

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

    /**
     * Fluid identity, amount in millibuckets, and variant components. This is the loader-neutral
     * on-disk shape; {@code ForgeFluidStacks} / {@code NeoForgeFluidStacks} /
     * {@code FabricFluidVariants} convert between it and their native fluid values. The components
     * are encoded with the enclosing item stack's registry context.
     */
    public record FluidContent(Fluid fluid, int amount, DataComponentPatch variant) {
        /** Persistent codec for stored fluid content. */
        public static final Codec<FluidContent> CODEC = RecordCodecBuilder.<FluidContent>create(instance -> instance.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(FluidContent::fluid),
                FINITE_AMOUNT_CODEC.fieldOf("amount").forGetter(FluidContent::amount),
                DataComponentPatch.CODEC.optionalFieldOf("variant", DataComponentPatch.EMPTY)
                        .forGetter(FluidContent::variant)
        ).apply(instance, FluidContent::new)).validate(FluidContent::validate);

        /** Network codec for stored fluid content. */
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidContent> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.FLUID), FluidContent::fluid,
                boundedVarInt(1, MAX_FINITE_AMOUNT_MB, "fluid amount"), FluidContent::amount,
                DataComponentPatch.STREAM_CODEC, FluidContent::variant,
                FluidContent::new).map(
                        content -> validate(content).getOrThrow(IllegalArgumentException::new),
                        content -> content);

        private static DataResult<FluidContent> validate(FluidContent content) {
            if (content.fluid() == Fluids.EMPTY) {
                return DataResult.error(() -> "Stored fluid content may not use the empty fluid");
            }
            if (BuiltInRegistries.FLUID.getKey(content.fluid()) == null) {
                return DataResult.error(() -> "Stored fluid content must use a registered fluid");
            }
            return DataResult.success(content);
        }
    }

    /** Captured entity type and FIFO entity snapshots. */
    public record CapturedMobs(ResourceLocation entityType, List<CompoundTag> entities) {
        public CapturedMobs {
            entities = List.copyOf(entities);
        }

        /** Persistent codec for captured-mob state. */
        public static final Codec<CapturedMobs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("entity_type").forGetter(CapturedMobs::entityType),
                CompoundTag.CODEC.listOf().validate(CapturedMobs::validateEntities)
                        .fieldOf("entities").forGetter(CapturedMobs::entities)
        ).apply(instance, CapturedMobs::new));

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

    /** The Junk/Trash Bucket stack list together with the render-layout seed it lives and dies with. */
    public record JunkContents(List<ItemStack> items, long layoutSeed) {
        public JunkContents {
            items = List.copyOf(items);
        }

        /** ItemStack does not provide value equality, so component equality must compare stack state. */
        @Override
        public boolean equals(Object value) {
            return this == value || value instanceof JunkContents other
                    && layoutSeed == other.layoutSeed && ItemStack.listMatches(items, other.items);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(layoutSeed) + ItemStack.hashStackList(items);
        }

        /** Persistent codec for stored junk contents and their layout seed. */
        public static final Codec<JunkContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.listOf().validate(JunkContents::validateItems)
                        .fieldOf("items").forGetter(JunkContents::items),
                Codec.LONG.fieldOf("layout_seed").forGetter(JunkContents::layoutSeed)
        ).apply(instance, JunkContents::new));

        /** Network codec for stored junk contents and their layout seed. */
        public static final StreamCodec<RegistryFriendlyByteBuf, JunkContents> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS)),
                JunkContents::items,
                ByteBufCodecs.VAR_LONG, JunkContents::layoutSeed,
                JunkContents::new);

        private static DataResult<List<ItemStack>> validateItems(List<ItemStack> items) {
            if (items.isEmpty()) return DataResult.error(() -> "Stored item list may not be empty");
            if (items.size() > BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS) {
                return DataResult.error(() -> "Too many stored item stacks: " + items.size());
            }
            for (ItemStack stack : items) {
                if (stack.isEmpty()) return DataResult.error(() -> "Stored item stack may not be empty");
                if (stack.getCount() > stack.getMaxStackSize()) {
                    return DataResult.error(() -> "Stored item stack exceeds its maximum size");
                }
            }
            return DataResult.success(items);
        }
    }

    /** Component type for loader-neutral fluid identity, amount, and variant data. */
    public static final DataComponentType<FluidContent> FLUID_CONTENT =
            DataComponentType.<FluidContent>builder()
                    .persistent(FluidContent.CODEC)
                    .networkSynchronized(FluidContent.STREAM_CODEC)
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
                    .persistent(CapturedMobs.CODEC)
                    .networkSynchronized(CapturedMobs.STREAM_CODEC)
                    .build();

    /** Component type for Junk/Trash Bucket item stacks and render-layout seed. */
    public static final DataComponentType<JunkContents> JUNK_CONTENTS =
            DataComponentType.<JunkContents>builder()
                    .persistent(JunkContents.CODEC)
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
                if (value < minimum || value > maximum) {
                    throw new IllegalArgumentException(
                            "Invalid " + name + ": " + value + " (expected " + minimum + "–" + maximum + ")");
                }
                ByteBufCodecs.VAR_INT.encode(buffer, value);
            }
        };
    }
}
