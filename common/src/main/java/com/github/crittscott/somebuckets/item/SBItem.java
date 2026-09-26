package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.interaction.MilkTransfers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.LegacyBucketMigration;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Infinite source and sink assigned to one server-allowed fluid or to allowed milk. Stacks like a
 * vanilla bucket: up to {@value VariableStackItem#EMPTY_STACK_SIZE} while unassigned, one once
 * assigned. The allowlist is enforced at assignment and every later input or output boundary;
 * disallowed existing assignments retain their state but remain inert until reset.
 * Dynamic names append a content suffix to the registered description ID, and the model uses
 * {@link FluidBucketItem#CONTENT_PROPERTY} for the shared content-state protocol.
 */
public class SBItem extends Item implements FluidBucketItem, VariableStackItem {

    /**
     * Creates a Source Bucket.
     *
     * @param props base item properties
     */
    public SBItem(Properties props) {
        super(props.stacksTo(EMPTY_STACK_SIZE).rarity(Rarity.RARE));
    }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return BucketState.isEmptyBucket(stack);
    }

    /** Migrates any recognized custom-data payload on the server while the stack is carried. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide) {
            if (!BucketState.discardInvalidState(stack)) return;
            LegacyBucketMigration.migrate(stack, (ServerLevel) level,
                    () -> entity.getScoreboardName() + " at " + entity.blockPosition()
                            + " in " + level.dimension().location());
        }
    }

    /**
     * Drives the Source Bucket gesture. A held-container transfer takes priority; then an assigned
     * bucket places its fluid on a normal targeted use, removes one matching source unit on a
     * sneak-targeted use, drinks assigned milk, or resets to empty on a sneak-use against air. An
     * unassigned bucket assigns itself from an allowed targeted source. The assignment never changes
     * on a take or place.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && !BucketState.discardInvalidState(stack)) return InteractionResult.PASS;

        BlockHitResult targetHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (FluidBucketItem.tryCrossHandTransfer(level, player, hand, stack, targetHit)) {
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }

        BucketState.Mode mode = BucketState.getMode(stack);
        // Sneak-use against air resets any assignment, milk included; tryShiftClear no-ops for an
        // unassigned bucket and for a non-sneak or block-targeted use, so a normal milk drink falls
        // through to the branch below.
        if (FluidBucketItem.tryShiftClear(level, player, stack, targetHit)) {
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        if (mode == BucketState.Mode.MILK) {
            if (!SBPolicy.allowsMilk()) return InteractionResult.PASS;
            player.startUsingItem(hand);
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }

        if (mode == BucketState.Mode.NONE) {
            BlockHitResult takeHit = getPlayerPOVHitResult(
                    level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (takeHit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;

            if (BucketOperations.get().firesWorldBucketEvent()
                    && !BucketOperations.get().hasBlockStorage(
                            level, takeHit.getBlockPos(), takeHit.getDirection())) {
                InteractionResult claimed = BucketOperations.get()
                        .beforeWorldBucketUse(player, level, stack, takeHit);
                if (claimed != null) return claimed;
            }
            if (SBFluidLogic.tryTake(level, takeHit, stack, player, hand)) {
                return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
            }
            return InteractionResult.PASS;
        }

        if (mode == BucketState.Mode.FLUID) {
            if (player.isShiftKeyDown()) {
                if (targetHit.getType() != HitResult.Type.BLOCK
                        || SBFluidLogic.classifyTarget(level, targetHit, stack)
                        != BucketOperations.SourceTarget.MATCHING_FLUID) {
                    return InteractionResult.PASS;
                }
                if (BucketOperations.get().firesWorldBucketEvent()
                        && !BucketOperations.get().hasBlockStorage(
                                level, targetHit.getBlockPos(), targetHit.getDirection())) {
                    InteractionResult claimed = BucketOperations.get()
                            .beforeWorldBucketUse(player, level, stack, targetHit);
                    if (claimed != null) return claimed;
                }
                if (SBFluidLogic.tryTake(level, targetHit, stack, player, hand)) {
                    return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
                }
                return InteractionResult.PASS;
            }

            BlockHitResult placeHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (placeHit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
            if (BucketOperations.get().firesWorldBucketEvent()
                    && !BucketOperations.get().hasBlockStorage(
                            level, placeHit.getBlockPos(), placeHit.getDirection())) {
                BlockHitResult eventHit = FluidBucketItem.withPos(placeHit,
                        SBFluidLogic.resolvePlaceTarget(
                                level, placeHit, stack, player, hand, true));
                InteractionResult claimed = BucketOperations.get()
                        .beforeWorldBucketUse(player, level, stack, eventHit);
                if (claimed != null) return claimed;
            }
            if (SBFluidLogic.tryPlace(level, placeHit, stack, player, hand)) {
                return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * Milks an adult cow with an unassigned bucket and assigns milk mode, when the allowlist permits
     * milk. Milking is routed through the cow's own interaction; the client predicts the vanilla
     * feedback and the server records the assignment only after an authorized interaction consumes
     * the action.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!player.level().isClientSide && !BucketState.discardInvalidState(stack)) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof Cow cow) || cow.isBaby()) return InteractionResult.PASS;
        if (BucketState.getMode(stack) != BucketState.Mode.NONE) return InteractionResult.PASS;
        if (!SBPolicy.allowsMilk()) return InteractionResult.PASS;

        Level level = player.level();
        if (level.isClientSide) {
            // Predict vanilla's client-side milking feedback without touching the bucket.
            MilkTransfers.milkCow(cow, player, hand);
            return InteractionResult.SUCCESS;
        }
        if (!Protections.mayAct(level, ProtectionContext.player(player, hand),
                ProtectionAction.ENTITY_INTERACT, cow.blockPosition(), Direction.UP,
                stack, cow)) return InteractionResult.PASS;

        if (!MilkTransfers.milkCow(cow, player, hand)) return InteractionResult.PASS;

        BucketState.setMilkAmount(stack, BUCKET_VOLUME_MB);
        player.setItemInHand(hand, stack);
        player.getInventory().setChanged();
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return BucketState.getMode(stack) == BucketState.Mode.MILK && SBPolicy.allowsMilk()
                ? ItemUseAnimation.DRINK : ItemUseAnimation.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return BucketState.getMode(stack) == BucketState.Mode.MILK && SBPolicy.allowsMilk()
                ? DRINK_DURATION_TICKS : 0;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (BucketState.getMode(stack) == BucketState.Mode.MILK && SBPolicy.allowsMilk()) {
            FluidBucketItem.finishMilkDrink(stack, level, user, this, false);
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        BucketState.Mode mode = BucketState.getMode(stack);
        String baseKey = getDescriptionId();

        if (mode == BucketState.Mode.FLUID) {
            return FluidBucketItem.resolveFluidName(baseKey, BucketState.getStoredFluid(stack));
        } else if (mode == BucketState.Mode.MILK) {
            return Component.translatable(baseKey + NAME_SUFFIX_MILK);
        }

        return Component.translatable(baseKey);
    }

    /**
     * Returns the crafting leftover for one use of this bucket as an ingredient. Loader item shells
     * expose this through {@code getCraftingRemainingItem}.
     *
     * @param stack the bucket stack consumed by the recipe
     * @return a 1-count copy with its assignment intact, since a Source Bucket is an infinite source;
     *         {@link ItemStack#EMPTY} for an unassigned bucket
     */
    public ItemStack getUnitRemainder(ItemStack stack) {
        if (BucketState.isEmptyBucket(stack)) return ItemStack.EMPTY;
        ItemStack result = stack.copy();
        result.setCount(1);
        return result;
    }
}
