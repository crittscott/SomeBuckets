package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.item.BBItem;
import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.item.SBItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.BucketOperations.CauldronFluid;
import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Vanilla water, lava, and powder-snow cauldron transitions for Big, Huge, and Source Buckets:
 * block-state changes, protection, sound, and stat/criterion accounting. Bucket state is edited
 * through {@link BucketState} directly, since a vanilla cauldron is not modded fluid storage. A
 * finite Big or Huge Bucket is credited or debited one unit; a Source Bucket is left unchanged (an
 * empty one is assigned by {@code SBFluidLogic}).
 *
 * <p>Water and lava transitions serve loaders that do not expose vanilla cauldrons as sided fluid
 * storage; they are reached through {@link #register} and {@link BucketOperations#cauldronTake} /
 * {@link BucketOperations#cauldronPlace}. Powder-snow transitions serve every loader.
 *
 * <p>Each method simulates before checking protection and mutating, and returns whether the
 * transition happened. Mutation and side effects are skipped on the client.
 */
public final class Cauldrons {
    private Cauldrons() {}

    /**
     * Wires Big and Huge Bucket entries into the vanilla empty, water, lava, and powder-snow
     * cauldron interaction maps. Called once during mod setup by loaders that serve vanilla water
     * and lava cauldrons through this class.
     *
     * @param big8 Big Bucket item
     * @param big64 Huge Bucket item
     */
    public static void register(Item big8, Item big64) {
        for (Item item : new Item[] {big8, big64}) {
            CauldronInteraction.EMPTY.map().put(item, Cauldrons::onEmptyCauldron);
            CauldronInteraction.WATER.map().put(item, Cauldrons::onWaterCauldron);
            CauldronInteraction.LAVA.map().put(item, Cauldrons::onLavaCauldron);
            CauldronInteraction.POWDER_SNOW.map().put(item, Cauldrons::onPowderSnowCauldron);
        }
    }

    /**
     * Wires Big and Huge Bucket powder-snow entries into the vanilla empty and powder-snow cauldron
     * interaction maps. Called once during mod setup by loaders that serve vanilla water and lava
     * cauldrons as sided fluid storage.
     *
     * @param big8 Big Bucket item
     * @param big64 Huge Bucket item
     */
    public static void registerPowder(Item big8, Item big64) {
        for (Item item : new Item[] {big8, big64}) {
            CauldronInteraction.EMPTY.map().put(item, Cauldrons::onEmptyCauldronPowder);
            CauldronInteraction.POWDER_SNOW.map().put(item, Cauldrons::onPowderSnowCauldron);
        }
    }

    /** Drains one bucket-volume of water from a full water cauldron into the bucket, emptying it. */
    public static boolean takeWater(Level level, BlockPos pos, Direction face, ItemStack stack,
                                    ProtectionContext context) {
        return takeFluid(level, pos, face, stack, context, Fluids.WATER,
                fullLayeredState(Blocks.WATER_CAULDRON));
    }

    /** Drains one bucket-volume of lava from a lava cauldron into the bucket, emptying it. */
    public static boolean takeLava(Level level, BlockPos pos, Direction face, ItemStack stack,
                                   ProtectionContext context) {
        return takeFluid(level, pos, face, stack, context, Fluids.LAVA,
                Blocks.LAVA_CAULDRON.defaultBlockState());
    }

    /** Fills an empty cauldron to a full water cauldron from one bucket-volume in the bucket. */
    public static boolean placeWater(Level level, BlockPos pos, Direction face, ItemStack stack,
                                     ProtectionContext context) {
        return placeFluid(level, pos, face, stack, context, Fluids.WATER,
                fullLayeredState(Blocks.WATER_CAULDRON));
    }

    /** Converts an empty cauldron into a lava cauldron from one bucket-volume in the bucket. */
    public static boolean placeLava(Level level, BlockPos pos, Direction face, ItemStack stack,
                                    ProtectionContext context) {
        return placeFluid(level, pos, face, stack, context, Fluids.LAVA,
                Blocks.LAVA_CAULDRON.defaultBlockState());
    }

    /**
     * Source Bucket only: a full cauldron of the assigned fluid accepts nothing, but a normal place
     * gesture still reports success with the empty sound, matching placement onto an existing source
     * block.
     */
    public static boolean placeOntoFullCauldron(Level level, BlockPos pos, Direction face, ItemStack stack,
                                                CauldronFluid fluid, ProtectionContext context) {
        BlockState state = level.getBlockState(pos);
        boolean matching = fluid == CauldronFluid.WATER
                ? state.is(Blocks.WATER_CAULDRON)
                        && state.getValue(LayeredCauldronBlock.LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL
                : state.is(Blocks.LAVA_CAULDRON);
        if (!matching) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos, face, stack, null)) return false;
        if (!level.isClientSide) {
            playBucketSound(level, pos, BucketOperations.get().emptySound(BucketState.getStoredFluid(stack)));
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, pos);
            if (context.player() != null) context.player().awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return true;
    }

    /**
     * Moves one powder-snow block from a full powder-snow cauldron at {@code pos} into {@code stack},
     * leaving an empty cauldron.
     *
     * <p>On the server it debits the cauldron, credits the bucket, awards the cauldron-use and
     * item-use stats, fires the filled-bucket criterion for a player, and emits
     * {@link GameEvent#FLUID_PICKUP}; the fill sound plays on both sides.
     *
     * @param level acting level
     * @param pos cauldron position
     * @param face face to authorize against
     * @param stack bucket stack, credited one unit on success
     * @param capacityUnits the bucket tier's powder-snow capacity
     * @param context authorization identity
     * @return {@code true} when the transfer ran; {@code false} without mutation unless the cauldron
     *         is full, the bucket is empty or already in powder-snow mode below {@code capacityUnits},
     *         and protection allows the interaction
     */
    public static boolean takePowder(Level level, BlockPos pos, Direction face, ItemStack stack,
                                     int capacityUnits, ProtectionContext context) {
        if (!level.getBlockState(pos).equals(fullLayeredState(Blocks.POWDER_SNOW_CAULDRON))) return false;

        BucketState.Mode mode = BucketState.getMode(stack);
        int currentUnits = BucketState.getPowderUnits(stack);
        if (mode != BucketState.Mode.NONE
                && (mode != BucketState.Mode.POWDER_SNOW || currentUnits >= capacityUnits)) {
            return false;
        }
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            BucketState.setPowderUnits(stack,
                    (mode == BucketState.Mode.POWDER_SNOW ? currentUnits : 0) + 1);
            complete(level, pos, stack, context, Blocks.CAULDRON.defaultBlockState(), true);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_FILL_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /**
     * Moves one powder-snow block from {@code stack} into an empty cauldron at {@code pos}, filling
     * it to a full powder-snow cauldron.
     *
     * <p>On the server it debits the bucket, sets the cauldron, awards the cauldron-use and item-use
     * stats, and emits {@link GameEvent#FLUID_PLACE}; the empty sound plays on both sides.
     *
     * @param level acting level
     * @param pos cauldron position
     * @param face face to authorize against
     * @param stack bucket stack, debited one unit on success
     * @param context authorization identity
     * @return {@code true} when the transfer ran; {@code false} without mutation unless the target is
     *         an empty cauldron, the bucket is in powder-snow mode, and
     *         protection allows the interaction
     */
    public static boolean placePowder(Level level, BlockPos pos, Direction face, ItemStack stack,
                                      ProtectionContext context) {
        if (!level.getBlockState(pos).is(Blocks.CAULDRON)) return false;
        if (BucketState.getMode(stack) != BucketState.Mode.POWDER_SNOW) return false;
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            BucketState.setPowderUnits(stack, BucketState.getPowderUnits(stack) - 1);
            complete(level, pos, stack, context, fullLayeredState(Blocks.POWDER_SNOW_CAULDRON), false);
        }
        level.playSound(context.player(), pos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private static InteractionResult onEmptyCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                      InteractionHand hand, ItemStack stack) {
        ProtectionContext context = ProtectionContext.player(player, hand);
        BucketState.Mode mode = BucketState.getMode(stack);
        boolean acted;
        if (mode == BucketState.Mode.FLUID) {
            Fluid fluid = BucketState.getStoredFluid(stack).fluid();
            acted = fluid == Fluids.WATER ? placeWater(level, pos, Direction.UP, stack, context)
                    : fluid == Fluids.LAVA && placeLava(level, pos, Direction.UP, stack, context);
        } else {
            acted = mode == BucketState.Mode.POWDER_SNOW
                    && placePowder(level, pos, Direction.UP, stack, context);
        }
        return result(level, acted);
    }

    private static InteractionResult onEmptyCauldronPowder(BlockState state, Level level, BlockPos pos,
                                                            Player player, InteractionHand hand, ItemStack stack) {
        return result(level, placePowder(level, pos, Direction.UP, stack, ProtectionContext.player(player, hand)));
    }

    private static InteractionResult onWaterCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                      InteractionHand hand, ItemStack stack) {
        return result(level, takeWater(level, pos, Direction.UP, stack, ProtectionContext.player(player, hand)));
    }

    private static InteractionResult onLavaCauldron(BlockState state, Level level, BlockPos pos, Player player,
                                                     InteractionHand hand, ItemStack stack) {
        return result(level, takeLava(level, pos, Direction.UP, stack, ProtectionContext.player(player, hand)));
    }

    private static InteractionResult onPowderSnowCauldron(BlockState state, Level level, BlockPos pos,
                                                           Player player, InteractionHand hand, ItemStack stack) {
        int capacityUnits = ((BBItem) stack.getItem()).getCapacityUnits();
        return result(level, takePowder(level, pos, Direction.UP, stack, capacityUnits,
                ProtectionContext.player(player, hand)));
    }

    private static InteractionResult result(Level level, boolean acted) {
        return acted ? (level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER)
                : InteractionResult.PASS;
    }

    private static boolean takeFluid(Level level, BlockPos pos, Direction face, ItemStack stack,
                                     ProtectionContext context, Fluid fluid, BlockState fullState) {
        if (!level.getBlockState(pos).equals(fullState)) return false;
        if (stack.getItem() instanceof BBItem
                && !BBItem.canAcceptFluidUnit(stack, unit(fluid))) return false;
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            if (stack.getItem() instanceof BBItem) creditFinite(stack, fluid);
            complete(level, pos, stack, context, Blocks.CAULDRON.defaultBlockState(), true);
            playBucketSound(level, pos, BucketOperations.get().fillSound(unit(fluid)));
        }
        return true;
    }

    private static boolean placeFluid(Level level, BlockPos pos, Direction face, ItemStack stack,
                                      ProtectionContext context, Fluid fluid, BlockState fullState) {
        if (!level.getBlockState(pos).is(Blocks.CAULDRON)) return false;
        if (stack.getItem() instanceof BBItem && !holdsPlaceableUnit(stack, fluid)) return false;
        if (stack.getItem() instanceof SBItem
                && !BucketState.getStoredFluid(stack).fluid().isSame(fluid)) return false;
        if (!mayInteract(level, pos, face, stack, context)) return false;

        if (!level.isClientSide) {
            if (stack.getItem() instanceof BBItem) {
                BucketState.drainFiniteContent(stack, FluidBucketItem.BUCKET_VOLUME_MB);
            }
            complete(level, pos, stack, context, fullState, false);
            playBucketSound(level, pos, BucketOperations.get().emptySound(unit(fluid)));
        }
        return true;
    }

    private static boolean holdsPlaceableUnit(ItemStack stack, Fluid fluid) {
        StoredFluid current = BucketState.getStoredFluid(stack);
        return BucketState.getMode(stack) == BucketState.Mode.FLUID
                && current.fluid().isSame(fluid)
                && current.amount() >= FluidBucketItem.BUCKET_VOLUME_MB;
    }

    private static void creditFinite(ItemStack stack, Fluid fluid) {
        StoredFluid current = BucketState.getStoredFluid(stack);
        boolean merging = BucketState.getMode(stack) == BucketState.Mode.FLUID;
        BucketState.setStoredFluid(stack, merging
                ? current.withAmount(current.amount() + FluidBucketItem.BUCKET_VOLUME_MB)
                : unit(fluid));
    }

    private static StoredFluid unit(Fluid fluid) {
        return new StoredFluid(fluid, FluidBucketItem.BUCKET_VOLUME_MB);
    }

    private static boolean mayInteract(Level level, BlockPos pos, Direction face, ItemStack stack,
                                       ProtectionContext context) {
        return Protections.mayAct(level, context, ProtectionAction.BLOCK_INTERACT, pos, face, stack, null);
    }

    /* Server-authoritative broadcast that also reaches the acting player. */
    private static void playBucketSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void complete(Level level, BlockPos pos, ItemStack stack, ProtectionContext context,
                                 BlockState resultState, boolean pickup) {
        level.setBlock(pos, resultState, Block.UPDATE_ALL);
        Player player = context.player();
        if (player != null) {
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            if (pickup && player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, stack);
            }
        }
        level.gameEvent(player, pickup ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, pos);
    }

    private static BlockState fullLayeredState(Block block) {
        return block.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
    }
}
