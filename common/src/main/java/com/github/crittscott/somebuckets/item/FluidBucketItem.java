package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.config.SBPolicy;
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
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
    public static final int LAVA_BUCKET_BURN_TIME_TICKS = 20_000;

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
     * Returns the crafting leftover for one use of this bucket as an ingredient. Loader item shells
     * expose this through their crafting-remainder hook ({@code getCraftingRemainder} on Forge and
     * NeoForge, {@code getRecipeRemainder} on Fabric).
     *
     * @param stack the bucket stack consumed by the recipe
     * @return the 1-count leftover, or {@link ItemStack#EMPTY} for an empty bucket
     */
    public abstract ItemStack getUnitRemainder(ItemStack stack);

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
        if (!(stack.getItem() instanceof FluidBucketItem)
                || BucketState.getMode(stack) != BucketState.Mode.FLUID) return false;

        StoredFluid fluid = BucketState.getStoredFluid(stack);
        if (stack.getItem() instanceof SBItem && !SBPolicy.allows(fluid.fluid())) return false;
        return fluid.fluid() == Fluids.LAVA
                && fluid.amount() >= BUCKET_VOLUME_MB;
    }

    /**
     * Milks {@code cow} into the held bucket, adding one bucket volume of milk up to
     * {@code capacityMb}. The caller has checked that the bucket can take milk. Milking is routed
     * through the cow's own interaction; the client predicts the vanilla feedback and the server
     * records the unit only after an authorized interaction consumes the action.
     */
    protected final InteractionResult milkInto(ItemStack stack, Player player, Cow cow, InteractionHand hand,
                                               int capacityMb) {
        Level level = player.level();
        if (level.isClientSide) {
            // Predict vanilla's client-side milking feedback without touching the bucket.
            HeldTransfers.milkCow(cow, player, hand);
            return InteractionResult.SUCCESS;
        }
        if (!Protections.mayInteract(level, cow.blockPosition())) return InteractionResult.PASS;
        if (!HeldTransfers.milkCow(cow, player, hand)) return InteractionResult.PASS;

        HeldTransfers.fillFromHand(level, player, hand, stack, bucket -> {
            int held = BucketState.getMode(bucket) == BucketState.Mode.MILK ? BucketState.getAmount(bucket) : 0;
            BucketState.setMilkAmount(bucket, Math.min(held + BUCKET_VOLUME_MB, capacityMb));
            return true;
        });
        player.setItemInHand(hand, stack);
        player.getInventory().setChanged();
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS_SERVER;
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
     * Reports whether a hit targets a block the bucket interacts with as a block, from
     * {@code useOn}: an exposed loader fluid store, or any cauldron when {@code includeCauldrons}.
     * Vanilla dispatch posts the loader's block-interaction event before {@code useOn}, which is
     * where claim mods decide whether a player may use a block.
     *
     * @param hit candidate hit; a miss is never a block target
     * @param includeCauldrons whether cauldrons count as block targets
     * @return {@code true} when the hit's block is served from {@code useOn}
     */
    protected static boolean isBlockTarget(Level level, HitResult hit, boolean includeCauldrons) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = blockHit.getBlockPos();
        return BucketOperations.get().hasBlockStorage(level, pos, blockHit.getDirection())
                || (includeCauldrons && level.getBlockState(pos).getBlock() instanceof AbstractCauldronBlock);
    }

    /**
     * Converts a hit on a block target (see {@link #isBlockTarget}) into a miss, so {@code use}
     * leaves that block to {@code useOn}.
     *
     * @return {@code hit} unchanged, or a miss at the same location
     */
    protected static BlockHitResult withoutBlockTarget(Level level, BlockHitResult hit, boolean includeCauldrons) {
        return isBlockTarget(level, hit, includeCauldrons)
                ? BlockHitResult.miss(hit.getLocation(), hit.getDirection(), hit.getBlockPos()) : hit;
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

        if (!level.isClientSide) BucketState.clearBucket(stack);
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
     * Completes a milk drink of one bucket volume through the stack's vanilla milk
     * {@link DataComponents#CONSUMABLE} component, which {@code BucketState} keeps present exactly in
     * milk mode: its sound and particles, drink game event, statistic, criterion, and effect clearing
     * under the loader's milk-cure rules. The component consumes a detached copy so the bucket itself
     * is never shrunk; a finite bucket instead drains one unit on the server.
     *
     * @param stack the milk-mode bucket
     * @param level acting level; the bucket is drained on the server only
     * @param user the drinking entity
     * @param drain {@code true} to remove one bucket volume (finite Big or Huge Bucket),
     *              {@code false} for an infinite Source Bucket
     */
    protected static void finishMilkDrink(ItemStack stack, Level level, LivingEntity user, boolean drain) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return;
        consumable.onConsume(level, user, stack.copy());
        if (drain && !level.isClientSide) {
            BucketState.drainFiniteContent(stack, BUCKET_VOLUME_MB);
        }
    }
}
