package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.client.SidedFluidColors;
import com.github.crittscott.somebuckets.fluid.BBFluidHandler;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.FluidProvider;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.advancements.CriteriaTriggers;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Finite, unstackable, single-content container shared by the Big and Huge Bucket tiers.
 * Capacity is expressed in whole bucket units, while Forge fluid capability transfers retain mB
 * precision; fluid, milk, and powder-snow modes remain mutually exclusive.
 * Dynamic names append a content suffix to the registered description ID, and
 * {@link FluidBucketItem#CONTENT_PROPERTY} exposes the shared item-model state protocol.
 */
public class BBItem extends Item implements FluidBucketItem {
    private static final int DEFAULT_FLUID_BAR_COLOR = 0x4A90E2;
    private static final int EMPTY_BAR_COLOR = 0xAAAAAA;
    private static final int MILK_BAR_COLOR = 0xFFFFFF;
    private static final int POWDER_SNOW_BAR_COLOR = 0xE0F8FF;

    private final int capacityUnits; // tier: 8 or 64

    public BBItem(Properties properties, int capacityUnits) {
        super(properties.stacksTo(1));
        this.capacityUnits = capacityUnits;
    }

    public int getCapacityUnits() { return capacityUnits; }
    public int getCapacityMb()    { return capacityUnits * FluidType.BUCKET_VOLUME; }

    /* ------------------------- Tooltip and naming ------------------------- */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int capUnits = getCapacityUnits();

        switch (mode) {
            case FLUID, MILK -> {
                int current = NBTUtil.getAmount(stack) / FluidType.BUCKET_VOLUME;
                tooltip.add(Component.translatable(
                        "tooltip.somebuckets.big_bucket.fluid", current, capUnits));
            }
            case POWDER_SNOW -> {
                int current = NBTUtil.getPowderUnits(stack);
                tooltip.add(Component.translatable(
                        "tooltip.somebuckets.big_bucket.powder_snow", current, capUnits));
            }
        }
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
        } else if (mode == NBTUtil.Mode.POWDER_SNOW) {
            return Component.translatable(baseKey + ".powder_snow");
        }

        return Component.translatable(baseKey);
    }

    /* ------------------------- UI bar ------------------------- */

    @Override public boolean isBarVisible(ItemStack stack) { return NBTUtil.getMode(stack) != NBTUtil.Mode.NONE; }

    @Override
    public int getBarWidth(ItemStack stack) {
        int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode == NBTUtil.Mode.FLUID || mode == NBTUtil.Mode.MILK) {
            return Math.round(ItemBars.ITEM_BAR_WIDTH * (float) NBTUtil.getAmount(stack)
                    / (float) (capUnits * FluidType.BUCKET_VOLUME));
        } else if (mode == NBTUtil.Mode.POWDER_SNOW) {
            return Math.round(ItemBars.ITEM_BAR_WIDTH * (float) NBTUtil.getPowderUnits(stack) / (float)capUnits);
        }
        return 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        switch (mode) {
            case FLUID -> {
                FluidStack fluidStack = NBTUtil.getFluidStack(stack);
                if (!fluidStack.isEmpty()) {
                    return SidedFluidColors.getColorRgb(fluidStack, DEFAULT_FLUID_BAR_COLOR);
                }
                return EMPTY_BAR_COLOR;
            }
            case MILK -> {
                return MILK_BAR_COLOR;
            }
            case POWDER_SNOW -> {
                return POWDER_SNOW_BAR_COLOR;
            }
            default -> {
                return EMPTY_BAR_COLOR;
            }
        }
    }


    /* ------------------------- Fluid capability ------------------------- */

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        // Provider always exposes fluid handler - behavior gated by mode
        return new FluidProvider(() -> new BBFluidHandler(stack));
    }

    /* ------------------------- Use (right-click) ------------------------- */

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

        // normalize zero-content modes to "none" before branching
        NBTUtil.normalizeEmptyState(stack);

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int capMb = ((BBItem) stack.getItem()).getCapacityMb();

        // Drinking milk
        if (mode == NBTUtil.Mode.MILK) {
            if (NBTUtil.getAmount(stack) >= FluidType.BUCKET_VOLUME) {
                player.startUsingItem(hand); return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.pass(stack);
        }

        // Two raytraces: SOURCE_ONLY for taking, NONE for placing (vanilla parity)
        BlockHitResult takeHit  = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        BlockHitResult placeHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);

        // Announce fluid operations and powder pickup at the position this call would actually act
        // on. Powder output uses the native block-place event instead. Target resolution mirrors the
        // dispatch below so the selected event and mutation position cannot disagree.
        BlockHitResult eventHit = resolveEventHit(level, player, hand, stack, mode, capMb, takeHit, placeHit);
        boolean powderPickup = mode == NBTUtil.Mode.POWDER_SNOW
                && takeHit.getType() == HitResult.Type.BLOCK
                && BBFluidLogic.canAttemptTakePowderAt(level, takeHit, stack);
        if (eventHit != null && eventHit.getType() == HitResult.Type.BLOCK
                && (mode != NBTUtil.Mode.POWDER_SNOW || powderPickup)) {
            InteractionResultHolder<ItemStack> claimed = Protections.onBucketUse(player, level, stack, eventHit);
            if (claimed != null) return claimed;
        }

        switch (mode) {
            case POWDER_SNOW:
                if (takeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.getInstance().tryTakePowder(level, takeHit, stack, player, hand))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                if (placeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.getInstance().tryPlacePowder(level, placeHit, stack, player, hand))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                break;

            case FLUID: {
                FluidStack current = NBTUtil.getFluidStack(stack);
                int amt = current.getAmount();

                if (amt == 0) {
                    if (takeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.getInstance().tryTake(level, takeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else if (amt >= capMb) {
                    if (placeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.getInstance().tryPlace(level, placeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else {
                    // Partial: try take, else place (bucket intuition)
                    if (takeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.getInstance().tryTake(level, takeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                    if (placeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.getInstance().tryPlace(level, placeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
                break;
            }

            default: // Empty or unsupported content
                if (takeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.getInstance().tryTake(level, takeHit, stack, player, hand))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                if (takeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.getInstance().tryTakePowder(level, takeHit, stack, player, hand))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                break;
        }
        return InteractionResultHolder.pass(stack);
    }

    /**
     * The block position {@code use}'s dispatch below would actually act on, mirroring its own
     * take-then-place branching. World-fluid operations and powder pickup post
     * {@code FillBucketEvent} against the result; capability transfers return null, and powder
     * output uses its native block-place event instead.
     */
    @Nullable
    private static BlockHitResult resolveEventHit(Level level, Player player, InteractionHand hand,
                                                   ItemStack stack, NBTUtil.Mode mode, int capMb,
                                                   BlockHitResult takeHit, BlockHitResult placeHit) {
        if (mode == NBTUtil.Mode.POWDER_SNOW) {
            if (takeHit.getType() == HitResult.Type.BLOCK
                    && BBFluidLogic.canAttemptTakePowderAt(level, takeHit, stack)) {
                return takeHit;
            }
            if (placeHit.getType() != HitResult.Type.BLOCK) return placeHit;
            return FluidBucketItem.withPos(placeHit,
                    BBFluidLogic.resolvePowderPlaceTarget(level, placeHit, player, hand, true));
        }

        if (mode == NBTUtil.Mode.FLUID) {
            FluidStack current = NBTUtil.getFluidStack(stack);
            int amt = current.getAmount();

            if (amt == 0) {
                return takeHit.getType() == HitResult.Type.BLOCK
                        && Transfers.hasBlockHandler(level, takeHit.getBlockPos(), takeHit.getDirection())
                        ? null : takeHit;
            }

            if (amt < capMb && takeHit.getType() == HitResult.Type.BLOCK) {
                if (Transfers.hasBlockHandler(level, takeHit.getBlockPos(), takeHit.getDirection())) {
                    return null;
                }
                if (BBFluidLogic.canAttemptTakeAt(level, takeHit, stack)) return takeHit;
            }
            if (placeHit.getType() != HitResult.Type.BLOCK) return placeHit;
            if (Transfers.hasBlockHandler(level, placeHit.getBlockPos(), placeHit.getDirection())) {
                return null;
            }
            return FluidBucketItem.withPos(placeHit, BBFluidLogic.resolvePlaceTarget(level, placeHit, stack, true));
        }

        if (takeHit.getType() == HitResult.Type.BLOCK
                && Transfers.hasBlockHandler(level, takeHit.getBlockPos(), takeHit.getDirection())) {
            return null;
        }
        return takeHit; // Empty or unsupported content: take is the only possible action.
    }

    @Override public int getUseDuration(ItemStack stack) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK
                && NBTUtil.getAmount(stack) >= FluidType.BUCKET_VOLUME ? DRINK_DURATION_TICKS : 0;
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK
                && NBTUtil.getAmount(stack) >= FluidType.BUCKET_VOLUME ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (NBTUtil.getMode(stack) == NBTUtil.Mode.MILK
                && NBTUtil.getAmount(stack) >= FluidType.BUCKET_VOLUME
                && living instanceof Player player) {
            // Trigger and award against the still-milk-filled stack before mutating it, matching
            // vanilla MilkBucketItem's ordering.
            if (player instanceof ServerPlayer sp) {
                CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!level.isClientSide) {
                player.removeAllEffects();
                NBTUtil.drainFiniteContent(stack, FluidType.BUCKET_VOLUME);
            }
        }
        return stack;
    }

    /* ------------------------- Interact with entities ------------------------- */

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        Level level = player.level();
        int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();

        // Milking adds one bucket volume, up to capacity.
        if (target instanceof Cow cow && !cow.isBaby()) {
            boolean canMilk = NBTUtil.getMode(stack) == NBTUtil.Mode.NONE ||
                    (NBTUtil.getMode(stack) == NBTUtil.Mode.MILK
                            && NBTUtil.getAmount(stack) < capUnits * FluidType.BUCKET_VOLUME);
            if (!canMilk) return InteractionResult.PASS;

            if (level.isClientSide) return InteractionResult.sidedSuccess(true);
            if (!Protections.mayAct(level, ProtectionContext.player(player, hand),
                    ProtectionAction.ENTITY_INTERACT, cow.blockPosition(), net.minecraft.core.Direction.UP,
                    stack, cow)) return InteractionResult.PASS;

            if (NBTUtil.getMode(stack) == NBTUtil.Mode.NONE) {
                NBTUtil.setMilkAmount(stack, FluidType.BUCKET_VOLUME);
            } else {
                NBTUtil.setMilkAmount(stack, Math.min(capUnits * FluidType.BUCKET_VOLUME,
                        NBTUtil.getAmount(stack) + FluidType.BUCKET_VOLUME));
            }

            level.playSound(null, player.blockPosition(), SoundEvents.COW_MILK, SoundSource.PLAYERS,
                    1.0F, 1.0F);
            player.setItemInHand(hand, stack);
            player.getInventory().setChanged();
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResult.sidedSuccess(false);
        }

        return InteractionResult.PASS;
    }

    /* ------------------------- Crafting remainder ------------------------- */

    @Override public boolean hasCraftingRemainingItem(ItemStack stack) { return !NBTUtil.isEmptyBucket(stack); }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        if (NBTUtil.isEmptyBucket(stack)) return ItemStack.EMPTY;

        ItemStack result = stack.copy();
        result.setCount(1);
        switch (NBTUtil.getMode(result)) {
            case FLUID, MILK -> NBTUtil.drainFiniteContent(result, FluidType.BUCKET_VOLUME);
            case POWDER_SNOW -> {
                int units = NBTUtil.getPowderUnits(result);
                if (units > 0) NBTUtil.setPowderUnits(result, units - 1);
            }
            default -> NBTUtil.clearBucket(result);
        }
        NBTUtil.normalizeEmptyState(result);
        return result;
    }
}
