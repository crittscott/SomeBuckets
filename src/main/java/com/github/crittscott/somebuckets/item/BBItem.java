package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.client.FluidColorHelper;
import com.github.crittscott.somebuckets.fluid.BBFluidHandler;
import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.fluid.FluidProvider;
import com.github.crittscott.somebuckets.interaction.Transfers;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;

public class BBItem extends Item {

    private final int capacityUnits; // tier: 8 or 64

    public BBItem(Properties properties, int capacityUnits) {
        super(properties.stacksTo(1));
        this.capacityUnits = capacityUnits;
    }

    public int getCapacityUnits() { return capacityUnits; }
    public int getCapacityMb()    { return capacityUnits * 1000; }

    /* ------------------------- Content property for model switching ------------------------- */

    /** Single float used by the item predicate to select non-fluid content models. */
    public static float getContentProperty(ItemStack stack) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        switch (mode) {
            case FLUID -> {
                return NBTUtil.getFluidStack(stack).isEmpty() ? 0.0f : 0.1f;
            }
            case MILK -> {
                return 0.2f;
            }
            case POWDER_SNOW -> {
                return 0.3f;
            }
        }
        return 0.0f; // empty
    }

    /* ------------------------- Tooltip and naming ------------------------- */

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int capUnits = getCapacityUnits();

        switch (mode) {
            case FLUID, MILK -> {
                int current = NBTUtil.getAmount(stack) / 1000;
                tooltip.add(Component.literal(current + "/" + capUnits + " buckets"));
            }
            case POWDER_SNOW -> {
                int current = NBTUtil.getPowderUnits(stack);
                tooltip.add(Component.literal(current + "/" + capUnits + " blocks"));
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        String bucketType = (getCapacityUnits() == 8) ? "big_bucket_8" : "big_bucket_64";

        if (mode == NBTUtil.Mode.FLUID) {
            FluidStack fluidStack = NBTUtil.getFluidStack(stack);
            if (!fluidStack.isEmpty()) {
                if (fluidStack.getFluid() == Fluids.WATER) {
                    return Component.translatable("item.somebuckets." + bucketType + ".water");
                } else if (fluidStack.getFluid() == Fluids.LAVA) {
                    return Component.translatable("item.somebuckets." + bucketType + ".lava");
                } else {
                    Component fluidName = fluidStack.getDisplayName();
                    return Component.translatable("item.somebuckets." + bucketType + ".fluid", fluidName);
                }
            }
        } else if (mode == NBTUtil.Mode.MILK) {
            return Component.translatable("item.somebuckets." + bucketType + ".milk");
        } else if (mode == NBTUtil.Mode.POWDER_SNOW) {
            return Component.translatable("item.somebuckets." + bucketType + ".powder_snow");
        }

        return Component.translatable("item.somebuckets." + bucketType);
    }

    /* ------------------------- UI bar ------------------------- */

    @Override public boolean isBarVisible(ItemStack stack) { return NBTUtil.getMode(stack) != NBTUtil.Mode.NONE; }

    @Override
    public int getBarWidth(ItemStack stack) {
        int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode == NBTUtil.Mode.FLUID || mode == NBTUtil.Mode.MILK) {
            return Math.round(13.0f * (float) NBTUtil.getAmount(stack) / (float)(capUnits * 1000));
        } else if (mode == NBTUtil.Mode.POWDER_SNOW) {
            return Math.round(13.0f * (float) NBTUtil.getPowderUnits(stack) / (float)capUnits);
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
                    return FluidColorHelper.getColorRgb(fluidStack, 0x4A90E2);
                }
                return 0xAAAAAA;
            }
            case MILK -> {
                return 0xFFFFFF;
            }
            case POWDER_SNOW -> {
                return 0xE0F8FF;
            }
            default -> {
                return 0xAAAAAA;
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

        // Shift-RC on air → empty
        if (player.isShiftKeyDown()) {
            HitResult hr = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (hr == null || hr.getType() == HitResult.Type.MISS) {
                NBTUtil.Mode mode = NBTUtil.getMode(stack);
                if (mode != NBTUtil.Mode.NONE) {
                    if (!level.isClientSide) {
                        NBTUtil.clearBucket(stack);
                    }
                    level.playSound(player, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS,
                            1.0f, 1.0f);
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
            }
        }

        // Cross-bucket transfer, deliberately restricted to right-clicking air: a targeted block
        // means the player expects the bucket to act on that block instead.
        HitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            ItemStack offHandStack = player.getOffhandItem();
            if (!offHandStack.isEmpty()) {
                if (Transfers.tryTransferEither(level, player, hand, stack, InteractionHand.OFF_HAND, offHandStack)) {
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
            }
        }

        // normalize zero-content modes to "none" before branching
        NBTUtil.normalizeEmptyState(stack);

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int capMb = ((BBItem) stack.getItem()).getCapacityMb();

        // Drinking milk
        if (mode == NBTUtil.Mode.MILK) {
            if (NBTUtil.getAmount(stack) >= 1000) {
                player.startUsingItem(hand); return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.pass(stack);
        }

        // Two raytraces: SOURCE_ONLY for taking, NONE for placing (vanilla parity)
        BlockHitResult takeHit  = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        BlockHitResult placeHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);

        // Announce the bucket use on the target this call would act on, so protection and automation
        // mods can veto it. Only a full bucket goes straight to placing.
        boolean placeOnly = mode == NBTUtil.Mode.FLUID && NBTUtil.getFluidStack(stack).getAmount() >= capMb;
        BlockHitResult eventHit = placeOnly ? placeHit : takeHit;
        if (eventHit.getType() == HitResult.Type.BLOCK) {
            InteractionResultHolder<ItemStack> claimed = Protections.onBucketUse(player, level, stack, eventHit);
            if (claimed != null) return claimed;
        }

        switch (mode) {
            case POWDER_SNOW:
                if (takeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.getInstance().tryTakePowder(level, takeHit, stack, player))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                if (placeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.getInstance().tryPlacePowder(level, placeHit, stack, player))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                break;

            case FLUID: {
                FluidStack current = NBTUtil.getFluidStack(stack);
                int amt = current.getAmount();

                if (amt == 0) {
                    if (takeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.getInstance().tryTake(level, takeHit, stack, player))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else if (amt >= capMb) {
                    if (placeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.getInstance().tryPlace(level, placeHit, stack, player))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else {
                    // Partial: try take, else place (bucket intuition)
                    if (takeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.getInstance().tryTake(level, takeHit, stack, player))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                    if (placeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.getInstance().tryPlace(level, placeHit, stack, player))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
                break;
            }

            default: // Empty or unsupported content
                if (takeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.getInstance().tryTake(level, takeHit, stack, player))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                if (takeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.getInstance().tryTakePowder(level, takeHit, stack, player))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                break;
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override public int getUseDuration(ItemStack stack) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK && NBTUtil.getAmount(stack) >= 1000 ? 32 : 0;
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK && NBTUtil.getAmount(stack) >= 1000 ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (NBTUtil.getMode(stack) == NBTUtil.Mode.MILK && NBTUtil.getAmount(stack) >= 1000 && living instanceof Player player) {
            if (!level.isClientSide) player.removeAllEffects();
            NBTUtil.setMilkAmount(stack, NBTUtil.getAmount(stack) - 1000);
            // normalize immediately after consuming the last unit
            NBTUtil.normalizeEmptyState(stack);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return stack;
    }

    /* ------------------------- Interact with entities ------------------------- */

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        Level level = player.level();
        int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();

        // Milking (1 unit = 1000 mB; up to capacity)
        if (target instanceof Cow cow && !cow.isBaby()) {
            boolean canMilk = NBTUtil.getMode(stack) == NBTUtil.Mode.NONE ||
                    (NBTUtil.getMode(stack) == NBTUtil.Mode.MILK && NBTUtil.getAmount(stack) < capUnits * 1000);
            if (!canMilk) return InteractionResult.PASS;

            if (level.isClientSide) return InteractionResult.sidedSuccess(true);
            if (!Protections.mayAct(level, ProtectionContext.player(player, hand),
                    ProtectionAction.ENTITY_INTERACT, cow.blockPosition(), net.minecraft.core.Direction.UP,
                    stack, cow)) return InteractionResult.PASS;

            if (NBTUtil.getMode(stack) == NBTUtil.Mode.NONE) NBTUtil.setMilkAmount(stack, 1000);
            else NBTUtil.setMilkAmount(stack, Math.min(capUnits * 1000, NBTUtil.getAmount(stack) + 1000));

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
        return NBTUtil.getCraftingRemainder(stack);
    }
}
