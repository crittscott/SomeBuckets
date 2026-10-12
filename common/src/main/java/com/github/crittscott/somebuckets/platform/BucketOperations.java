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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Loader-specific primitives used by the shared {@code FluidTransactions} and {@code HeldTransfers}
 * orchestration and by shared bucket items directly. This interface is the whole server-side loader
 * surface: the dispenser's automation player, sided block fluid stores, arbitrary-fluid world
 * placement, per-fluid fill/empty sounds, fluid presentation, held-container fluid moves,
 * item-inventory detection, mob-bucket contents, the protection events, the item pickup and toss
 * events, checked world placement, and the Forge {@code FillBucketEvent} carve-out.
 *
 * <p>World-operation methods are called on both logical sides. Unless stated otherwise, a
 * {@code true} result means an accepted client prediction or a completed server operation;
 * {@code false} means the operation was rejected without changing bucket or world state. The server
 * is authoritative. Signatures stay in vanilla and {@link StoredFluid} terms; loader-native fluid
 * values are converted at the loader boundary.
 */
public interface BucketOperations {
    /** Stable identity used by dispenser-owned actions on every loader. */
    GameProfile DISPENSER_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes((SomeBuckets.MODID + ":dispenser").getBytes(StandardCharsets.UTF_8)),
            "[SomeBuckets]");

    /**
     * A loader-native fluid store exposed by one block face, in millibuckets. A simulated call
     * changes nothing; an executed call commits at once. Common code owns the transaction around it:
     * preview, protection, the bucket side, and observability.
     */
    interface BlockFluidStore {
        /**
         * The fluids the store may yield, in the order it offers them. Only identity and variant are
         * meaningful; amounts are not. Empty when the store holds no fluid.
         */
        List<StoredFluid> offered();

        /**
         * Removes up to {@code request.amount()} of exactly {@code request}'s variant.
         *
         * @return the fluid removed, or {@link StoredFluid#EMPTY}
         */
        StoredFluid drain(StoredFluid request, boolean simulate);

        /**
         * Inserts up to {@code offered.amount()} of {@code offered}.
         *
         * @return the millibuckets accepted
         */
        int fill(StoredFluid offered, boolean simulate);
    }

    /** The entity type and fluid a vanilla-style {@code MobBucketItem} carries. */
    record MobBucketContents(EntityType<?> entityType, Fluid fluid) {}

    /**
     * One held-container fluid move: the fluid moved and both containers as they stand afterward.
     * A Some Buckets container is edited in place and reported as the same stack; a foreign
     * container may be reported as a different stack, such as a filled bucket for an empty one.
     */
    record HeldMove(StoredFluid fluid, ItemStack from, ItemStack to) {}

    /** Holds the loader-installed implementation without forcing eager platform initialization. */
    final class Holder {
        private static BucketOperations instance;
        private Holder() {}
    }

    /**
     * Installs the loader implementation used by common item code, replacing any previous instance.
     * Called once during single-threaded mod bootstrap.
     *
     * @throws NullPointerException if {@code operations} is {@code null}
     */
    static void install(BucketOperations operations) {
        Holder.instance = Objects.requireNonNull(operations, "operations");
    }

    /**
     * Returns the installed loader implementation.
     *
     * @throws IllegalStateException if the loader entry point has not installed an implementation
     */
    static BucketOperations get() {
        BucketOperations operations = Holder.instance;
        if (operations == null) throw new IllegalStateException("Bucket operations are not installed");
        return operations;
    }

    /** Returns the level's stable fake player that dispensers act as. Server-only. */
    ServerPlayer automationPlayer(ServerLevel level);

    // ---- Held transfer ----

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

    /**
     * Reads the entity type and fluid of a {@code MobBucketItem} stack, vanilla or modded.
     *
     * @return the contents, or {@code null} when the stack is not a {@code MobBucketItem}
     */
    @Nullable
    MobBucketContents mobBucketContents(ItemStack stack);

    // ---- Block fluid storage and container discovery ----

    /**
     * Returns the loader fluid store the block at {@code pos} exposes on {@code face}. Vanilla
     * cauldrons never report one, since {@code Cauldrons} owns them on every loader. A present store
     * owns the interaction even when it refuses.
     *
     * @return the store, or {@code null} when the face exposes none
     */
    @Nullable
    BlockFluidStore blockFluidStore(Level level, BlockPos pos, Direction face);

    /**
     * Tests whether the stack exposes a loader-native item-inventory handler (backpacks, pouches,
     * crates). Junk and Trash Bucket intake consults this so a modded portable container is refused
     * even when it leaves {@link net.minecraft.world.item.Item#canFitInsideContainerItems()} set.
     */
    boolean carriesItemContainer(ItemStack stack);

    // ---- Protection and pickup events ----

    /**
     * Posts the loader's block-break event for {@code actor} removing the block or fluid at
     * {@code pos}, before the world changes. Server-only.
     *
     * @return {@code true} when no listener refused the break
     */
    boolean permitsBlockBreak(ServerLevel level, Player actor, BlockPos pos);

    /**
     * Posts the loader's block-interaction event for {@code actor} using the item in {@code hand}
     * on the block at {@code hit}. Server-only.
     *
     * @return {@code true} when no listener refused the interaction
     */
    boolean permitsBlockInteract(ServerLevel level, Player actor, InteractionHand hand, BlockHitResult hit);

    /**
     * Posts the loader's entity-interaction event for {@code actor} using the item in {@code hand}
     * on {@code target}. Server-only.
     *
     * @return {@code true} when no listener refused the interaction
     */
    boolean permitsEntityInteract(ServerLevel level, Player actor, InteractionHand hand, Entity target);

    /**
     * Posts the loader's item-pickup event for {@code actor} taking {@code entity}, which item filters
     * and claim mods use to veto a collection. Server-only.
     *
     * @return {@code false} when a listener denies the pickup
     */
    boolean allowsItemPickup(ItemEntity entity, Player actor);

    /**
     * Posts the loader's post-pickup event after a real player's Junk or Trash Bucket intake has
     * shrunk {@code entity}'s stack, as vanilla pickup does before the entity is discarded.
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

    /**
     * Runs {@code place}, one world placement of {@code placed} at {@code pos} against {@code face},
     * under the loader's block-place check for the context's actor on the server, so claim and
     * protection mods judge the block actually placed. Forge records the placement's block changes,
     * posts the place event for them, and restores them when it is refused; recording armed around
     * an enclosing {@code useOn} is suspended meanwhile, so Forge neither repeats nor defers the
     * event. Fabric has no recording and posts Architectury's place event and consults Common
     * Protection API, when installed, before {@code place} runs. Client prediction runs {@code place}
     * unchecked. Callers debit the bucket only after success.
     *
     * @param placed the state the placement produces, for loaders that check before placing
     * @param place performs the placement, returning {@code false} when it did not happen
     * @return {@code true} when the placement happened and stands
     */
    boolean placeChecked(Level level, ProtectionContext context, BlockPos pos, Direction face,
                         BlockState placed, BooleanSupplier place);

    /**
     * Forge-only lazy pre-dispatch hook firing {@code FillBucketEvent}. Fabric returns {@code null}
     * without resolving {@code hit}. Common code treats {@code null} as "continue normal bucket
     * processing". Only cancellation is honored; a listener cannot substitute a filled bucket for a
     * Some Buckets item. The supplier may return {@code null} when no block operation should be
     * announced.
     *
     * @return {@link InteractionResult#FAIL} when a Forge listener cancelled the use, or
     *         {@code null} to continue
     */
    @Nullable
    InteractionResult beforeWorldBucketUse(Player player, Level level, ItemStack stack,
                                           Supplier<BlockHitResult> hit);

    // ---- Fluid presentation ----

    /** The loader-native display name for the stored fluid and its variant payload. */
    Component fluidDisplayName(StoredFluid fluid);

    /** The bucket fill sound the loader declares for {@code fluid}, or empty when it declares none. */
    Optional<SoundEvent> fillSound(StoredFluid fluid);

    /**
     * The pickup sound {@code pickup} declares for {@code state}, through the loader's state-aware
     * lookup where one exists.
     */
    Optional<SoundEvent> pickupSound(BucketPickup pickup, BlockState state);

    /** The bucket empty sound the loader declares for {@code fluid}, or empty when it declares none. */
    Optional<SoundEvent> emptySound(StoredFluid fluid);

    // ---- Arbitrary fluid world placement ----

    /**
     * Places one bucket-volume of {@code stored} into the world honoring the loader's vaporization,
     * block-state, and empty-sound rules. Checks {@link Protections#mayModify}, places through
     * {@link Protections#placeChecked}, emits the fluid-place game event, and on server success
     * debits a finite bucket while leaving a Source Bucket unchanged.
     *
     * @param allowFaceOffset whether an unusable clicked position may resolve to the neighbor
     * @return {@code true} for an accepted client prediction or a completed server placement
     */
    boolean placeArbitraryFluid(Level level, BlockHitResult hit, ItemStack stack, ProtectionContext context,
                                StoredFluid stored, boolean allowFaceOffset);

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
}
