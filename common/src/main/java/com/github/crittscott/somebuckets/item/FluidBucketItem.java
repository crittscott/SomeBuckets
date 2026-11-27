package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.Cauldrons;
import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Base of the mod's single-fluid-container buckets (Big, Huge, and Source Bucket): the fluid
 * container rules every loader's fluid storage and the shared fluid logic apply, content naming, the
 * crafting remainder contract, lava fuel, cow milking, milk drinking, the item-model state protocol,
 * and the gesture helpers both item classes share.
 *
 * <p>The container rules are pure: {@link #acceptable} and {@link #extractable} preview a transfer
 * in {@link StoredFluid} terms without changing the stack, and {@link #insert} and {@link #extract}
 * commit exactly what the preview allowed. A finite Big or Huge Bucket stores what it takes up to
 * its capacity; a Source Bucket admits only allowlisted fluids, keeps nothing beyond its assigned
 * identity, and supplies or sinks one bucket volume per call without depleting.
 */
public abstract class FluidBucketItem extends SomeBucketItem {
    public static final int BUCKET_VOLUME_MB = 1_000;
    public static final int DRINK_DURATION_TICKS = 32;
    public static final int LAVA_BUCKET_BURN_TIME_TICKS = 20_000;

    public static final ResourceLocation CONTENT_PROPERTY = new ResourceLocation(SomeBuckets.MODID, "bb_content");
    public static final float CONTENT_EMPTY = 0.0F;
    public static final float CONTENT_FLUID = 0.1F;
    public static final float CONTENT_MILK = 0.2F;
    public static final float CONTENT_POWDER_SNOW = 0.3F;

    /** Foreign containers that held transfer leaves alone, such as buckets that drain to the wrong empty item. */
    public static final TagKey<Item> HELD_TRANSFER_EXCLUDED =
            TagKey.create(Registries.ITEM, new ResourceLocation(SomeBuckets.MODID, "held_transfer_excluded"));

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
     * Previews draining up to {@code maxMb} of whatever fluid the stack holds.
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
     * expose this through their crafting-remainder hook.
     *
     * @return the 1-count leftover, or {@link ItemStack#EMPTY} for an empty bucket
     */
    public abstract ItemStack getUnitRemainder(ItemStack stack);

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

    /** Whether held transfer may use {@code other}, the container in the hand opposite a Some Buckets bucket. */
    public static boolean allowsHeldTransfer(ItemStack other) {
        return !other.is(HELD_TRANSFER_EXCLUDED);
    }

    /**
     * Evaluates the {@link #CONTENT_PROPERTY} protocol shared by Big, Huge, and Source Bucket
     * models.
     *
     * @return exactly one of {@link #CONTENT_EMPTY}, {@link #CONTENT_FLUID},
     *         {@link #CONTENT_MILK}, or {@link #CONTENT_POWDER_SNOW}
     */
    public static float getContentProperty(ItemStack stack) {
        return switch (BucketState.getMode(stack)) {
            case FLUID -> BucketState.getStoredFluid(stack).isEmpty() ? CONTENT_EMPTY : CONTENT_FLUID;
            case MILK -> CONTENT_MILK;
            case POWDER_SNOW -> CONTENT_POWDER_SNOW;
            default -> CONTENT_EMPTY;
        };
    }

    /** Appends the stored content's suffix to the registered description id. */
    @Override
    public Component getName(ItemStack stack) {
        String baseKey = getDescriptionId();
        return switch (BucketState.getMode(stack)) {
            case FLUID -> {
                StoredFluid fluid = BucketState.getStoredFluid(stack);
                yield fluid.isEmpty() ? Component.translatable(baseKey) : resolveFluidName(baseKey, fluid);
            }
            case MILK -> Component.translatable(baseKey + ".milk");
            case POWDER_SNOW -> Component.translatable(baseKey + ".powder_snow");
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
        if (level.isClientSide) {
            // Predict vanilla's client-side milking feedback without touching the bucket.
            HeldTransfers.milkCow(cow, player, hand);
            return InteractionResult.SUCCESS;
        }
        if (!Protections.mayInteractWithEntity(level, ProtectionContext.player(player, hand), cow, stack)) {
            return InteractionResult.PASS;
        }
        if (!HeldTransfers.milkCow(cow, player, hand)) return InteractionResult.PASS;

        HeldTransfers.fillFromHand(level, player, hand, stack, bucket -> {
            addMilk(bucket, Math.min(BUCKET_VOLUME_MB, milkRoomMb(bucket)));
            return true;
        });
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.CONSUME;
    }

    /**
     * {@code placeHit} re-targeted at the position the loader's world placement of the stored fluid
     * would write, for announcing a placement before it happens. {@code placeHit} has already had
     * block targets removed ({@link #withoutBlockTarget}), so only world placement can serve it.
     */
    protected static BlockHitResult worldPlaceHit(Level level, Player player, InteractionHand hand, ItemStack stack,
                                                  BlockHitResult placeHit) {
        return withPos(placeHit, BucketOperations.get().resolveArbitraryPlaceTarget(level, placeHit, stack, player,
                hand, BucketState.getStoredFluid(stack), true));
    }

    /** {@code base} re-targeted at {@code pos}, or {@code base} unchanged if {@code pos} matches it. */
    protected static BlockHitResult withPos(BlockHitResult base, BlockPos pos) {
        return pos.equals(base.getBlockPos()) ? base
                : new BlockHitResult(base.getLocation(), base.getDirection(), pos, base.isInside());
    }

    /**
     * Reports whether a hit targets a block the item leaves to block-side handling: an exposed
     * loader fluid store, served from {@code useOn}, or, when {@code includeCauldrons}, a vanilla
     * cauldron, served by the Source Bucket's own {@code useOn} or by the cauldron interaction map
     * for Big and Huge Buckets. Vanilla dispatch posts the loader's block-interaction event before
     * either, which is where claim mods decide whether a player may use a block.
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
     */
    protected static BlockHitResult withoutBlockTarget(Level level, BlockHitResult hit, boolean includeCauldrons) {
        return isBlockTarget(level, hit, includeCauldrons)
                ? BlockHitResult.miss(hit.getLocation(), hit.getDirection(), hit.getBlockPos()) : hit;
    }

    /**
     * Lazily posts Forge's world bucket-use event when {@code hitSupplier} resolves a block hit.
     * Fabric returns without resolving the supplier, so loader-neutral item code does not pay for
     * Forge-only target selection.
     */
    @Nullable
    protected static InteractionResult beforeWorldBucketUse(Player player, Level level, ItemStack stack,
                                                            Supplier<BlockHitResult> hitSupplier) {
        return BucketOperations.get().beforeWorldBucketUse(player, level, stack, () -> {
            BlockHitResult hit = hitSupplier.get();
            return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        });
    }

    /** The water/lava/generic-fluid dynamic-name suffix shared by every fluid-mode bucket. */
    private static Component resolveFluidName(String baseKey, StoredFluid fluid) {
        if (fluid.fluid() == Fluids.WATER) {
            return Component.translatable(baseKey + ".water");
        } else if (fluid.fluid() == Fluids.LAVA) {
            return Component.translatable(baseKey + ".lava");
        } else {
            return Component.translatable(baseKey + ".fluid", BucketOperations.get().fluidDisplayName(fluid));
        }
    }

    /**
     * Clears a bucket of any content on a sneak-use against air. {@code airHit} is the caller's own
     * raytrace, shared with {@link #tryCrossHandTransfer}; only a miss clears.
     *
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
     * {@code airHit} is the caller's own {@code ClipContext.Fluid.NONE} raytrace.
     *
     * @return {@code true} iff a transfer occurred
     */
    protected static boolean tryCrossHandTransfer(Level level, Player player, InteractionHand hand, ItemStack stack,
                                                  HitResult airHit) {
        if (airHit.getType() != HitResult.Type.MISS) return false;
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        if (otherStack.isEmpty() || !allowsHeldTransfer(otherStack)) return false;
        return HeldTransfers.tryTransfer(level, player, hand, stack, otherHand, otherStack);
    }

    /**
     * Starts drinking held milk, or passes when the stack holds none the player can drink.
     */
    protected final InteractionResultHolder<ItemStack> startMilkDrink(Player player, InteractionHand hand,
                                                                      ItemStack stack) {
        if (!holdsDrinkableMilk(stack)) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public final UseAnim getUseAnimation(ItemStack stack) {
        return holdsDrinkableMilk(stack) ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public final int getUseDuration(ItemStack stack) {
        return holdsDrinkableMilk(stack) ? DRINK_DURATION_TICKS : 0;
    }

    @Override
    public final ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (holdsDrinkableMilk(stack)) finishMilkDrink(stack, level, user);
        return stack;
    }

    /*
     * Completes a milk drink of one bucket volume as vanilla's milk bucket does: the consume-item
     * criterion and item-use statistic against the still-filled stack, then the loader's milk cure
     * on the server. A bucket that drainsOnDrink loses one unit on the server.
     */
    private void finishMilkDrink(ItemStack stack, Level level, LivingEntity user) {
        if (user instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        }
        if (user instanceof Player player) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        if (!level.isClientSide) {
            BucketOperations.get().cureAsMilk(user);
            if (drainsOnDrink()) BucketState.drainFiniteContent(stack, BUCKET_VOLUME_MB);
        }
    }
}
