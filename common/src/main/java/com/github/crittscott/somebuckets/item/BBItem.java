package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.advancements.CriteriaTriggers;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Finite, single-content container shared by the Big and Huge Bucket tiers. Stacks like a vanilla
 * bucket: up to {@value VariableStackItem#EMPTY_STACK_SIZE} while empty, one once filled.
 * Capacity is expressed in whole bucket units, while loader fluid transfers retain mB
 * precision; fluid, milk, and powder-snow modes remain mutually exclusive.
 * Dynamic names append a content suffix to the registered description ID, and
 * {@link FluidBucketItem#CONTENT_PROPERTY} exposes the shared item-model state protocol.
 */
public class BBItem extends Item implements FluidBucketItem, VariableStackItem {
    private static final int DEFAULT_FLUID_BAR_COLOR = 0x4A90E2;
    private static final int EMPTY_BAR_COLOR = 0xAAAAAA;
    private static final int MILK_BAR_COLOR = 0xFFFFFF;
    private static final int POWDER_SNOW_BAR_COLOR = 0xE0F8FF;

    private final int capacityUnits; // tier: 8 or 64

    public BBItem(Properties properties, int capacityUnits) {
        super(properties.stacksTo(EMPTY_STACK_SIZE).rarity(Rarity.UNCOMMON));
        this.capacityUnits = capacityUnits;
    }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return NBTUtil.isEmptyBucket(stack);
    }

    public int getCapacityUnits() { return capacityUnits; }
    public int getCapacityMb()    { return capacityUnits * BUCKET_VOLUME_MB; }

    /**
     * Whether a finite Big or Huge Bucket in {@code stack} can take one more bucket-volume of
     * {@code incoming}: it must carry no mode yet, or already be in fluid mode holding a compatible
     * variant with room for one more unit. Read-only; checks neither protection nor the world.
     */
    public static boolean canAcceptFluidUnit(ItemStack stack, StoredFluid incoming) {
        if (!(stack.getItem() instanceof BBItem item)) return false;
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        if (mode == NBTUtil.Mode.NONE) return true;
        if (mode != NBTUtil.Mode.FLUID) return false;
        StoredFluid current = NBTUtil.getStoredFluid(stack);
        return current.isEmpty()
                || (current.isSameVariant(incoming)
                        && current.amount() + BUCKET_VOLUME_MB <= item.getCapacityMb());
    }

    /* ------------------------- Tooltip and naming ------------------------- */

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int capUnits = getCapacityUnits();

        switch (mode) {
            case FLUID, MILK -> {
                int current = NBTUtil.getAmount(stack) / BUCKET_VOLUME_MB;
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
            StoredFluid fluid = NBTUtil.getStoredFluid(stack);
            if (!fluid.isEmpty()) {
                return FluidBucketItem.resolveFluidName(baseKey, fluid);
            }
        } else if (mode == NBTUtil.Mode.MILK) {
            return Component.translatable(baseKey + NAME_SUFFIX_MILK);
        } else if (mode == NBTUtil.Mode.POWDER_SNOW) {
            return Component.translatable(baseKey + NAME_SUFFIX_POWDER_SNOW);
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
                    / (float) (capUnits * BUCKET_VOLUME_MB));
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
                StoredFluid fluid = NBTUtil.getStoredFluid(stack);
                if (!fluid.isEmpty()) {
                    return BucketOperations.get().fluidColor(fluid, DEFAULT_FLUID_BAR_COLOR);
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

    /* ------------------------- Use (right-click) ------------------------- */

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        if (NBTUtil.getMode(stack) != NBTUtil.Mode.POWDER_SNOW
                || NBTUtil.getPowderUnits(stack) <= 0) return InteractionResult.PASS;

        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), context.isInside());
        if (!player.isShiftKeyDown()
                && BucketOperations.get().canAttemptPowderTake(context.getLevel(), hit, stack)) {
            return InteractionResult.PASS;
        }

        return BucketOperations.get().tryPowderPlace(
                context.getLevel(), hit, stack, player, context.getHand())
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide)
                : InteractionResult.PASS;
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

        // Repair malformed persisted zero-content modes before branching.
        NBTUtil.normalizeEmptyState(stack);

        NBTUtil.Mode mode = NBTUtil.getMode(stack);
        int capMb = ((BBItem) stack.getItem()).getCapacityMb();

        // Drinking milk
        if (mode == NBTUtil.Mode.MILK) {
            if (NBTUtil.getAmount(stack) >= BUCKET_VOLUME_MB) {
                player.startUsingItem(hand); return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.pass(stack);
        }

        // Two raytraces: SOURCE_ONLY for taking, NONE for placing (vanilla parity)
        BlockHitResult takeHit  = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        BlockHitResult placeHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);

        // Sneaking at a powder-snow target prefers placing another block over taking the one
        // targeted, so a full-handed player can build outward instead of vacuuming their own wall.
        boolean targetsPowderSnow = mode == NBTUtil.Mode.POWDER_SNOW
                && takeHit.getType() == HitResult.Type.BLOCK
                && BucketOperations.get().canAttemptPowderTake(level, takeHit, stack);
        boolean powderPickup = targetsPowderSnow && !player.isShiftKeyDown();

        // Announce fluid operations and powder pickup at the position this call would actually act
        // on. Powder output uses the native block-place event instead. Target resolution mirrors the
        // dispatch below so the selected event and mutation position cannot disagree.
        BlockHitResult eventHit = resolveEventHit(level, player, hand, stack, mode, capMb, takeHit, placeHit,
                powderPickup);
        if (eventHit != null && eventHit.getType() == HitResult.Type.BLOCK
                && (mode != NBTUtil.Mode.POWDER_SNOW || powderPickup)) {
            InteractionResultHolder<ItemStack> claimed = BucketOperations.get()
                    .beforeWorldBucketUse(player, level, stack, eventHit);
            if (claimed != null) return claimed;
        }

        switch (mode) {
            case POWDER_SNOW:
                if (powderPickup &&
                        BucketOperations.get().tryPowderTake(level, takeHit, stack, player, hand))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                break;

            case FLUID: {
                StoredFluid current = NBTUtil.getStoredFluid(stack);
                int amt = current.amount();

                if (amt == 0) {
                    if (takeHit.getType() != HitResult.Type.MISS &&
                            BucketOperations.get().tryBigTake(level, takeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else if (amt >= capMb) {
                    if (placeHit.getType() != HitResult.Type.MISS &&
                            BucketOperations.get().tryBigPlace(level, placeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else {
                    // Partial: try take, else place (bucket intuition)
                    if (takeHit.getType() != HitResult.Type.MISS &&
                            BucketOperations.get().tryBigTake(level, takeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                    if (placeHit.getType() != HitResult.Type.MISS &&
                            BucketOperations.get().tryBigPlace(level, placeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
                break;
            }

            default: // Empty or unsupported content
                if (takeHit.getType() != HitResult.Type.MISS &&
                        BucketOperations.get().tryBigTake(level, takeHit, stack, player, hand))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                if (takeHit.getType() != HitResult.Type.MISS &&
                        BucketOperations.get().tryPowderTake(level, takeHit, stack, player, hand))
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
                                                   BlockHitResult takeHit, BlockHitResult placeHit,
                                                   boolean powderPickup) {
        if (mode == NBTUtil.Mode.POWDER_SNOW) {
            return powderPickup ? takeHit : null;
        }

        if (mode == NBTUtil.Mode.FLUID) {
            StoredFluid current = NBTUtil.getStoredFluid(stack);
            int amt = current.amount();

            if (amt == 0) {
                return takeHit.getType() == HitResult.Type.BLOCK
                        && BucketOperations.get().hasBlockStorage(level, takeHit.getBlockPos(), takeHit.getDirection())
                        ? null : takeHit;
            }

            if (amt < capMb && takeHit.getType() == HitResult.Type.BLOCK) {
                if (BucketOperations.get().hasBlockStorage(level, takeHit.getBlockPos(), takeHit.getDirection())) {
                    return null;
                }
                if (BucketOperations.get().canAttemptBigTake(level, takeHit, stack)) return takeHit;
            }
            if (placeHit.getType() != HitResult.Type.BLOCK) return placeHit;
            if (BucketOperations.get().hasBlockStorage(level, placeHit.getBlockPos(), placeHit.getDirection())) {
                return null;
            }
            return FluidBucketItem.withPos(placeHit,
                    BucketOperations.get().resolveBigPlaceTarget(
                            level, placeHit, stack, player, hand, true));
        }

        if (takeHit.getType() == HitResult.Type.BLOCK
                && BucketOperations.get().hasBlockStorage(level, takeHit.getBlockPos(), takeHit.getDirection())) {
            return null;
        }
        return takeHit; // Empty or unsupported content: take is the only possible action.
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity user) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK
                && NBTUtil.getAmount(stack) >= BUCKET_VOLUME_MB ? DRINK_DURATION_TICKS : 0;
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) {
        return NBTUtil.getMode(stack) == NBTUtil.Mode.MILK
                && NBTUtil.getAmount(stack) >= BUCKET_VOLUME_MB ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (NBTUtil.getMode(stack) == NBTUtil.Mode.MILK
                && NBTUtil.getAmount(stack) >= BUCKET_VOLUME_MB
                && living instanceof Player player) {
            // Trigger and award against the still-milk-filled stack before mutating it, matching
            // vanilla MilkBucketItem's ordering.
            if (player instanceof ServerPlayer sp) {
                CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!level.isClientSide) {
                player.removeAllEffects();
                NBTUtil.drainFiniteContent(stack, BUCKET_VOLUME_MB);
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
                            && NBTUtil.getAmount(stack) < capUnits * BUCKET_VOLUME_MB);
            if (!canMilk) return InteractionResult.PASS;

            if (level.isClientSide) return InteractionResult.sidedSuccess(true);
            if (!Protections.mayAct(level, ProtectionContext.player(player, hand),
                    ProtectionAction.ENTITY_INTERACT, cow.blockPosition(), Direction.UP,
                    stack, cow)) return InteractionResult.PASS;

            if (NBTUtil.getMode(stack) == NBTUtil.Mode.NONE) {
                NBTUtil.setMilkAmount(stack, BUCKET_VOLUME_MB);
            } else {
                NBTUtil.setMilkAmount(stack, Math.min(capUnits * BUCKET_VOLUME_MB,
                        NBTUtil.getAmount(stack) + BUCKET_VOLUME_MB));
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

    /**
     * Returns the crafting leftover for one use of this bucket as an ingredient: a 1-count copy with
     * one bucket volume of fluid or milk, or one powder-snow block, removed and its empty state
     * canonicalized. An already-empty bucket yields {@link ItemStack#EMPTY}. Loader item shells
     * expose this through {@code getCraftingRemainingItem}.
     */
    public ItemStack getUnitRemainder(ItemStack stack) {
        if (NBTUtil.isEmptyBucket(stack)) return ItemStack.EMPTY;

        ItemStack result = stack.copy();
        result.setCount(1);
        switch (NBTUtil.getMode(result)) {
            case FLUID, MILK -> NBTUtil.drainFiniteContent(result, BUCKET_VOLUME_MB);
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
