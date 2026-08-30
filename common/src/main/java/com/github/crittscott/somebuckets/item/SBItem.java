package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/**
 * Infinite source and sink assigned to one server-allowed fluid or to allowed milk. Stacks like a
 * vanilla bucket: up to {@value VariableStackItem#EMPTY_STACK_SIZE} while unassigned, one once
 * assigned. The allowlist is enforced at assignment and every later input or output boundary;
 * disallowed existing assignments retain their state but remain inert until reset.
 * Dynamic names append a content suffix to the registered description ID, and the model uses
 * {@link FluidBucketItem#CONTENT_PROPERTY} for the shared content-state protocol.
 */
public class SBItem extends Item implements FluidBucketItem, VariableStackItem {

    public SBItem(Properties props) {
        super(props.stacksTo(EMPTY_STACK_SIZE).rarity(Rarity.RARE));
    }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return NBTUtil.isEmptyBucket(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult targetHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (FluidBucketItem.tryCrossHandTransfer(level, player, hand, stack, targetHit)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode == NBTUtil.Mode.FLUID && player.isShiftKeyDown()
                && targetHit.getType() == HitResult.Type.MISS) {
            if (!level.isClientSide) {
                NBTUtil.clearBucket(stack);
                level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (mode == NBTUtil.Mode.MILK) {
            if (!SBPolicy.allowsMilk()) return InteractionResultHolder.pass(stack);
            player.startUsingItem(hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (mode == NBTUtil.Mode.NONE) {
            BlockHitResult takeHit = getPlayerPOVHitResult(
                    level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (takeHit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);

            if (!BucketOperations.get().hasBlockStorage(
                    level, takeHit.getBlockPos(), takeHit.getDirection())) {
                InteractionResultHolder<ItemStack> claimed = BucketOperations.get()
                        .beforeWorldBucketUse(player, level, stack, takeHit);
                if (claimed != null) return claimed;
            }
            if (BucketOperations.get().trySourceTake(level, takeHit, stack, player, hand)) {
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
            return InteractionResultHolder.pass(stack);
        }

        if (mode == NBTUtil.Mode.FLUID) {
            if (player.isShiftKeyDown()) {
                if (targetHit.getType() != HitResult.Type.BLOCK
                        || BucketOperations.get().classifySourceTarget(level, targetHit, stack)
                        != BucketOperations.SourceTarget.MATCHING_FLUID) {
                    return InteractionResultHolder.pass(stack);
                }
                if (!BucketOperations.get().hasBlockStorage(
                        level, targetHit.getBlockPos(), targetHit.getDirection())) {
                    InteractionResultHolder<ItemStack> claimed = BucketOperations.get()
                            .beforeWorldBucketUse(player, level, stack, targetHit);
                    if (claimed != null) return claimed;
                }
                if (BucketOperations.get().trySourceTake(level, targetHit, stack, player, hand)) {
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
                return InteractionResultHolder.pass(stack);
            }

            BlockHitResult placeHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (placeHit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(stack);
            if (!BucketOperations.get().hasBlockStorage(
                    level, placeHit.getBlockPos(), placeHit.getDirection())) {
                BlockHitResult eventHit = FluidBucketItem.withPos(placeHit,
                        BucketOperations.get().resolveSourcePlaceTarget(
                                level, placeHit, stack, player, hand, true));
                InteractionResultHolder<ItemStack> claimed = BucketOperations.get()
                        .beforeWorldBucketUse(player, level, stack, eventHit);
                if (claimed != null) return claimed;
            }
            if (BucketOperations.get().trySourcePlace(level, placeHit, stack, player, hand)) {
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Cow cow) || cow.isBaby()) return InteractionResult.PASS;
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) return InteractionResult.PASS;
        if (!SBPolicy.allowsMilk()) return InteractionResult.PASS;

        Level level = player.level();
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (!Protections.mayAct(level, ProtectionContext.player(player, hand),
                ProtectionAction.ENTITY_INTERACT, cow.blockPosition(), Direction.UP,
                stack, cow)) return InteractionResult.PASS;

        NBTUtil.setMilkAmount(stack, BUCKET_VOLUME_MB);
        level.playSound(null, player.blockPosition(), SoundEvents.COW_MILK, SoundSource.PLAYERS, 1.0F,
                1.0F);
        player.setItemInHand(hand, stack);
        player.getInventory().setChanged();
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK && SBPolicy.allowsMilk()
                ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK && SBPolicy.allowsMilk()
                ? DRINK_DURATION_TICKS : 0;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (NBTUtil.getMode(stack) == NBTUtil.Mode.MILK && SBPolicy.allowsMilk()) {
            if (!level.isClientSide) {
                user.removeAllEffects();
                if (user instanceof Player p) {
                    p.awardStat(Stats.ITEM_USED.get(this));
                }
                if (user instanceof ServerPlayer sp) {
                    CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
                }
            }
            level.playSound(user, new BlockPos(user.getBlockX(), user.getBlockY(), user.getBlockZ()),
                    SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        String baseKey = getDescriptionId();

        if (mode == NBTUtil.Mode.FLUID) {
            StoredFluid fluid = NBTUtil.getStoredFluid(stack);
            if (!fluid.isEmpty()) {
                return FluidBucketItem.resolveFluidName(baseKey, fluid);
            }
        } else if (mode == NBTUtil.Mode.MILK) {
            return Component.translatable(baseKey + NAME_SUFFIX_MILK);
        }

        return Component.translatable(baseKey);
    }

    /**
     * Returns the crafting leftover for one use of this bucket as an ingredient. Because a Source
     * Bucket is an infinite source, an assigned bucket comes back as a 1-count copy with its
     * assignment intact; an unassigned (empty) bucket yields {@link ItemStack#EMPTY}. Loader item
     * shells expose this through {@code getCraftingRemainingItem}.
     */
    public ItemStack getUnitRemainder(ItemStack stack) {
        if (NBTUtil.isEmptyBucket(stack)) return ItemStack.EMPTY;
        ItemStack result = stack.copy();
        result.setCount(1);
        return result;
    }
}
