package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.protection.ForgeBucketEvents;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.fluid.FluidProvider;
import com.github.crittscott.somebuckets.fluid.SBFluidHandler;
import com.github.crittscott.somebuckets.fluid.SBFluidLogic;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import javax.annotation.Nullable;

/**
 * Unstackable infinite source and sink assigned to one server-allowed fluid or to allowed milk.
 * The allowlist is enforced at assignment and every later input or output boundary; disallowed
 * existing assignments retain their state but remain inert until reset.
 * Dynamic names append a content suffix to the registered description ID, and the model uses
 * {@link FluidBucketItem#CONTENT_PROPERTY} for the shared content-state protocol.
 */
public class SBItem extends Item implements FluidBucketItem {

    public SBItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidProvider(() -> new SBFluidHandler(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        HitResult airHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (FluidBucketItem.tryShiftClear(level, player, stack, airHit)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (FluidBucketItem.tryCrossHandTransfer(level, player, hand, stack, airHit)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode == NBTUtil.Mode.MILK) {
            if (!SBPolicy.allowsMilk()) return InteractionResultHolder.pass(stack);
            player.startUsingItem(hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // Ray trace blocks; SB picks/places any fluid
        HitResult result = getPlayerPOVHitResult(level, player,
                mode == NBTUtil.Mode.NONE ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) result;

            // Capability transactions use their own exact-position authorization and deliberately do
            // not post FillBucketEvent. World/cauldron use still announces the resolved mutation.
            if (!Transfers.hasBlockHandler(level, bhr.getBlockPos(), bhr.getDirection())) {
                BlockHitResult eventHit = mode == NBTUtil.Mode.FLUID
                        ? FluidBucketItem.withPos(bhr, SBFluidLogic.resolvePlaceTarget(level, bhr, stack, true))
                        : bhr;
                InteractionResultHolder<ItemStack> claimed = ForgeBucketEvents.onBucketUse(player, level, stack, eventHit);
                if (claimed != null) return claimed;
            }

            if (mode == NBTUtil.Mode.NONE) {
                if (SBFluidLogic.getInstance().tryTake(level, bhr, stack, player, hand)) {
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
            } else if (mode == NBTUtil.Mode.FLUID) {
                if (SBFluidLogic.getInstance().tryPlace(level, bhr, stack, player, hand)) {
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
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
                ProtectionAction.ENTITY_INTERACT, cow.blockPosition(), net.minecraft.core.Direction.UP,
                stack, cow)) return InteractionResult.PASS;

        NBTUtil.setMilkAmount(stack, FluidType.BUCKET_VOLUME);
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
    public int getUseDuration(ItemStack stack) {
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
            FluidStack fluidStack = NBTUtil.getFluidStack(stack);
            if (!fluidStack.isEmpty()) {
                return FluidBucketItem.resolveFluidName(baseKey, fluidStack);
            }
        } else if (mode == NBTUtil.Mode.MILK) {
            return Component.translatable(baseKey + ".milk");
        }

        return Component.translatable(baseKey);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return !NBTUtil.isEmptyBucket(stack);
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        // Infinite source: an assigned bucket comes back with its assignment intact.
        if (NBTUtil.isEmptyBucket(stack)) return ItemStack.EMPTY;
        ItemStack result = stack.copy();
        result.setCount(1);
        return result;
    }
}
