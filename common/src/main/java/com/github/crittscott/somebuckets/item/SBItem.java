package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
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
        boolean acted = switch (BucketState.getMode(stack)) {
            case NONE -> HeldTransfers.fillFromHand(level, player, hand, stack,
                    bucket -> FluidTransactions.tryTakeSource(level, hit, bucket, player, hand));
            case FLUID -> player.isShiftKeyDown()
                    ? FluidTransactions.classifySourceTarget(level, hit, stack)
                            == FluidTransactions.SourceTarget.MATCHING_FLUID
                            && FluidTransactions.tryTakeSource(level, hit, stack, player, hand)
                    : FluidTransactions.tryPlaceSource(level, hit, stack, player, hand);
            default -> false;
        };
        return acted ? success(level) : InteractionResult.PASS;
    }

    /**
     * Drives the Source Bucket gesture. A held-container transfer takes priority; then an assigned
     * bucket places its fluid on a normal targeted use, removes one matching source unit on a
     * sneak-targeted use, drinks assigned milk, or resets to empty on a sneak-use against air. An
     * unassigned bucket assigns itself from an allowed targeted source. The assignment never changes
     * on a take or place. Cauldrons and fluid stores are left to {@link #useOn}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult targetHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (FluidBucketItem.tryCrossHandTransfer(level, player, hand, stack, targetHit)) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }

        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode == BucketState.Mode.FLUID && FluidBucketItem.tryShiftClear(level, player, stack, targetHit)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (mode == BucketState.Mode.MILK) {
            if (!SBPolicy.allowsMilk()) return InteractionResultHolder.pass(stack);
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

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
                    () -> FluidBucketItem.withPos(placeHit,
                            FluidTransactions.resolveSourcePlaceTarget(level, placeHit, stack, player, hand, true)));
            if (claimed != null) return new InteractionResultHolder<>(claimed, stack);
            if (FluidTransactions.tryPlaceSource(level, placeHit, stack, player, hand)) {
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }

        return InteractionResultHolder.pass(stack);
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
    public UseAnim getUseAnimation(ItemStack stack) {
        return holdsDrinkableMilk(stack) ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return holdsDrinkableMilk(stack) ? DRINK_DURATION_TICKS : 0;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (holdsDrinkableMilk(stack)) finishMilkDrink(stack, level, user, false);
        return stack;
    }

    private static boolean holdsDrinkableMilk(ItemStack stack) {
        return BucketState.getMode(stack) == BucketState.Mode.MILK && SBPolicy.allowsMilk();
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
