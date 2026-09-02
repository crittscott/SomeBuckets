package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Loader-specific fluid primitives used by the shared {@code BBFluidLogic} / {@code SBFluidLogic}
 * orchestration and by shared bucket items directly. This interface is the whole loader surface of
 * the fluid subsystem: a sided block-storage probe and one-unit move, vanilla water/lava cauldron
 * transitions, arbitrary-fluid world placement, per-fluid fill/empty sounds, native powder-snow
 * placement finalization, fluid presentation, the aquatic Mob Bucket water pair, held-container
 * transfer, and the Forge {@code FillBucketEvent} carve-out.
 *
 * <p>World-operation methods are called on both logical sides. Unless stated otherwise, a
 * {@code true} result means an accepted client prediction or a completed server operation;
 * {@code false} means the operation was rejected without changing bucket or world state. The server
 * is authoritative. Keep signatures in vanilla and {@link StoredFluid} terms; convert loader-native
 * fluid values at the loader boundary.
 */
public interface BucketOperations {
    /** Read-only classification of the exact block targeted by an assigned Source Bucket. */
    enum SourceTarget {
        /** One bucket-volume of the assigned fluid can be removed from the target. */
        MATCHING_FLUID,
        /** Fluid is present, but it is different or cannot be collected as one bucket-volume. */
        BLOCKING_FLUID,
        /** The target contains no fluid, so normal Source Bucket placement may be attempted. */
        NO_FLUID
    }

    /**
     * Outcome of dispatching a fluid operation to a sided block fluid store. A present store owns the
     * interaction even when it refuses, so common code falls back to world handling only for
     * {@link #NO_STORE}.
     */
    enum BlockFluidOutcome {
        /** The clicked face exposes no fluid store; world fallback is permitted. */
        NO_STORE,
        /** A fluid store exists but cannot complete the requested operation. */
        REFUSED,
        /** The store accepted the preview or completed the server transaction. */
        SUCCESS;

        /** Whether a block store, rather than world fallback, owns this operation. */
        public boolean handled() {
            return this != NO_STORE;
        }

        /** Whether the block store accepted the operation. */
        public boolean succeeded() {
            return this == SUCCESS;
        }
    }

    /** Holds the loader-installed implementation without forcing eager platform initialization. */
    final class Holder {
        private static BucketOperations instance;
        private Holder() {}
    }

    /**
     * Installs the loader implementation used by common item code, replacing any previous instance.
     * Called once during single-threaded mod bootstrap.
     *
     * @param operations the loader implementation to install
     * @throws NullPointerException if {@code operations} is {@code null}
     */
    static void install(BucketOperations operations) {
        Holder.instance = Objects.requireNonNull(operations, "operations");
    }

    /**
     * Returns the installed loader implementation.
     *
     * @return the loader implementation installed by {@link #install}
     * @throws IllegalStateException if the loader entry point has not installed an implementation
     */
    static BucketOperations get() {
        BucketOperations operations = Holder.instance;
        if (operations == null) throw new IllegalStateException("Bucket operations are not installed");
        return operations;
    }

    // ---- Held transfer ----

    /**
     * Transfers compatible content between the two held stacks, trying {@code bucket} to
     * {@code other} before the reverse direction.
     *
     * @return {@code true} if at least one content transfer was accepted
     */
    boolean tryHeldTransfer(Level level, Player player, InteractionHand bucketHand, ItemStack bucket,
                            InteractionHand otherHand, ItemStack other);

    // ---- Block storage and container discovery ----

    /**
     * Tests whether the specified block face exposes loader fluid storage.
     *
     * @return {@code true} when storage exists, whether or not it can accept the current operation
     */
    boolean hasBlockStorage(Level level, BlockPos pos, Direction face);

    /**
     * Tests whether the stack exposes a loader-native item-inventory handler (backpacks, pouches,
     * crates). Junk and Trash Bucket intake consults this so a modded portable container is refused
     * even when it leaves {@link net.minecraft.world.item.Item#canFitInsideContainerItems()} set.
     *
     * @return {@code true} when the stack exposes an item-inventory handler
     */
    boolean carriesItemContainer(ItemStack stack);

    // ---- Forge FillBucketEvent carve-out ----

    /**
     * Whether this loader fires a world bucket-use event, so shared item code should pre-resolve the
     * affected block and call {@link #beforeWorldBucketUse}. Only Forge does; NeoForge and Fabric
     * return {@code false}.
     */
    boolean firesWorldBucketEvent();

    /**
     * Forge-only pre-dispatch hook firing {@code FillBucketEvent}. NeoForge and Fabric return
     * {@code null}. Common code treats {@code null} as "continue normal bucket processing".
     *
     * @return the final interaction result when a Forge listener claimed the interaction, or
     *         {@code null} to continue
     */
    @Nullable
    InteractionResultHolder<ItemStack> beforeWorldBucketUse(Player player, Level level, ItemStack stack,
                                                            BlockHitResult hit);

    // ---- Fluid presentation ----

    /** The loader-native display name for the stored fluid and its variant payload. */
    Component fluidDisplayName(StoredFluid fluid);

    /**
     * Resolves the loader-native RGB tint for a stored fluid.
     *
     * @param fallback color returned when the loader has no tint for the fluid
     * @return the loader tint, or {@code fallback}
     */
    int fluidColor(StoredFluid fluid, int fallback);

    /** The loader-resolved bucket fill sound for {@code fluid}. */
    SoundEvent fillSound(StoredFluid fluid);

    /** The loader-resolved bucket empty sound for {@code fluid}. */
    SoundEvent emptySound(StoredFluid fluid);

    // ---- Mob Bucket aquatic water ----

    /**
     * Removes one source-water block for aquatic mob capture through the loader's native world
     * pickup contract, including its sound and fluid-pickup game event.
     *
     * @return {@code true} when the block gave up its water or the client predicted it
     */
    boolean takeAquaticSourceWater(Level level, BlockPos pos, StoredFluid expected, @Nullable Player player);

    /**
     * Places one water source at {@code pos} for a released aquatic Mob Bucket mob through the
     * loader's native fluid-placement contract, checking {@link ProtectionAction#FLUID_EDIT}
     * authorization itself and including the applicable sound and fluid-place game event.
     *
     * @return {@code true} when the water was placed or evaporated in an ultra-warm dimension
     */
    boolean placeAquaticSourceWater(Level level, BlockPos pos, ItemStack stack, ProtectionContext context,
                                    Direction face);

    // ---- Sided block fluid storage ----

    /**
     * Read-only preview of whether a finite bucket could take one bucket-volume from a sided block
     * store at the hit. Protection is not evaluated and no state changes.
     */
    BlockFluidOutcome previewBlockTake(Level level, BlockHitResult hit, ItemStack stack);

    /**
     * Attempts to take one bucket-volume from a sided block store into the bucket, checking
     * {@link ProtectionAction#BLOCK_INTERACT} and, on server success, crediting the bucket (a finite
     * bucket) or assigning it (an empty Source Bucket). A present store owns dispatch even when it
     * refuses.
     *
     * @param asSource whether the acting bucket is a Source Bucket
     */
    BlockFluidOutcome blockTake(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                                boolean asSource);

    /**
     * Attempts to place one bucket-volume from the bucket into a sided block store, checking
     * {@link ProtectionAction#BLOCK_INTERACT} and, on server success, debiting a finite bucket while
     * leaving a Source Bucket unchanged. A present store owns dispatch even when it refuses.
     *
     * @param asSource whether the acting bucket is a Source Bucket
     */
    BlockFluidOutcome blockPlace(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                                 boolean asSource);

    /**
     * Classifies a present sided block store for an assigned Source Bucket without checking
     * protection or mutating either side.
     *
     * @return the classification, or {@code null} when no sided store is present
     */
    @Nullable
    SourceTarget classifyBlockTarget(Level level, BlockHitResult hit, ItemStack stack);

    // ---- Vanilla water/lava cauldron transitions ----

    /**
     * Drains one bucket-volume of {@code fluid} (water or lava) from a full cauldron at {@code pos}
     * into the bucket, emptying the cauldron. Checks {@link ProtectionAction#BLOCK_INTERACT}, plays
     * the fill sound, and on server success credits a finite bucket while leaving a Source Bucket
     * unchanged. Loaders that expose vanilla cauldrons as sided fluid storage return {@code false}
     * here and rely on {@link #blockTake}.
     *
     * @return {@code true} when the transition happened
     */
    boolean cauldronTake(Level level, BlockPos pos, Direction face, ItemStack stack, Fluid fluid,
                         ProtectionContext context);

    /**
     * Fills an empty cauldron at {@code pos} to a full {@code fluid} (water or lava) cauldron from
     * the bucket. Checks {@link ProtectionAction#BLOCK_INTERACT}, plays the empty sound, and on
     * server success debits a finite bucket while leaving a Source Bucket unchanged. Loaders that
     * expose vanilla cauldrons as sided fluid storage return {@code false} here and rely on
     * {@link #blockPlace}.
     *
     * @return {@code true} when the transition happened
     */
    boolean cauldronPlace(Level level, BlockPos pos, Direction face, ItemStack stack, Fluid fluid,
                          ProtectionContext context);

    // ---- Arbitrary fluid world placement ----

    /**
     * Places one bucket-volume of {@code stored} into the world honoring the loader's vaporization,
     * block-state, and empty-sound rules. Checks {@link ProtectionAction#FLUID_EDIT} (and
     * {@link ProtectionAction#BLOCK_EDIT} when a replaceable block would be destroyed), emits the
     * fluid-place game event, and on server success debits a finite bucket while leaving a Source
     * Bucket unchanged.
     *
     * @param asSource whether the acting bucket is a Source Bucket
     * @param allowFaceOffset whether an unusable clicked position may resolve to the neighbor
     * @return {@code true} for an accepted client prediction or a completed server placement
     */
    boolean placeArbitraryFluid(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                                StoredFluid stored, boolean asSource, boolean allowFaceOffset);

    /**
     * Resolves the position an arbitrary-fluid placement would target without checking protection or
     * changing state.
     *
     * @param allowFaceOffset whether placement may target the neighbor along the clicked face
     * @return the candidate target; placement is not guaranteed to succeed there
     */
    BlockPos resolveArbitraryPlaceTarget(Level level, BlockHitResult hit, ItemStack stack,
                                         @Nullable Player player, InteractionHand hand, StoredFluid stored,
                                         boolean allowFaceOffset);

    // ---- Powder snow ----

    /**
     * Places one stored powder-snow block. The loader builds the {@code BlockPlaceContext} (whose
     * constructor is not accessible to common), honors {@code allowFaceOffset}, checks
     * {@link ProtectionAction#BLOCK_EDIT} at the resolved position, runs
     * {@link net.minecraft.world.item.BlockItem#place} on
     * {@link net.minecraft.world.item.Items#POWDER_SNOW_BUCKET} with its own block-place-event and
     * rollback behavior, and on server success debits one unit. The caller has already guarded mode
     * and a positive unit count.
     *
     * @param allowFaceOffset whether an unusable clicked position may resolve to the neighbor
     * @return {@code true} for an accepted client prediction or a committed server placement
     */
    boolean placeStoredPowder(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                              boolean allowFaceOffset);
}
