package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Finite, single-content container shared by the Big and Huge Bucket tiers. Stacks like a vanilla
 * bucket: up to {@value SomeBucketItem#EMPTY_STACK_SIZE} while empty, one once filled.
 * Capacity is expressed in whole bucket units, while loader fluid transfers retain mB
 * precision; fluid, milk, and powder-snow modes remain mutually exclusive.
 * Dynamic names append a content suffix to the registered description ID.
 */
public class BBItem extends FluidBucketItem {
    private static final int EMPTY_BAR_COLOR = 0xAAAAAA;
    private static final int MILK_BAR_COLOR = 0xFFFFFF;
    private static final int POWDER_SNOW_BAR_COLOR = 0xE0F8FF;

    /* Client-installed stored-fluid bar color; a dedicated server keeps the default. */
    private static volatile ToIntFunction<StoredFluid> fluidBarColor =
            fluid -> DEFAULT_BUCKET_BAR_COLOR;

    private final int capacityUnits; // tier: 8 or 64

    /**
     * Creates a finite bucket with the given whole-bucket capacity.
     *
     * @param properties base item properties
     * @param capacityUnits capacity in bucket-volume units
     */
    public BBItem(Properties properties, int capacityUnits) {
        super(properties.rarity(Rarity.UNCOMMON));
        this.capacityUnits = capacityUnits;
    }

    /**
     * Installs the client's stored-fluid bar color. Called once during client bootstrap.
     *
     * @param resolver maps stored fluid to its RGB bar color
     */
    public static void installFluidBarColor(ToIntFunction<StoredFluid> resolver) {
        fluidBarColor = resolver;
    }

    /** Returns this bucket's capacity in whole bucket-volume units. */
    public int getCapacityUnits() { return capacityUnits; }

    /** Returns this bucket's capacity in millibuckets. */
    @Override
    public int getCapacityMb() { return capacityUnits * BUCKET_VOLUME_MB; }

    /* ------------------------- Container rules ------------------------- */

    @Override
    public boolean acceptsFluid(Fluid fluid) {
        return true;
    }

    /** An empty bucket takes any fluid; a fluid-mode bucket takes more of the same variant up to capacity. */
    @Override
    public int acceptable(ItemStack stack, StoredFluid offered) {
        if (offered.isEmpty()) return 0;
        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode == BucketState.Mode.NONE) return Math.min(getCapacityMb(), offered.amount());
        if (mode != BucketState.Mode.FLUID) return 0;
        StoredFluid current = BucketState.getStoredFluid(stack);
        if (!current.isSameVariant(offered)) return 0;
        return Math.max(0, Math.min(getCapacityMb() - current.amount(), offered.amount()));
    }

    @Override
    public void insert(ItemStack stack, StoredFluid offered, int amount) {
        StoredFluid current = BucketState.getStoredFluid(stack);
        BucketState.setStoredFluid(stack, BucketState.getMode(stack) == BucketState.Mode.FLUID
                ? current.withAmount(current.amount() + amount)
                : offered.withAmount(amount));
    }

    @Override
    public StoredFluid extractable(ItemStack stack, int maxMb) {
        if (BucketState.getMode(stack) != BucketState.Mode.FLUID || maxMb <= 0) return StoredFluid.EMPTY;
        StoredFluid current = BucketState.getStoredFluid(stack);
        return current.withAmount(Math.min(current.amount(), maxMb));
    }

    @Override
    public void extract(ItemStack stack, int amount) {
        BucketState.drainFiniteContent(stack, amount);
    }

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
        return stack.getItem() instanceof BBItem item
                && item.acceptable(stack, incoming.withAmount(BUCKET_VOLUME_MB)) == BUCKET_VOLUME_MB;
    }

    /* ------------------------- Tooltip ------------------------- */

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

    /* ------------------------- UI bar ------------------------- */

    @Override public boolean isBarVisible(ItemStack stack) { return BucketState.getMode(stack) != BucketState.Mode.NONE; }

    @Override
    public int getBarWidth(ItemStack stack) {
        int capUnits = getCapacityUnits();
        BucketState.Mode mode = BucketState.getMode(stack);
        if (mode == BucketState.Mode.FLUID || mode == BucketState.Mode.MILK) {
            return barWidth(BucketState.getAmount(stack), capUnits * BUCKET_VOLUME_MB);
        } else if (mode == BucketState.Mode.POWDER_SNOW) {
            return barWidth(BucketState.getPowderUnits(stack), capUnits);
        }
        return 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        BucketState.Mode mode = BucketState.getMode(stack);
        switch (mode) {
            case FLUID -> {
                return fluidBarColor.applyAsInt(BucketState.getStoredFluid(stack));
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
     * Handles a use against a block. A clicked loader fluid store is served here, after vanilla
     * dispatch posted the block-interaction event: an empty bucket takes, a full one places, and a
     * partial one takes when it can and places otherwise. For a powder-snow-filled bucket, sneaking,
     * or a target that cannot be collected, places one block. Every other use passes so
     * {@link #use} can run the take-then-place order.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        InteractionHand hand = context.getHand();
        ItemStack stack = context.getItemInHand();
        BucketState.Mode mode = BucketState.getMode(stack);
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), context.isInside());

        if ((mode == BucketState.Mode.NONE || mode == BucketState.Mode.FLUID)
                && FluidBucketItem.isBlockTarget(level, hit, false)) {
            boolean acted = mode == BucketState.Mode.NONE
                    ? HeldTransfers.fillFromHand(level, player, hand, stack,
                            working -> FluidTransactions.tryTakeFinite(level, hit, working, player, hand))
                    : (BucketState.getAmount(stack) < getCapacityMb()
                            && FluidTransactions.tryTakeFinite(level, hit, stack, player, hand))
                            || FluidTransactions.tryPlaceFinite(level, hit, stack, player, hand);
            if (!acted) return InteractionResult.PASS;
            return success(level);
        }

        if (mode != BucketState.Mode.POWDER_SNOW) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()
                && FluidTransactions.canAttemptTakePowderAt(level, hit, stack)) {
            return InteractionResult.PASS;
        }

        return FluidTransactions.tryPlacePowder(level, hit, stack, player, hand)
                ? success(level)
                : InteractionResult.PASS;
    }

    /**
     * Drives the main gesture. A held-container transfer or a sneak-clear on air takes priority; then
     * a milk-filled bucket drinks; otherwise the bucket takes compatible content when possible and
     * places one unit when not, resolving take and place targets with separate raytraces for vanilla
     * parity and posting the fill-bucket event at the position dispatch will act on.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        HitResult airHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (FluidBucketItem.tryShiftClear(level, player, stack, airHit)) {
            return success(level);
        }
        if (FluidBucketItem.tryCrossHandTransfer(level, player, hand, stack, airHit)) {
            return success(level);
        }

        BucketState.Mode mode = BucketState.getMode(stack);
        int capMb = getCapacityMb();

        // Drinking milk: vanilla's use starts the stack's CONSUMABLE
        if (mode == BucketState.Mode.MILK) {
            return super.use(level, player, hand);
        }

        // Two raytraces: SOURCE_ONLY for taking, NONE for placing (vanilla parity). A fluid store
        // is left to useOn, so it counts as a miss here.
        BlockHitResult takeHit = FluidBucketItem.withoutBlockTarget(level,
                getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY), false);
        BlockHitResult placeHit = FluidBucketItem.withoutBlockTarget(level,
                getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE), false);

        // Sneaking at a powder-snow target prefers placing another block over taking the one
        // targeted, so a full-handed player can build outward instead of vacuuming their own wall.
        boolean targetsPowderSnow = mode == BucketState.Mode.POWDER_SNOW
                && takeHit.getType() == HitResult.Type.BLOCK
                && FluidTransactions.canAttemptTakePowderAt(level, takeHit, stack);
        boolean powderPickup = targetsPowderSnow && !player.isShiftKeyDown();

        // Announce fluid operations and powder pickup at the position this call would actually act
        // on. Powder output uses the native block-place event instead. Target resolution mirrors the
        // dispatch below so the selected event and mutation position cannot disagree. The loader
        // hook resolves this supplier only on Forge.
        InteractionResult claimed = FluidBucketItem.beforeWorldBucketUse(player, level, stack, () -> {
            BlockHitResult eventHit = resolveEventHit(level, player, hand, stack, mode, capMb, takeHit,
                    placeHit, powderPickup);
            return mode != BucketState.Mode.POWDER_SNOW || powderPickup ? eventHit : null;
        });
        if (claimed != null) return claimed;

        switch (mode) {
            case POWDER_SNOW:
                if (powderPickup &&
                        FluidTransactions.tryTakePowder(level, takeHit, stack, player, hand))
                    return success(level);
                break;

            case FLUID: {
                int amt = BucketState.getAmount(stack);

                if (amt >= capMb) {
                    if (placeHit.getType() != HitResult.Type.MISS &&
                            FluidTransactions.tryPlaceFinite(level, placeHit, stack, player, hand))
                        return success(level);
                } else {
                    // Partial: try take, else place (bucket intuition)
                    if (takeHit.getType() != HitResult.Type.MISS &&
                            FluidTransactions.tryTakeFinite(level, takeHit, stack, player, hand))
                        return success(level);

                    if (placeHit.getType() != HitResult.Type.MISS &&
                            FluidTransactions.tryPlaceFinite(level, placeHit, stack, player, hand))
                        return success(level);
                }
                break;
            }

            default: // Empty or unsupported content
                if (takeHit.getType() != HitResult.Type.MISS &&
                        HeldTransfers.fillFromHand(level, player, hand, stack,
                                working -> FluidTransactions.tryTakeFinite(level, takeHit, working, player, hand)))
                    return success(level);

                if (takeHit.getType() != HitResult.Type.MISS &&
                        HeldTransfers.fillFromHand(level, player, hand, stack,
                                working -> FluidTransactions.tryTakePowder(level, takeHit, working, player, hand)))
                    return success(level);
                break;
        }
        return InteractionResult.PASS;
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
     * @return the hit to post the fill-bucket event against, or {@code null} when powder output will
     *         use its native block-place event
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
            int amt = BucketState.getAmount(stack);

            if (amt < capMb && takeHit.getType() == HitResult.Type.BLOCK
                    && FluidTransactions.canTakeFiniteAt(level, takeHit, stack)) {
                return takeHit;
            }
            if (placeHit.getType() != HitResult.Type.BLOCK) return placeHit;
            return FluidBucketItem.withPos(placeHit,
                    FluidTransactions.resolveFinitePlaceTarget(
                            level, placeHit, stack, player, hand, true));
        }

        return takeHit; // Empty or unsupported content: take is the only possible action.
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (BucketState.getMode(stack) == BucketState.Mode.MILK && living instanceof Player) {
            FluidBucketItem.finishMilkDrink(stack, level, living, true);
        }
        return stack;
    }

    /* ------------------------- Interact with entities ------------------------- */

    /**
     * Milks an adult cow into an empty or milk-holding bucket, adding one bucket volume up to
     * capacity.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Cow cow) || cow.isBaby()) return InteractionResult.PASS;
        BucketState.Mode mode = BucketState.getMode(stack);
        boolean canMilk = mode == BucketState.Mode.NONE
                || (mode == BucketState.Mode.MILK && BucketState.getAmount(stack) < getCapacityMb());
        if (!canMilk) return InteractionResult.PASS;
        return milkInto(stack, player, cow, hand, getCapacityMb());
    }

    /* ------------------------- Crafting remainder ------------------------- */

    /**
     * Returns the crafting leftover for one use of this bucket as an ingredient.
     *
     * @param stack the bucket stack consumed by the recipe
     * @return a 1-count copy with one bucket volume of fluid or milk, or one powder-snow block,
     *         removed and its empty state canonicalized; {@link ItemStack#EMPTY} for an already-empty
     *         bucket
     */
    @Override
    public ItemStack getUnitRemainder(ItemStack stack) {
        if (BucketState.isEmptyBucket(stack)) return ItemStack.EMPTY;

        ItemStack result = stack.copy();
        result.setCount(1);
        switch (BucketState.getMode(result)) {
            case FLUID, MILK -> BucketState.drainFiniteContent(result, BUCKET_VOLUME_MB);
            case POWDER_SNOW -> BucketState.setPowderUnits(result, BucketState.getPowderUnits(result) - 1);
            default -> BucketState.clearBucket(result);
        }
        return result;
    }
}
