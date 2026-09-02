package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.fluid.BBFluidLogic;
import com.github.crittscott.somebuckets.interaction.MilkTransfers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.github.crittscott.somebuckets.protection.Protections;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
        return BucketState.isEmptyBucket(stack);
    }

    public int getCapacityUnits() { return capacityUnits; }
    public int getCapacityMb()    { return capacityUnits * BUCKET_VOLUME_MB; }

    /**
     * Reports whether a finite Big or Huge Bucket can take one more bucket-volume of a fluid.
     * Read-only; checks neither protection nor the world.
     *
     * @param stack candidate bucket stack
     * @param incoming fluid the caller wants to add one unit of
     * @return {@code true} when the stack is a {@link BBItem} that carries no mode yet, or is already
     *         in fluid mode holding a compatible variant with room for one more unit
     */
    public static boolean canAcceptFluidUnit(ItemStack stack, StoredFluid incoming) {
        if (!(stack.getItem() instanceof BBItem item)) return false;
        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode == BucketState.Mode.NONE) return true;
        if (mode != BucketState.Mode.FLUID) return false;
        StoredFluid current = BucketState.getStoredFluid(stack);
        return current.isEmpty()
                || (current.isSameVariant(incoming)
                        && current.amount() + BUCKET_VOLUME_MB <= item.getCapacityMb());
    }

    /* ------------------------- Tooltip and naming ------------------------- */

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        BucketState.Mode mode = BucketState.getMode(stack);
        int capUnits = getCapacityUnits();

        switch (mode) {
            case FLUID, MILK -> {
                int current = BucketState.getAmount(stack) / BUCKET_VOLUME_MB;
                tooltip.add(Component.translatable(
                        "tooltip.somebuckets.big_bucket.fluid", current, capUnits));
            }
            case POWDER_SNOW -> {
                int current = BucketState.getPowderUnits(stack);
                tooltip.add(Component.translatable(
                        "tooltip.somebuckets.big_bucket.powder_snow", current, capUnits));
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        BucketState.Mode mode = BucketState.getMode(stack);
        String baseKey = getDescriptionId();

        if (mode == BucketState.Mode.FLUID) {
            StoredFluid fluid = BucketState.getStoredFluid(stack);
            if (!fluid.isEmpty()) {
                return FluidBucketItem.resolveFluidName(baseKey, fluid);
            }
        } else if (mode == BucketState.Mode.MILK) {
            return Component.translatable(baseKey + NAME_SUFFIX_MILK);
        } else if (mode == BucketState.Mode.POWDER_SNOW) {
            return Component.translatable(baseKey + NAME_SUFFIX_POWDER_SNOW);
        }

        return Component.translatable(baseKey);
    }

    /* ------------------------- UI bar ------------------------- */

    @Override public boolean isBarVisible(ItemStack stack) { return BucketState.getMode(stack) != BucketState.Mode.NONE; }

    @Override
    public int getBarWidth(ItemStack stack) {
        int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();
        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode == BucketState.Mode.FLUID || mode == BucketState.Mode.MILK) {
            return Math.round(VariableStackItem.ITEM_BAR_WIDTH * (float) BucketState.getAmount(stack)
                    / (float) (capUnits * BUCKET_VOLUME_MB));
        } else if (mode == BucketState.Mode.POWDER_SNOW) {
            return Math.round(VariableStackItem.ITEM_BAR_WIDTH * (float) BucketState.getPowderUnits(stack) / (float)capUnits);
        }
        return 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        BucketState.Mode mode = BucketState.getMode(stack);
        switch (mode) {
            case FLUID -> {
                StoredFluid fluid = BucketState.getStoredFluid(stack);
                if (!fluid.isEmpty()) {
                    return BucketOperations.get().fluidColor(fluid, VariableStackItem.DEFAULT_BUCKET_BAR_COLOR);
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

    /**
     * Handles a use against a block for a powder-snow-filled bucket: sneaking, or a target that
     * cannot be collected, places one block; otherwise the use passes so {@link #use} can run the
     * take-then-place order. Buckets in any other mode pass.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        if (BucketState.getMode(stack) != BucketState.Mode.POWDER_SNOW
                || BucketState.getPowderUnits(stack) <= 0) return InteractionResult.PASS;

        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), context.isInside());
        if (!player.isShiftKeyDown()
                && BBFluidLogic.canAttemptTakePowderAt(context.getLevel(), hit, stack)) {
            return InteractionResult.PASS;
        }

        return BBFluidLogic.tryPlacePowder(
                context.getLevel(), hit, stack, player, context.getHand())
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide)
                : InteractionResult.PASS;
    }

    /**
     * Drives the main gesture. A held-container transfer or a sneak-clear on air takes priority; then
     * a milk-filled bucket drinks; otherwise the bucket takes compatible content when possible and
     * places one unit when not, resolving take and place targets with separate raytraces for vanilla
     * parity and posting the fill-bucket event at the position dispatch will act on.
     */
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

        BucketState.Mode mode = BucketState.getMode(stack);
        int capMb = ((BBItem) stack.getItem()).getCapacityMb();

        // Drinking milk
        if (mode == BucketState.Mode.MILK) {
            if (BucketState.getAmount(stack) >= BUCKET_VOLUME_MB) {
                player.startUsingItem(hand); return InteractionResultHolder.consume(stack);
            }
            return InteractionResultHolder.pass(stack);
        }

        // Two raytraces: SOURCE_ONLY for taking, NONE for placing (vanilla parity)
        BlockHitResult takeHit  = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        BlockHitResult placeHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);

        // Sneaking at a powder-snow target prefers placing another block over taking the one
        // targeted, so a full-handed player can build outward instead of vacuuming their own wall.
        boolean targetsPowderSnow = mode == BucketState.Mode.POWDER_SNOW
                && takeHit.getType() == HitResult.Type.BLOCK
                && BBFluidLogic.canAttemptTakePowderAt(level, takeHit, stack);
        boolean powderPickup = targetsPowderSnow && !player.isShiftKeyDown();

        // Announce fluid operations and powder pickup at the position this call would actually act
        // on. Powder output uses the native block-place event instead. Target resolution mirrors the
        // dispatch below so the selected event and mutation position cannot disagree. Only Forge
        // fires a world bucket-use event, so other loaders skip the pre-resolution.
        if (BucketOperations.get().firesWorldBucketEvent()) {
            BlockHitResult eventHit = resolveEventHit(level, player, hand, stack, mode, capMb, takeHit,
                    placeHit, powderPickup);
            if (eventHit != null && eventHit.getType() == HitResult.Type.BLOCK
                    && (mode != BucketState.Mode.POWDER_SNOW || powderPickup)) {
                InteractionResultHolder<ItemStack> claimed = BucketOperations.get()
                        .beforeWorldBucketUse(player, level, stack, eventHit);
                if (claimed != null) return claimed;
            }
        }

        switch (mode) {
            case POWDER_SNOW:
                if (powderPickup &&
                        BBFluidLogic.tryTakePowder(level, takeHit, stack, player, hand))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                break;

            case FLUID: {
                StoredFluid current = BucketState.getStoredFluid(stack);
                int amt = current.amount();

                if (amt == 0) {
                    if (takeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.tryTake(level, takeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else if (amt >= capMb) {
                    if (placeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.tryPlace(level, placeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                } else {
                    // Partial: try take, else place (bucket intuition)
                    if (takeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.tryTake(level, takeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                    if (placeHit.getType() != HitResult.Type.MISS &&
                            BBFluidLogic.tryPlace(level, placeHit, stack, player, hand))
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
                break;
            }

            default: // Empty or unsupported content
                if (takeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.tryTake(level, takeHit, stack, player, hand))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);

                if (takeHit.getType() != HitResult.Type.MISS &&
                        BBFluidLogic.tryTakePowder(level, takeHit, stack, player, hand))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                break;
        }
        return InteractionResultHolder.pass(stack);
    }

    /**
     * Resolves the block position {@link #use}'s dispatch would actually act on, mirroring its own
     * take-then-place branching so the posted event and the mutation cannot disagree.
     *
     * @param level acting level
     * @param player acting player
     * @param hand hand holding the bucket
     * @param stack the bucket stack
     * @param mode current bucket mode
     * @param capMb capacity in millibuckets
     * @param takeHit source-only raytrace used for taking
     * @param placeHit fluid-none raytrace used for placing
     * @param powderPickup whether this call would collect a powder-snow block
     * @return the hit to post the fill-bucket event against, or {@code null} when a block storage
     *         owns the transfer or powder output will use its native block-place event
     */
    @Nullable
    private static BlockHitResult resolveEventHit(Level level, Player player, InteractionHand hand,
                                                   ItemStack stack, BucketState.Mode mode, int capMb,
                                                   BlockHitResult takeHit, BlockHitResult placeHit,
                                                   boolean powderPickup) {
        if (mode == BucketState.Mode.POWDER_SNOW) {
            return powderPickup ? takeHit : null;
        }

        if (mode == BucketState.Mode.FLUID) {
            StoredFluid current = BucketState.getStoredFluid(stack);
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
                if (BBFluidLogic.canAttemptTakeAt(level, takeHit, stack)) return takeHit;
            }
            if (placeHit.getType() != HitResult.Type.BLOCK) return placeHit;
            if (BucketOperations.get().hasBlockStorage(level, placeHit.getBlockPos(), placeHit.getDirection())) {
                return null;
            }
            return FluidBucketItem.withPos(placeHit,
                    BBFluidLogic.resolvePlaceTarget(
                            level, placeHit, stack, player, hand, true));
        }

        if (takeHit.getType() == HitResult.Type.BLOCK
                && BucketOperations.get().hasBlockStorage(level, takeHit.getBlockPos(), takeHit.getDirection())) {
            return null;
        }
        return takeHit; // Empty or unsupported content: take is the only possible action.
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity user) {
        return BucketState.getMode(stack) == BucketState.Mode.MILK
                && BucketState.getAmount(stack) >= BUCKET_VOLUME_MB ? DRINK_DURATION_TICKS : 0;
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) {
        return BucketState.getMode(stack) == BucketState.Mode.MILK
                && BucketState.getAmount(stack) >= BUCKET_VOLUME_MB ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (BucketState.getMode(stack) == BucketState.Mode.MILK
                && BucketState.getAmount(stack) >= BUCKET_VOLUME_MB
                && living instanceof Player) {
            FluidBucketItem.finishMilkDrink(stack, level, living, this, true);
        }
        return stack;
    }

    /* ------------------------- Interact with entities ------------------------- */

    /**
     * Milks an adult cow into the bucket, adding one bucket volume up to capacity and selecting milk
     * mode on an empty bucket. Milking is routed through the cow's own interaction; the client
     * predicts the vanilla feedback and the server records the unit only after an authorized
     * interaction consumes the action.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        Level level = player.level();
        int capUnits = ((BBItem) stack.getItem()).getCapacityUnits();

        // Milking adds one bucket volume, up to capacity.
        if (target instanceof Cow cow && !cow.isBaby()) {
            boolean canMilk = BucketState.getMode(stack) == BucketState.Mode.NONE ||
                    (BucketState.getMode(stack) == BucketState.Mode.MILK
                            && BucketState.getAmount(stack) < capUnits * BUCKET_VOLUME_MB);
            if (!canMilk) return InteractionResult.PASS;

            if (level.isClientSide) {
                // Predict vanilla's client-side milking feedback without touching the bucket.
                MilkTransfers.milkCow(cow, player, hand);
                return InteractionResult.sidedSuccess(true);
            }
            if (!Protections.mayAct(level, ProtectionContext.player(player, hand),
                    ProtectionAction.ENTITY_INTERACT, cow.blockPosition(), Direction.UP,
                    stack, cow)) return InteractionResult.PASS;

            if (!MilkTransfers.milkCow(cow, player, hand)) return InteractionResult.PASS;

            if (BucketState.getMode(stack) == BucketState.Mode.NONE) {
                BucketState.setMilkAmount(stack, BUCKET_VOLUME_MB);
            } else {
                BucketState.setMilkAmount(stack, Math.min(capUnits * BUCKET_VOLUME_MB,
                        BucketState.getAmount(stack) + BUCKET_VOLUME_MB));
            }

            player.setItemInHand(hand, stack);
            player.getInventory().setChanged();
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResult.sidedSuccess(false);
        }

        return InteractionResult.PASS;
    }

    /* ------------------------- Crafting remainder ------------------------- */

    /**
     * Returns the crafting leftover for one use of this bucket as an ingredient. Loader item shells
     * expose this through {@code getCraftingRemainingItem}.
     *
     * @param stack the bucket stack consumed by the recipe
     * @return a 1-count copy with one bucket volume of fluid or milk, or one powder-snow block,
     *         removed and its empty state canonicalized; {@link ItemStack#EMPTY} for an already-empty
     *         bucket
     */
    public ItemStack getUnitRemainder(ItemStack stack) {
        if (BucketState.isEmptyBucket(stack)) return ItemStack.EMPTY;

        ItemStack result = stack.copy();
        result.setCount(1);
        switch (BucketState.getMode(result)) {
            case FLUID, MILK -> BucketState.drainFiniteContent(result, BUCKET_VOLUME_MB);
            case POWDER_SNOW -> {
                int units = BucketState.getPowderUnits(result);
                if (units > 0) BucketState.setPowderUnits(result, units - 1);
            }
            default -> BucketState.clearBucket(result);
        }
        return result;
    }
}
