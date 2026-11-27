package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
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
 * disallowed existing assignments retain their state but remain inert until reset by a sneak-use
 * against air.
 * Dynamic names append a content suffix to the registered description ID, and the model uses
 * {@link FluidBucketItem#CONTENT_PROPERTY} for the shared content-state protocol.
 */
public class SBItem extends FluidBucketItem {

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
     * one bucket volume of its own content without storing it.
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
        if (BucketState.getMode(stack) != BucketState.Mode.NONE) return;
        BucketState.setStoredFluid(stack, offered.withAmount(BUCKET_VOLUME_MB));
    }

    /** An assigned, allowed bucket yields up to one bucket volume without depleting. */
    @Override
    public StoredFluid extractable(ItemStack stack, int maxMb) {
        if (maxMb <= 0 || BucketState.getMode(stack) != BucketState.Mode.FLUID) return StoredFluid.EMPTY;
        StoredFluid current = BucketState.getStoredFluid(stack);
        return SBPolicy.allows(current.fluid())
                ? current.withAmount(Math.min(BUCKET_VOLUME_MB, maxMb)) : StoredFluid.EMPTY;
    }

    /** Nothing is removed from an infinite source. */
    @Override
    public void extract(ItemStack stack, int amount) {
    }

    /* ------------------------- Use (right-click) ------------------------- */

    /**
     * Handles a use against a clicked vanilla cauldron or loader fluid store, after vanilla dispatch
     * posted the block-interaction event, with the same gestures {@link #use} applies to world fluid:
     * an unassigned bucket assigns itself from the block, and an assigned bucket places into it, or
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
        // Forge's useOn capture would otherwise post this block use as a placement, and its refusal
        // rollback cannot see inventory changes such as an assigned bucket from a stack of empties.
        boolean acted = BucketOperations.get().asBlockInteraction(level, () -> switch (BucketState.getMode(stack)) {
            case NONE -> HeldTransfers.fillFromHand(level, player, hand, stack,
                    bucket -> FluidTransactions.tryTakeSource(level, hit, bucket, player, hand));
            case FLUID -> player.isShiftKeyDown()
                    ? FluidTransactions.classifySourceTarget(level, hit, stack)
                            == FluidTransactions.SourceTarget.MATCHING_FLUID
                            && FluidTransactions.tryTakeSource(level, hit, stack, player, hand)
                    : FluidTransactions.tryPlaceSource(level, hit, stack, player, hand);
            default -> false;
        });
        return acted ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    /**
     * Drives the Source Bucket gesture. A held-container transfer takes priority, then a sneak-use
     * against air resets any assignment, fluid or milk, to empty. An assigned bucket places its
     * fluid on a normal targeted use, removes one matching source unit on a sneak-targeted use, or
     * drinks assigned milk. An unassigned bucket assigns itself from an allowed targeted source. The
     * assignment never changes on a take or place. Cauldrons and fluid stores are left to
     * {@link #useOn}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult targetHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (FluidBucketItem.tryCrossHandTransfer(level, player, hand, stack, targetHit)) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }

        if (FluidBucketItem.tryShiftClear(level, player, stack, targetHit)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode == BucketState.Mode.MILK) return startMilkDrink(player, hand, stack);

        if (mode == BucketState.Mode.NONE) {
            BlockHitResult takeHit = FluidBucketItem.withoutBlockTarget(level,
                    getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY), true);
            if (takeHit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);

            InteractionResult claimed = FluidBucketItem.beforeWorldBucketUse(
                    player, level, stack, () -> takeHit);
            if (claimed != null) return new InteractionResultHolder<>(claimed, stack);
            if (HeldTransfers.fillFromHand(level, player, hand, stack,
                    bucket -> FluidTransactions.tryTakeSource(level, takeHit, bucket, player, hand))) {
                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
            }
            return InteractionResultHolder.pass(stack);
        }

        if (mode == BucketState.Mode.FLUID) {
            if (player.isShiftKeyDown()) {
                BlockHitResult takeHit = FluidBucketItem.withoutBlockTarget(level, targetHit, true);
                if (takeHit.getType() != HitResult.Type.BLOCK
                        || FluidTransactions.classifySourceTarget(level, takeHit, stack)
                        != FluidTransactions.SourceTarget.MATCHING_FLUID) {
                    return InteractionResultHolder.pass(stack);
                }
                InteractionResult claimed = FluidBucketItem.beforeWorldBucketUse(
                        player, level, stack, () -> takeHit);
                if (claimed != null) return new InteractionResultHolder<>(claimed, stack);
                if (FluidTransactions.tryTakeSource(level, takeHit, stack, player, hand)) {
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
                return InteractionResultHolder.pass(stack);
            }

            BlockHitResult placeHit = FluidBucketItem.withoutBlockTarget(level,
                    getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE), true);
            if (placeHit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
            InteractionResult claimed = FluidBucketItem.beforeWorldBucketUse(player, level, stack,
                    () -> FluidBucketItem.worldPlaceHit(level, player, hand, stack, placeHit));
            if (claimed != null) return new InteractionResultHolder<>(claimed, stack);
            if (FluidTransactions.tryPlaceSource(level, placeHit, stack, player, hand)) {
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    /* ------------------------- Milk ------------------------- */

    /** An unassigned bucket is assigned to milk from a cow when the allowlist permits milk. */
    @Override
    public boolean canMilkInto(ItemStack stack) {
        return BucketState.getMode(stack) == BucketState.Mode.NONE && SBPolicy.allowsMilk();
    }

    /** Assigned milk is drinkable while the allowlist permits milk. */
    @Override
    protected boolean holdsDrinkableMilk(ItemStack stack) {
        return BucketState.getMode(stack) == BucketState.Mode.MILK && SBPolicy.allowsMilk();
    }

    @Override
    protected boolean drainsOnDrink() {
        return false;
    }

    /**
     * Returns a 1-count copy with its assignment intact, since a Source Bucket is an infinite
     * source; {@link ItemStack#EMPTY} for an unassigned bucket.
     */
    @Override
    public ItemStack getUnitRemainder(ItemStack stack) {
        if (BucketState.isEmptyBucket(stack)) return ItemStack.EMPTY;
        return stack.copyWithCount(1);
    }
}
