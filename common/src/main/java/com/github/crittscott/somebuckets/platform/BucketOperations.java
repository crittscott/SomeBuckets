package com.github.crittscott.somebuckets.platform;

import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Cross-loader fluid transactions and interaction hooks used by shared bucket items.
 *
 * <p>World-operation methods are called on both logical sides. Unless stated otherwise, a
 * {@code true} result means an accepted client prediction or a completed server operation;
 * {@code false} means the operation was rejected without changing bucket or world state. The server
 * is authoritative. When the clicked face exposes loader fluid storage, that storage owns dispatch
 * even if it refuses the requested transfer; implementations do not then fall back to world-fluid
 * behavior.
 *
 * <p>The contract is loader-neutral except for {@link #firesWorldBucketEvent} and
 * {@link #beforeWorldBucketUse}, a Forge-specific {@code FillBucketEvent} carve-out that NeoForge and
 * Fabric no-op. Shared item code skips the whole event-target pre-resolution when
 * {@link #firesWorldBucketEvent} is {@code false}. Keep signatures here in vanilla and
 * {@link StoredFluid} terms; convert loader-native fluid values at the loader boundary rather than
 * adding a loader-shaped method for one platform's convenience.
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
     * <p>A successful call may replace either hand's stack and may settle container remainders. The
     * client performs the same hand-state prediction; world drops and other authoritative side
     * effects occur only on the server.
     *
     * @param bucketHand hand holding the Some Buckets container
     * @param bucket the Some Buckets container stack
     * @param otherHand hand holding the other container
     * @param other the other container stack
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
     * @param stack candidate stack
     * @return {@code true} when the stack exposes an item-inventory handler
     */
    boolean carriesItemContainer(ItemStack stack);

    // ---- Forge FillBucketEvent carve-out ----

    /**
     * Whether this loader fires a world bucket-use event, so shared item code should pre-resolve the
     * affected block and call {@link #beforeWorldBucketUse}. Only Forge does; NeoForge and Fabric
     * return {@code false} and skip the pre-resolution entirely.
     */
    boolean firesWorldBucketEvent();

    /**
     * Forge-only pre-dispatch hook. Forge fires its {@code FillBucketEvent} here (via
     * {@code ForgeEventFactory.onBucketUse}) so other mods and protection systems can veto or claim a
     * bucket use before common processing. NeoForge and Fabric have no equivalent event and return
     * {@code null}. Common code treats {@code null} as "continue normal bucket processing" and only
     * calls this when {@link #firesWorldBucketEvent} is {@code true}.
     *
     * @return the final interaction result when a Forge listener claimed the interaction, or
     *         {@code null} to continue
     */
    @Nullable
    InteractionResultHolder<ItemStack> beforeWorldBucketUse(Player player, Level level, ItemStack stack,
                                                            BlockHitResult hit);

    // ---- Fluid presentation ----

    /**
     * Returns the loader-native display name for the stored fluid and its variant payload.
     *
     * @param fluid stored fluid to name
     * @return the loader-native display name component
     */
    Component fluidDisplayName(StoredFluid fluid);

    /**
     * Resolves the loader-native RGB tint for a stored fluid.
     *
     * @param fluid stored fluid to tint
     * @param fallback color returned when the loader has no tint for the fluid
     * @return the loader tint, or {@code fallback}
     */
    int fluidColor(StoredFluid fluid, int fallback);

    // ---- Mob Bucket aquatic water ----

    /**
     * Removes one source-water block for aquatic mob capture through the loader's native world
     * pickup contract, including its sound and fluid-pickup game event.
     *
     * @param pos block the captured mob occupies
     * @param expected the water value the caller requires
     * @param player acting player, or {@code null} for automation
     * @return {@code true} when the block gave up its water or the client predicted it
     */
    boolean takeAquaticSourceWater(Level level, BlockPos pos, StoredFluid expected,
                                   @Nullable Player player);

    /**
     * Places one water source at {@code pos} for a released aquatic Mob Bucket mob through the
     * loader's native fluid-placement contract, checking {@link ProtectionAction#FLUID_EDIT}
     * authorization itself and including the applicable sound and fluid-place game event.
     *
     * @param pos destination block
     * @param stack the Mob Bucket stack driving the release
     * @param context authorization identity
     * @param face release direction to authorize against
     * @return {@code true} when the water was placed or evaporated in an ultra-warm dimension;
     *         {@code false} when placement was denied or impossible
     */
    boolean placeAquaticSourceWater(Level level, BlockPos pos, ItemStack stack, ProtectionContext context,
                                    Direction face);

    // ---- Big and Huge Bucket fluid ----

    /**
     * Performs a read-only preview of whether a finite bucket can take one bucket-volume at the hit.
     * Protection is not evaluated and no state is changed.
     *
     * @return {@code true} when the pickup could be attempted
     */
    boolean canAttemptBigTake(Level level, BlockHitResult hit, ItemStack stack);

    /**
     * Attempts to take one bucket-volume into a finite Big or Huge Bucket for a player.
     *
     * @return whether pickup was predicted or completed
     */
    boolean tryBigTake(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    /**
     * Attempts to place one bucket-volume from a finite Big or Huge Bucket for a player.
     *
     * @return whether placement was predicted or completed
     */
    boolean tryBigPlace(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    /**
     * Resolves the position a finite-fluid placement would target without checking protection or
     * changing state.
     *
     * @param allowFaceOffset whether placement may target the neighbor along the clicked face
     * @return the candidate target; this does not guarantee that placement will succeed
     */
    BlockPos resolveBigPlaceTarget(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                   InteractionHand hand, boolean allowFaceOffset);

    // ---- Powder snow ----

    /**
     * Performs a read-only capacity and block-state preview for one powder-snow pickup. Protection is
     * not evaluated and no state is changed.
     *
     * @return {@code true} when the pickup could be attempted
     */
    boolean canAttemptPowderTake(Level level, BlockHitResult hit, ItemStack stack);

    /**
     * Attempts to collect one powder-snow block into a finite bucket for a player.
     *
     * @return whether the pickup was predicted or completed
     */
    boolean tryPowderTake(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    /**
     * Attempts native placement of one stored powder-snow block for a player.
     *
     * @return whether the placement was predicted or completed
     */
    boolean tryPowderPlace(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    // ---- Source Bucket fluid ----

    /**
     * Attempts to take one allowed bucket-volume at the hit. An empty Source Bucket is assigned; an
     * assigned Source Bucket accepts only its matching fluid and preserves its identity.
     *
     * @return whether the pickup or assignment was predicted or completed
     */
    boolean trySourceTake(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    /**
     * Classifies an assigned Source Bucket's exact target without checking protection or mutating
     * either side. A present block-fluid store owns the result even when it cannot be drained.
     *
     * @return the target classification
     */
    SourceTarget classifySourceTarget(Level level, BlockHitResult hit, ItemStack stack);

    /**
     * Attempts to place one bucket-volume from an assigned Source Bucket without consuming its stored
     * identity.
     *
     * @return whether the placement was predicted or completed
     */
    boolean trySourcePlace(Level level, BlockHitResult hit, ItemStack stack, Player player, InteractionHand hand);

    /**
     * Resolves the position a Source Bucket fluid placement would target without checking protection
     * or changing state.
     *
     * @param allowFaceOffset whether placement may target the neighbor along the clicked face
     * @return the candidate target; this does not guarantee that placement will succeed
     */
    BlockPos resolveSourcePlaceTarget(Level level, BlockHitResult hit, ItemStack stack, Player player,
                                      InteractionHand hand, boolean allowFaceOffset);
}
