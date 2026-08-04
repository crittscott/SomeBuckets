package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class SBItem extends Item {

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

        // Shift-RC on air → clear the assignment. A targeted block means the player expects the
        // bucket to act on that block instead.
        if (player.isShiftKeyDown()) {
            HitResult hr = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (hr == null || hr.getType() == HitResult.Type.MISS) {
                if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) {
                    if (!level.isClientSide) NBTUtil.clearBucket(stack);
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

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode == NBTUtil.Mode.MILK) {
            player.startUsingItem(hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // Ray trace blocks; SB picks/places any fluid
        HitResult result = getPlayerPOVHitResult(level, player,
                mode == NBTUtil.Mode.NONE ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) result;

            // Announce the bucket use so protection and automation mods can veto it
            InteractionResultHolder<ItemStack> claimed = Protections.onBucketUse(player, level, stack, bhr);
            if (claimed != null) return claimed;

            if (mode == NBTUtil.Mode.NONE) {
                if (SBFluidLogic.getInstance().tryTake(level, bhr, stack, player)) {
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
            } else if (mode == NBTUtil.Mode.FLUID) {
                if (SBFluidLogic.getInstance().tryPlace(level, bhr, stack, player)) {
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        Player player = ctx.getPlayer();
        BlockPos clickedPos = ctx.getClickedPos();

        // Check if target block has fluid handler capability - if so, let capability system handle it
        BlockEntity blockEntity = level.getBlockEntity(clickedPos);
        if (blockEntity != null && blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, ctx.getClickedFace()).isPresent()) {
            return InteractionResult.PASS; // Let the block handle it via capability system
        }

        // Normal fluid interactions - only for world blocks without fluid handler
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        BlockHitResult bhr = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), clickedPos, false);

        if (mode == NBTUtil.Mode.NONE) {
            return SBFluidLogic.getInstance().tryTake(level, bhr, stack, player)
                    ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        } else if (mode == NBTUtil.Mode.FLUID) {
            return SBFluidLogic.getInstance().tryPlace(level, bhr, stack, player)
                    ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Cow cow) || cow.isBaby()) return InteractionResult.PASS;
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.NONE) return InteractionResult.PASS;

        Level level = player.level();
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (!Protections.mayAct(level, ProtectionContext.player(player, hand),
                ProtectionAction.ENTITY_INTERACT, cow.blockPosition(), net.minecraft.core.Direction.UP,
                stack, cow)) return InteractionResult.PASS;

        NBTUtil.setMilkAmount(stack, 1000);
        if (player instanceof ServerPlayer sp) {
            CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.COW_MILK, SoundSource.PLAYERS, 1.0F,
                1.0F);
        player.setItemInHand(hand, stack);
        player.getInventory().setChanged();
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK ? 32 : 0;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (NBTUtil.getMode(stack) == NBTUtil.Mode.MILK) {
            if (!level.isClientSide) {
                user.removeAllEffects();
                if (user instanceof Player p) {
                    p.awardStat(Stats.ITEM_USED.get(this));
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

        if (mode == NBTUtil.Mode.FLUID) {
            FluidStack fluidStack = NBTUtil.getFluidStack(stack);
            if (!fluidStack.isEmpty()) {
                if (fluidStack.getFluid() == Fluids.WATER) {
                    return Component.translatable("item.somebuckets.source_bucket.water");
                } else if (fluidStack.getFluid() == Fluids.LAVA) {
                    return Component.translatable("item.somebuckets.source_bucket.lava");
                } else {
                    // Generic fluid name
                    String fluidName = fluidStack.getFluid().getFluidType().getDescription().getString();
                    return Component.translatable("item.somebuckets.source_bucket.fluid", fluidName);
                }
            }
        } else if (mode == NBTUtil.Mode.MILK) {
            return Component.translatable("item.somebuckets.source_bucket.milk");
        }

        return Component.translatable("item.somebuckets.source_bucket");
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

    public static float getContentProperty(ItemStack stack) {
        return BBItem.getContentProperty(stack);
    }
}
