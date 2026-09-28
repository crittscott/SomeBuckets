package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Infinite source and sink assigned to one server-allowed fluid or to allowed milk. Stacks like a
 * vanilla bucket: up to {@value SomeBucketItem#EMPTY_STACK_SIZE} while unassigned, one once
 * assigned. The allowlist is enforced at assignment and every later input or output boundary;
 * disallowed existing assignments retain their state but remain inert until reset.
 * Dynamic names append a content suffix to the registered description ID.
 */
public class SBItem extends FluidBucketItem {

    /**
     * Creates a Source Bucket.
     *
     * @param props base item properties
     */
    public SBItem(Properties props) {
        super(props.rarity(Rarity.RARE));
    }

    /* ------------------------- Container rules ------------------------- */

    /** A Source Bucket reports one bucket volume: it supplies and sinks one unit per call. */
    @Override
    public int getCapacityMb() {
        return BUCKET_VOLUME_MB;
    }

    @Override
    public boolean acceptsFluid(Fluid fluid) {
        return SBPolicy.allows(fluid);
    }

    /**
     * An unassigned bucket takes an allowed fluid as its assignment; an assigned bucket sinks up to
     * one bucket volume of its own variant without storing it.
     */
    @Override
    public int acceptable(ItemStack stack, StoredFluid offered) {
        if (offered.isEmpty() || !SBPolicy.allows(offered.fluid())) return 0;
        BucketState.Mode mode = BucketState.getMode(stack);
        boolean admits = mode == BucketState.Mode.NONE
                || (mode == BucketState.Mode.FLUID && BucketState.getStoredFluid(stack).isSameVariant(offered));
        return admits ? Math.min(BUCKET_VOLUME_MB, offered.amount()) : 0;
    }

    /** Records the assignment of an unassigned bucket; an assigned bucket keeps its identity unchanged. */
    @Override
    public void insert(ItemStack stack, StoredFluid offered, int amount) {
        if (BucketState.getMode(stack) == BucketState.Mode.NONE) {
            BucketState.setStoredFluid(stack, offered.withAmount(BUCKET_VOLUME_MB));
        }
    }

    /** An assigned, allowed bucket yields up to one bucket volume without depleting. */
    @Override
    public StoredFluid extractable(ItemStack stack, int maxMb) {
        if (BucketState.getMode(stack) != BucketState.Mode.FLUID || maxMb <= 0) return StoredFluid.EMPTY;
        StoredFluid current = BucketState.getStoredFluid(stack);
        if (!SBPolicy.allows(current.fluid())) return StoredFluid.EMPTY;
        return current.withAmount(Math.min(BUCKET_VOLUME_MB, maxMb));
    }

    /** Nothing is removed from an infinite source. */
    @Override
    public void extract(ItemStack stack, int amount) {
    }

    /**
     * Handles a use against a clicked cauldron or loader fluid store, after vanilla dispatch posted
     * the block-interaction event, with the same gestures {@link #use} applies to world fluid: an
     * unassigned bucket assigns itself from the block, and an assigned bucket places into it, or
     * removes one matching unit when sneaking. Every other use passes.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), context.isInside());
        if (!FluidBucketItem.isBlockTarget(level, hit, true)) return InteractionResult.PASS;

        InteractionHand hand = context.getHand();
        ItemStack stack = context.getItemInHand();
        boolean acted = switch (BucketState.getMode(stack)) {
            case NONE -> HeldTransfers.fillFromHand(level, player, hand, stack,
                    bucket -> FluidTransactions.tryTakeSource(level, hit, bucket, player, hand));
            case FLUID -> player.isShiftKeyDown()
                    ? FluidTransactions.classifySourceTarget(level, hit, stack) == BucketOperations.SourceTarget.MATCHING_FLUID
                            && FluidTransactions.tryTakeSource(level, hit, stack, player, hand)
                    : FluidTransactions.tryPlaceSource(level, hit, stack, player, hand);
            default -> false;
        };
        if (!acted) return InteractionResult.PASS;
        return success(level);
    }

    /**
     * Drives the Source Bucket gesture. A held-container transfer takes priority; then an assigned
     * bucket places its fluid on a normal targeted use, removes one matching source unit on a
     * sneak-targeted use, drinks assigned milk, or resets to empty on a sneak-use against air. An
     * unassigned bucket assigns itself from an allowed targeted source. The assignment never changes
     * on a take or place. Cauldrons and fluid stores are left to {@link #useOn}.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult targetHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (FluidBucketItem.tryCrossHandTransfer(level, player, hand, stack, targetHit)) {
            return success(level);
        }

        BucketState.Mode mode = BucketState.getMode(stack);
        // Sneak-use against air resets any assignment, milk included; tryShiftClear no-ops for an
        // unassigned bucket and for a non-sneak or block-targeted use, so a normal milk drink falls
        // through to the branch below.
        if (FluidBucketItem.tryShiftClear(level, player, stack, targetHit)) {
            return success(level);
        }
        if (mode == BucketState.Mode.MILK) {
            if (!SBPolicy.allowsMilk()) return InteractionResult.PASS;
            // Vanilla's use starts the stack's CONSUMABLE
            return super.use(level, player, hand);
        }

        if (mode == BucketState.Mode.NONE) {
            BlockHitResult takeHit = FluidBucketItem.withoutBlockTarget(level,
                    getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY), true);
            if (takeHit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;

            InteractionResult claimed = FluidBucketItem.beforeWorldBucketUse(
                    player, level, stack, () -> takeHit);
            if (claimed != null) return claimed;
            if (HeldTransfers.fillFromHand(level, player, hand, stack,
                    bucket -> FluidTransactions.tryTakeSource(level, takeHit, bucket, player, hand))) {
                return success(level);
            }
            return InteractionResult.PASS;
        }

        if (mode == BucketState.Mode.FLUID) {
            if (player.isShiftKeyDown()) {
                BlockHitResult takeHit = FluidBucketItem.withoutBlockTarget(level, targetHit, true);
                if (takeHit.getType() != HitResult.Type.BLOCK
                        || FluidTransactions.classifySourceTarget(level, takeHit, stack)
                        != BucketOperations.SourceTarget.MATCHING_FLUID) {
                    return InteractionResult.PASS;
                }
                InteractionResult claimed = FluidBucketItem.beforeWorldBucketUse(
                        player, level, stack, () -> takeHit);
                if (claimed != null) return claimed;
                if (FluidTransactions.tryTakeSource(level, takeHit, stack, player, hand)) {
                    return success(level);
                }
                return InteractionResult.PASS;
            }

            BlockHitResult placeHit = FluidBucketItem.withoutBlockTarget(level,
                    getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE), true);
            if (placeHit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
            InteractionResult claimed = FluidBucketItem.beforeWorldBucketUse(player, level, stack, () -> {
                BlockHitResult eventHit = FluidBucketItem.withPos(placeHit,
                        FluidTransactions.resolveSourcePlaceTarget(
                                level, placeHit, stack, player, hand, true));
                return eventHit;
            });
            if (claimed != null) return claimed;
            if (FluidTransactions.tryPlaceSource(level, placeHit, stack, player, hand)) {
                return success(level);
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * Milks an adult cow with an unassigned bucket and assigns milk mode, when the allowlist permits
     * milk.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Cow cow) || cow.isBaby()) return InteractionResult.PASS;
        if (BucketState.getMode(stack) != BucketState.Mode.NONE) return InteractionResult.PASS;
        if (!SBPolicy.allowsMilk()) return InteractionResult.PASS;
        return milkInto(stack, player, cow, hand, BUCKET_VOLUME_MB);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (BucketState.getMode(stack) == BucketState.Mode.MILK && SBPolicy.allowsMilk()) {
            FluidBucketItem.finishMilkDrink(stack, level, user, false);
        }
        return stack;
    }

    /**
     * Returns the crafting leftover for one use of this bucket as an ingredient.
     *
     * @param stack the bucket stack consumed by the recipe
     * @return a 1-count copy with its assignment intact, since a Source Bucket is an infinite source;
     *         {@link ItemStack#EMPTY} for an unassigned bucket
     */
    @Override
    public ItemStack getUnitRemainder(ItemStack stack) {
        if (BucketState.isEmptyBucket(stack)) return ItemStack.EMPTY;
        ItemStack result = stack.copy();
        result.setCount(1);
        return result;
    }
}
