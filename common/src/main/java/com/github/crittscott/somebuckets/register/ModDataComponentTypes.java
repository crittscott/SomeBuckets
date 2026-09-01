package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.MBItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The registered {@link DataComponentType}s that carry every bucket family's persistent per-stack
 * state. {@code BucketState} is the sole reader and writer; each loader's {@code register} code only
 * enters these instances into {@link Registries#DATA_COMPONENT_TYPE} under the ids declared here,
 * mirroring the sound registration split ({@code ModSoundIds} plus a loader {@code ModSounds}).
 *
 * <p>{@link #FLUID_CONTENT}, {@link #MILK_AMOUNT}, {@link #POWDER_UNITS}, and {@link #CAPTURED_MOBS}
 * are the mutually exclusive content group a bucket write clears before selecting one;
 * {@link #JUNK_CONTENTS} is independent and coexists with any of them.
 */
public final class ModDataComponentTypes {
    private ModDataComponentTypes() {}

    public static final ResourceLocation FLUID_CONTENT_ID = id("fluid_content");
    public static final ResourceLocation MILK_AMOUNT_ID = id("milk_amount");
    public static final ResourceLocation POWDER_UNITS_ID = id("powder_units");
    public static final ResourceLocation CAPTURED_MOBS_ID = id("captured_mobs");
    public static final ResourceLocation JUNK_CONTENTS_ID = id("junk_contents");

    /**
     * Fluid identity, amount in millibuckets, and an optional detached loader variant payload.
     * This is the loader-neutral on-disk shape; {@code ForgeFluidStacks} / {@code NeoForgeFluidStacks}
     * / {@code FabricFluidVariants} convert between it and their native fluid values.
     */
    public record FluidContent(Fluid fluid, int amount, Optional<CompoundTag> variant) {
        public static final Codec<FluidContent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(FluidContent::fluid),
                Codec.INT.fieldOf("amount").forGetter(FluidContent::amount),
                CompoundTag.CODEC.optionalFieldOf("variant").forGetter(FluidContent::variant)
        ).apply(instance, FluidContent::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, FluidContent> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.FLUID), FluidContent::fluid,
                ByteBufCodecs.VAR_INT, FluidContent::amount,
                ByteBufCodecs.OPTIONAL_COMPOUND_TAG, FluidContent::variant,
                FluidContent::new);
    }

    /** The stored entity type plus the FIFO list of bucket-format entity snapshots. */
    public record CapturedMobs(ResourceLocation entityType, List<CompoundTag> entities) {
        public static final Codec<CapturedMobs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("entity_type").forGetter(CapturedMobs::entityType),
                CompoundTag.CODEC.listOf().fieldOf("entities").forGetter(CapturedMobs::entities)
        ).apply(instance, CapturedMobs::new));

        /**
         * Client sync carries only the entity type and the snapshot count. The client renders the
         * Mob Bucket tooltip, filled property, and item bar from those two values alone and never
         * reads a snapshot body; the full FIFO snapshots stay server-side and on disk through
         * {@link #CODEC}, where {@code MBItem.releaseOldest} needs them. The received value holds
         * that many placeholder compounds so {@code BucketState.getEntityCount} stays correct.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, CapturedMobs> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, CapturedMobs::entityType,
                ByteBufCodecs.VAR_INT, mobs -> mobs.entities().size(),
                CapturedMobs::withPlaceholders);

        private static CapturedMobs withPlaceholders(ResourceLocation entityType, int count) {
            int bounded = Math.min(Math.max(count, 0), MBItem.MAX_MOBS);
            List<CompoundTag> placeholders = new ArrayList<>(bounded);
            for (int i = 0; i < bounded; i++) placeholders.add(new CompoundTag());
            return new CapturedMobs(entityType, List.copyOf(placeholders));
        }
    }

    /** The Junk/Trash Bucket stack list together with the render-layout seed it lives and dies with. */
    public record JunkContents(List<ItemStack> items, long layoutSeed) {
        public static final Codec<JunkContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.listOf().fieldOf("items").forGetter(JunkContents::items),
                Codec.LONG.fieldOf("layout_seed").forGetter(JunkContents::layoutSeed)
        ).apply(instance, JunkContents::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, JunkContents> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), JunkContents::items,
                ByteBufCodecs.VAR_LONG, JunkContents::layoutSeed,
                JunkContents::new);
    }

    public static final DataComponentType<FluidContent> FLUID_CONTENT =
            DataComponentType.<FluidContent>builder()
                    .persistent(FluidContent.CODEC)
                    .networkSynchronized(FluidContent.STREAM_CODEC)
                    .build();

    public static final DataComponentType<Integer> MILK_AMOUNT =
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build();

    public static final DataComponentType<Integer> POWDER_UNITS =
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build();

    public static final DataComponentType<CapturedMobs> CAPTURED_MOBS =
            DataComponentType.<CapturedMobs>builder()
                    .persistent(CapturedMobs.CODEC)
                    .networkSynchronized(CapturedMobs.STREAM_CODEC)
                    .build();

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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, path);
    }
}
