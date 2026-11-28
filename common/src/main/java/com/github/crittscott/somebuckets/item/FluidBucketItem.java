package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Base of the mod's single-fluid-container buckets (Big, Huge, and Source Bucket): the fluid
 * container rules every loader's fluid storage and the shared fluid logic apply, content naming, the
 * crafting remainder contract, lava fuel, cow milking, milk drinking, and the gesture helpers both
 * item classes share.
 *
 * <p>The container rules are pure: {@link #acceptable} and {@link #extractable} preview a transfer
 * in {@link StoredFluid} terms without changing the stack, and {@link #insert} and {@link #extract}
 * commit exactly what the preview allowed. A finite Big or Huge Bucket stores what it takes up to
 * its capacity; a Source Bucket admits only allowlisted fluids, keeps nothing beyond its assigned
 * identity, and supplies or sinks one bucket volume per call without depleting.
 */
public abstract class FluidBucketItem extends SomeBucketItem {
    public static final int BUCKET_VOLUME_MB = 1_000;

    /**
     * Dynamic-name suffixes appended to a bucket's registered description id, one per content kind.
     * Each has a matching {@code item.somebuckets.<bucket><suffix>} entry in {@code en_us.json}.
     */
    public static final String NAME_SUFFIX_WATER = ".water";
    public static final String NAME_SUFFIX_LAVA = ".lava";
    public static final String NAME_SUFFIX_FLUID = ".fluid";
    public static final String NAME_SUFFIX_MILK = ".milk";
    public static final String NAME_SUFFIX_POWDER_SNOW = ".powder_snow";

    protected FluidBucketItem(Properties properties) {
        super(properties);
    }

    /** The container's reported fluid capacity in millibuckets. */
    public abstract int getCapacityMb();

    /** Whether the container admits {@code fluid} at all, regardless of its current content. */
    public abstract boolean acceptsFluid(Fluid fluid);

    /**
     * Previews filling: how much of {@code offered} the stack would take.
     *
     * @return accepted millibuckets, from zero to {@code offered.amount()}
     */
    public abstract int acceptable(ItemStack stack, StoredFluid offered);

    /**
     * Commits a fill that {@link #acceptable} allowed.
     *
     * @param amount accepted millibuckets, no more than {@code acceptable(stack, offered)} returned
     */
    public abstract void insert(ItemStack stack, StoredFluid offered, int amount);

    /**
     * Previews draining up to {@code maxMb} of whatever the stack holds.
     *
     * @return the fluid and amount the stack would yield, or {@link StoredFluid#EMPTY}
     */
    public abstract StoredFluid extractable(ItemStack stack, int maxMb);

    /**
     * Commits a drain that {@link #extractable} allowed.
     *
     * @param amount yielded millibuckets, no more than {@code extractable} returned
     */
    public abstract void extract(ItemStack stack, int amount);

    /**
     * Returns the crafting leftover for one use of this bucket as an ingredient.
     *
     * @param stack the bucket stack consumed by the recipe
     * @return the 1-count leftover, or {@link ItemStack#EMPTY} for an empty bucket
     */
    public abstract ItemStack getUnitRemainder(ItemStack stack);

    /** Adapts the bucket's one-use remainder to the loaders' crafting hook. */
    @Nullable
    protected final ItemStackTemplate unitRemainderTemplate(ItemInstance instance) {
        ItemStack stack = switch (instance) {
            case ItemStack liveStack -> liveStack;
            case ItemStackTemplate template -> template.create();
            default -> throw new IllegalArgumentException("Unsupported item instance " + instance.getClass());
        };
        ItemStack remainder = getUnitRemainder(stack);
        return remainder.isEmpty() ? null : ItemStackTemplate.fromNonEmptyStack(remainder);
    }

    /**
     * Whether the bucket can take one bucket volume of milk from a cow: room in a Big or Huge
     * Bucket, or an unassigned Source Bucket while the allowlist permits milk.
     */
    public abstract boolean canMilkInto(ItemStack stack);

    /** Whether the stack holds milk the player can drink now. */
    protected abstract boolean holdsDrinkableMilk(ItemStack stack);

    /** Whether drinking milk drains one bucket volume; an infinite Source Bucket does not. */
    protected abstract boolean drainsOnDrink();

    /**
     * Room for milk in millibuckets: the capacity when empty, what remains when holding milk, and
     * none when holding anything else.
     */
    public int milkRoomMb(ItemStack stack) {
        return switch (BucketState.getMode(stack)) {
            case NONE -> getCapacityMb();
            case MILK -> getCapacityMb() - BucketState.getAmount(stack);
            default -> 0;
        };
    }

    /**
     * Adds {@code mb} of milk to an empty or milk-holding bucket. The caller has checked
     * {@link #milkRoomMb}.
     */
    public void addMilk(ItemStack stack, int mb) {
        int held = BucketState.getMode(stack) == BucketState.Mode.MILK ? BucketState.getAmount(stack) : 0;
        BucketState.setMilkAmount(stack, held + mb);
    }

    /**
     * The content the stack exposes to loader fluid storage: its fluid in fluid mode, its milk as
     * the loader milk fluid in milk mode when a mod has enabled one, otherwise empty.
     */
    public static StoredFluid exposedFluid(ItemStack stack) {
        return switch (BucketState.getMode(stack)) {
            case FLUID -> BucketState.getStoredFluid(stack);
            case MILK -> milkAsFluid(BucketState.getAmount(stack));
            default -> StoredFluid.EMPTY;
        };
    }

    /** Whether {@code fluid} is the loader milk fluid, which these buckets hold in milk mode. */
    protected static boolean isMilk(StoredFluid fluid) {
        Fluid milk = BucketOperations.get().milkFluid();
        return milk != null && !fluid.isEmpty() && fluid.fluid().isSame(milk);
    }

    /** {@code amount} millibuckets of the loader milk fluid, or empty when no mod has enabled it. */
    protected static StoredFluid milkAsFluid(int amount) {
        Fluid milk = BucketOperations.get().milkFluid();
        return milk == null || amount <= 0 ? StoredFluid.EMPTY : new StoredFluid(milk, amount);
    }

    /** Appends the stored content's suffix to the registered description id. */
    @Override
    public Component getName(ItemStack stack) {
        String baseKey = getDescriptionId();
        return switch (BucketState.getMode(stack)) {
            case FLUID -> resolveFluidName(baseKey, BucketState.getStoredFluid(stack));
            case MILK -> Component.translatable(baseKey + NAME_SUFFIX_MILK);
            case POWDER_SNOW -> Component.translatable(baseKey + NAME_SUFFIX_POWDER_SNOW);
            default -> Component.translatable(baseKey);
        };
    }

    /**
     * Reports whether {@code stack} currently provides bucket-grade lava fuel. Finite buckets need
     * at least one full unit; a Source Bucket must also retain an allowed lava assignment.
     */
    public static boolean isLavaFuel(ItemStack stack) {
        if (stack.getCount() != 1 || !(stack.getItem() instanceof FluidBucketItem)
                || BucketState.getMode(stack) != BucketState.Mode.FLUID) return false;

        StoredFluid fluid = BucketState.getStoredFluid(stack);
        if (stack.getItem() instanceof SBItem && !SBPolicy.allows(fluid.fluid())) return false;
        return fluid.fluid() == Fluids.LAVA
                && fluid.amount() >= BUCKET_VOLUME_MB;
    }

    /** Milks an adult cow into the bucket when {@link #canMilkInto} admits it. */
    @Override
    public final InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                        InteractionHand hand) {
        if (!(target instanceof Cow cow) || cow.isBaby() || !canMilkInto(stack)) return InteractionResult.PASS;
        return milkInto(stack, player, cow, hand);
    }

    /*
     * Milks cow into the held bucket, adding one bucket volume of milk up to its milk room. The
     * caller has checked that the bucket can take milk. Milking is routed through the cow's own
     * interaction; the client predicts the vanilla feedback and the server records the unit only
     * after an authorized interaction consumes the action. A stack of empty buckets fills one.
     */
    private InteractionResult milkInto(ItemStack stack, Player player, Cow cow, InteractionHand hand) {
        Level level = player.level();
        if (level.isClientSide()) {
            // Predict vanilla's client-side milking feedback without touching the bucket.
            HeldTransfers.milkCow(cow, player, hand);
            return InteractionResult.SUCCESS;
        }
        if (!Protections.mayInteractWithEntity(level, cow.blockPosition())) return InteractionResult.PASS;
        if (!HeldTransfers.milkCow(cow, player, hand)) return InteractionResult.PASS;

        HeldTransfers.fillFromHand(level, player, hand, stack, bucket -> {
            addMilk(bucket, Math.min(BUCKET_VOLUME_MB, milkRoomMb(bucket)));
            return true;
        });
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Re-targets {@code placeHit} at the position the loader's world placement of the stored fluid
     * would write, for announcing a placement before it happens. {@code placeHit} has already had
     * block targets removed ({@link #withoutBlockTarget}), so only world placement can serve it.
     */
    protected static BlockHitResult worldPlaceHit(Level level, Player player, InteractionHand hand, ItemStack stack,
                                                  BlockHitResult placeHit) {
        return withPos(placeHit, BucketOperations.get().resolveArbitraryPlaceTarget(level, placeHit, stack, player,
                hand, BucketState.getStoredFluid(stack), true));
    }

    /**
     * Re-targets a hit at a different block position.
     *
     * @param base the original hit
     * @param pos the position to re-target at
     * @return {@code base} re-targeted at {@code pos}, or {@code base} unchanged when {@code pos}
     *         already matches it
     */
    protected static BlockHitResult withPos(BlockHitResult base, BlockPos pos) {
        return pos.equals(base.getBlockPos()) ? base
                : new BlockHitResult(base.getLocation(), base.getDirection(), pos, base.isInside());
    }

    /**
     * Reports whether a hit targets a block the item leaves to block-side handling: an exposed loader
     * fluid store, served from {@code useOn}, or, when {@code includeCauldrons}, a vanilla cauldron,
     * served by the Source Bucket's own {@code useOn} or by the cauldron interaction map for Big and
     * Huge Buckets. Vanilla dispatch posts the loader's block-interaction event before either, which
     * is where claim mods decide whether a player may use a block.
     *
     * @param hit candidate hit; a miss is never a block target
     * @param includeCauldrons whether vanilla cauldrons count as block targets
     * @return {@code true} when the hit's block is left to block-side handling
     */
    protected static boolean isBlockTarget(Level level, HitResult hit, boolean includeCauldrons) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = blockHit.getBlockPos();
        return FluidTransactions.hasBlockStore(level, pos, blockHit.getDirection())
                || (includeCauldrons && Cauldrons.isVanillaCauldron(level.getBlockState(pos)));
    }

    /**
     * Converts a hit on a block target (see {@link #isBlockTarget}) into a miss, so {@code use}
     * leaves that block to block-side handling.
     *
     * @return {@code hit} unchanged, or a miss at the same location
     */
    protected static BlockHitResult withoutBlockTarget(Level level, BlockHitResult hit, boolean includeCauldrons) {
        return isBlockTarget(level, hit, includeCauldrons)
                ? BlockHitResult.miss(hit.getLocation(), hit.getDirection(), hit.getBlockPos()) : hit;
    }

    /**
     * Lazily posts Forge's world bucket-use event when {@code hitSupplier} resolves a block hit.
     * Other loaders return without resolving the supplier, so loader-neutral item code does not pay
     * for Forge-only target selection.
     */
    @Nullable
    protected static InteractionResult beforeWorldBucketUse(Player player, Level level, ItemStack stack,
                                                            Supplier<BlockHitResult> hitSupplier) {
        return BucketOperations.get().beforeWorldBucketUse(player, level, stack, () -> {
            BlockHitResult hit = hitSupplier.get();
            return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        });
    }

    /**
     * Builds the dynamic display name for a fluid-mode bucket.
     *
     * @param baseKey the bucket's registered description id
     * @param fluid the stored fluid
     * @return a component using the water, lava, or generic-fluid name suffix
     */
    private static Component resolveFluidName(String baseKey, StoredFluid fluid) {
        if (fluid.fluid() == Fluids.WATER) {
            return Component.translatable(baseKey + NAME_SUFFIX_WATER);
        } else if (fluid.fluid() == Fluids.LAVA) {
            return Component.translatable(baseKey + NAME_SUFFIX_LAVA);
        } else {
            return Component.translatable(baseKey + NAME_SUFFIX_FLUID,
                    BucketOperations.get().fluidDisplayName(fluid));
        }
    }

    /**
     * Clears an assigned bucket on a sneak-use against air.
     *
     * @param level acting level; the mutation runs on the server only
     * @param player acting player
     * @param stack the bucket stack
     * @param airHit the caller's own {@code ClipContext.Fluid.NONE} raytrace, shared with
     *               {@link #tryCrossHandTransfer}
     * @return {@code true} iff the interaction was handled (the bucket had content to clear)
     */
    protected static boolean tryShiftClear(Level level, Player player, ItemStack stack, HitResult airHit) {
        if (!player.isShiftKeyDown()) return false;
        if (airHit.getType() != HitResult.Type.MISS) return false;
        if (BucketState.getMode(stack) == BucketState.Mode.NONE) return false;

        if (!level.isClientSide()) BucketState.clearBucket(stack);
        level.playSound(player, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS,
                1.0f, 1.0f);
        return true;
    }

    /**
     * Transfers content with whatever the other hand holds, deliberately restricted to right-clicking
     * air: a targeted block means the player expects the bucket to act on that block instead.
     *
     * @param level acting level
     * @param player acting player
     * @param hand hand holding the bucket
     * @param stack the bucket stack
     * @param airHit the caller's own {@code ClipContext.Fluid.NONE} raytrace
     * @return {@code true} iff a transfer occurred
     */
    protected static boolean tryCrossHandTransfer(Level level, Player player, InteractionHand hand, ItemStack stack,
                                                  HitResult airHit) {
        if (airHit.getType() != HitResult.Type.MISS) return false;
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        if (otherStack.isEmpty()) return false;
        return HeldTransfers.tryTransfer(level, player, hand, stack, otherHand, otherStack);
    }

    /**
     * Starts drinking held milk through vanilla's use, which starts the stack's
     * {@link DataComponents#CONSUMABLE}, or passes when the stack holds no milk the player can drink.
     */
    protected final InteractionResult startMilkDrink(Level level, Player player, InteractionHand hand) {
        if (!holdsDrinkableMilk(player.getItemInHand(hand))) return InteractionResult.PASS;
        return super.use(level, player, hand);
    }

    @Override
    public final ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (holdsDrinkableMilk(stack)) finishMilkDrink(stack, level, user);
        return stack;
    }

    /*
     * Completes a milk drink of one bucket volume through the stack's vanilla milk CONSUMABLE
     * component, which BucketState keeps present exactly in milk mode: its sound and particles, drink
     * game event, statistic, criterion, and effect clearing under the loader's milk-cure rules. The
     * component consumes a detached copy so the bucket itself is never shrunk; a bucket that
     * drainsOnDrink instead loses one unit on the server.
     */
    private void finishMilkDrink(ItemStack stack, Level level, LivingEntity user) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return;
        consumable.onConsume(level, user, stack.copy());
        if (drainsOnDrink() && !level.isClientSide()) {
            BucketState.drainFiniteContent(stack, BUCKET_VOLUME_MB);
        }
    }
}
