package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Loader-specific primitives used by the shared {@code FluidTransactions} and {@code HeldTransfers}
 * orchestration and by shared bucket items directly. This interface is the whole server-side loader
 * surface: the dispenser's automation player, a sided block-storage probe and one-unit move,
 * arbitrary-fluid world placement, per-fluid fill/empty sounds, native powder-snow placement
 * finalization, fluid presentation, held-container fluid moves, item-inventory detection, the item
 * pickup and toss events, the player block-break and block-place protection events, and the Forge
 * {@code FillBucketEvent} carve-out.
 *
 * <p>World-operation methods are called on both logical sides. Unless stated otherwise, a
 * {@code true} result means an accepted client prediction or a completed server operation;
 * {@code false} means the operation was rejected without changing bucket or world state. The server
 * is authoritative. Keep signatures in vanilla and {@link StoredFluid} terms; convert loader-native
 * fluid values at the loader boundary.
 */
public interface BucketOperations {
    /** Stable identity used by dispenser-owned actions on every loader. */
    GameProfile DISPENSER_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes((SomeBuckets.MODID + ":dispenser").getBytes(StandardCharsets.UTF_8)),
            "[SomeBuckets]");

    /**
     * Translation key shown to the acting player when a native fluid container reports a transfer
     * result that contradicts its own simulation.
     */
    String FLUID_TRANSFER_INCONSISTENT_KEY = "message.somebuckets.fluid_transfer_inconsistent";

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

    /**
     * Returns the level's stable fake player that dispensers act as. Server-only.
     *
     * @param level server level the automation acts in
     * @return the loader's automation player for that level
     */
    ServerPlayer automationPlayer(ServerLevel level);

    // ---- Held transfer ----

    /**
     * One held-container fluid move: the fluid moved and both containers as they stand afterward.
     * A Some Buckets container is edited in place and reported as the same stack; a foreign
     * container may be reported as a different stack, such as a filled bucket for an empty one.
     */
    record HeldMove(StoredFluid fluid, ItemStack from, ItemStack to) {}

    /**
     * Moves as much fluid as the pair allows from one one-count container item to another through
     * the loader's item fluid storage. At least one side is a Big, Huge, or Source Bucket.
     *
     * @param unlimited whether {@code from} is an assigned, allowed Source Bucket, which fills
     *                  {@code to} to capacity with its fluid without depleting
     * @return the move, or {@code null} when nothing moved and neither stack changed
     */
    @Nullable
    HeldMove moveHeldFluid(ItemStack from, ItemStack to, boolean unlimited);

    /** Whether {@code stack} exposes item fluid storage that currently holds fluid. */
    boolean holdsFluid(ItemStack stack);

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

    /**
     * Posts the loader's player item-pickup event, which item filters and claim mods use to veto a
     * player collecting a dropped item. Junk and Trash Bucket intake by a real player consults this
     * before taking from {@code entity}.
     *
     * @return {@code false} when a listener denies the pickup
     */
    boolean allowsItemPickup(ItemEntity entity, Player player);

    /**
     * Posts the loader's post-pickup event after a real player's Junk or Trash Bucket intake has
     * shrunk {@code entity}'s stack, as vanilla pickup does before the entity is discarded. Fabric
     * has no such event.
     *
     * @param original copy of the entity's stack before intake
     * @param count number of items collected
     */
    void afterItemPickup(Player player, ItemEntity entity, ItemStack original, int count);

    /**
     * Throws {@code stack} from {@code player} the way the drop-item key does, posting the loader's
     * toss event where one exists.
     *
     * @return {@code true} when the stack entered the world; {@code false} when nothing was thrown,
     *         including a cancelled toss
     */
    boolean tossFromPlayer(Player player, ItemStack stack);

    // ---- Protection events ----

    /**
     * Posts the loader's block-break check for a real player removing the fluid or block at
     * {@code pos}, the event claim and protection mods observe. Server-only.
     *
     * @return {@code false} when a listener denies the removal
     */
    boolean permitsBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos);

    /**
     * Posts the loader's block-place check for a real player placing fluid at {@code pos} against
     * {@code face}, before the world changes. Server-only.
     *
     * @return {@code false} when a listener denies the placement
     */
    boolean permitsBlockPlace(ServerLevel level, ServerPlayer player, BlockPos pos, Direction face);

    // ---- Forge FillBucketEvent carve-out ----

    /**
     * Whether this loader fires a world bucket-use event, so shared item code should pre-resolve the
     * affected block and call {@link #beforeWorldBucketUse}. Only Forge does; NeoForge and Fabric
     * return {@code false}.
     */
    boolean firesWorldBucketEvent();

    /**
     * Forge-only pre-dispatch hook firing {@code FillBucketEvent}. NeoForge and Fabric return
     * {@code null}. Common code treats {@code null} as "continue normal bucket processing". Only
     * cancellation is honored; a listener cannot substitute a filled bucket for a Some Buckets item.
     *
     * @return {@link InteractionResult#FAIL} when a Forge listener cancelled the use, or
     *         {@code null} to continue
     */
    @Nullable
    InteractionResult beforeWorldBucketUse(Player player, Level level, ItemStack stack,
                                           BlockHitResult hit);

    // ---- Saved-data migration ----

    /**
     * Whether this loader's 1.21.1 release saved a stored fluid's variant as the loader's native raw
     * tag rather than an encoded component patch. Only Forge does, because its fluid stack carried a
     * free-form tag; NeoForge and Fabric return {@code false}.
     */
    boolean releasedFluidVariantIsRawTag();

    // ---- Fluid presentation ----

    /** The loader-native display name for the stored fluid and its variant payload. */
    Component fluidDisplayName(StoredFluid fluid);

    /** The loader-resolved bucket fill sound for {@code fluid}. */
    SoundEvent fillSound(StoredFluid fluid);

    /**
     * The pickup sound {@code pickup} declares for {@code state}, through the loader's state-aware
     * lookup where one exists.
     */
    Optional<SoundEvent> pickupSound(BucketPickup pickup, BlockState state);

    /** The loader-resolved bucket empty sound for {@code fluid}. */
    SoundEvent emptySound(StoredFluid fluid);

    // ---- Sided block fluid storage ----

    /**
     * Read-only preview of whether a finite bucket could take one bucket-volume from a sided block
     * store at the hit. Protection is not evaluated and no state changes.
     */
    BlockFluidOutcome previewBlockTake(Level level, BlockHitResult hit, ItemStack stack);

    /**
     * Attempts to take one bucket-volume from a sided block store into the bucket, checking
     * {@link Protections#mayModify} and, on server success, crediting the bucket (a finite
     * bucket) or assigning it (an empty Source Bucket). A present store owns dispatch even when it
     * refuses.
     *
     * @param asSource whether the acting bucket is a Source Bucket
     */
    BlockFluidOutcome blockTake(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                                boolean asSource);

    /**
     * Attempts to place one bucket-volume from the bucket into a sided block store, checking
     * {@link Protections#mayModify} and, on server success, debiting a finite bucket while
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

    // ---- Arbitrary fluid world placement ----

    /**
     * Places one bucket-volume of {@code stored} into the world honoring the loader's vaporization,
     * block-state, and empty-sound rules. Checks {@link Protections#mayModify}, emits the
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
                                         Player player, InteractionHand hand, StoredFluid stored,
                                         boolean allowFaceOffset);

    // ---- Powder snow ----

    /**
     * Runs {@link BlockItem#place} for a stored powder-snow block so that the loader's block-place
     * event is posted from inside {@code place()}, where a cancellation still fails the placement
     * before the caller debits the bucket.
     *
     * @return the placement result
     */
    InteractionResult placePowderBlock(BlockItem item, BlockPlaceContext placement);
}
