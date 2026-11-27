package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import com.github.crittscott.somebuckets.interaction.HeldTransfers;
import com.github.crittscott.somebuckets.mixin.common.MobBucketItemAccessor;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.protection.Protections;
import com.github.crittscott.somebuckets.util.BucketState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FIFO storage for full mob snapshots, limited to one exact entity type per bucket.
 * Capture appends an eligible live mob only after authorization; release recreates the oldest
 * snapshot and removes it from storage only after the entity enters the world. Held use moves one
 * mob between this bucket and another Mob Bucket or a mob bucket in the other hand.
 * {@link #FILLED_PROPERTY} exposes the empty-versus-occupied item-model state.
 */
public class MBItem extends SomeBucketItem {
    public static final int MAX_MOBS = 8;
    /**
     * Largest network encoding of one captured mob, in bytes. Keeps a full bucket, and a shulker box
     * of full buckets, below the client's per-item NBT limit, and so also stops a mob carrying a
     * filled Mob Bucket from nesting buckets without bound.
     */
    public static final int MAX_SNAPSHOT_BYTES = 8 * 1024;
    public static final ResourceLocation FILLED_PROPERTY =
            new ResourceLocation(SomeBuckets.MODID, "filled");
    public static final float MODEL_EMPTY = 0.0F;
    public static final float MODEL_FILLED = 1.0F;

    private static final TagKey<EntityType<?>> UNCAPTURABLE =
            TagKey.create(Registries.ENTITY_TYPE,
                    new ResourceLocation(SomeBuckets.MODID, "uncapturable"));

    public MBItem(Properties properties) {
        super(properties.rarity(Rarity.RARE));
    }

    /**
     * Evaluates the {@link #FILLED_PROPERTY} protocol for the Mob Bucket model.
     *
     * @return {@link #MODEL_EMPTY} when no snapshot is stored, otherwise {@link #MODEL_FILLED}
     */
    public static float getFilledProperty(ItemStack stack) {
        return BucketState.getEntityCount(stack) > 0 ? MODEL_FILLED : MODEL_EMPTY;
    }

    /**
     * Whether an entity may be stored in a Mob Bucket at all, independent of what the bucket already
     * holds. Excludes players and other non-{@link Mob} living entities, types that cannot be
     * serialized, uncapturable types, and entities involved in a ride or a leash, since the other
     * half of the pair is not part of the snapshot.
     */
    public static boolean canCapture(Entity entity) {
        if (!(entity instanceof Mob mob)) return false;
        if (mob.isRemoved()) return false;
        if (!canStoreType(mob.getType())) return false;
        return !mob.isPassenger() && !mob.isVehicle() && !mob.isLeashed();
    }

    /** Whether an entity type may be stored at all: serializable and not in the uncapturable tag. */
    public static boolean canStoreType(EntityType<?> entityType) {
        return entityType.canSerialize() && !entityType.is(UNCAPTURABLE);
    }

    /**
     * Whether {@code actor} may take {@code mob} away from its owner. An owned mob, such as a tamed
     * pet or horse, may be captured only by its owning player; automation captures no owned mob.
     *
     * @param actor acting real player, or {@code null} for automation
     */
    public static boolean mayCaptureAs(Mob mob, @Nullable Player actor) {
        if (!(mob instanceof OwnableEntity ownable)) return true;
        UUID owner = ownable.getOwnerUUID();
        return owner == null || (actor != null && owner.equals(actor.getUUID()));
    }

    /** Whether the bucket has room for another snapshot of the exact stored entity type. */
    public static boolean canAccept(ItemStack stack, EntityType<?> entityType) {
        int count = BucketState.getEntityCount(stack);
        if (count >= MAX_MOBS) return false;
        return count == 0 || BucketState.getCurrentEntityType(stack) == entityType;
    }

    /**
     * Captures one eligible, type-compatible mob after authorizing the supplied interaction face.
     * A water-dwelling mob also takes the water source block it occupies, so a release followed by
     * a recapture cannot generate a free-standing water block. A mob whose saved data would exceed
     * {@link #MAX_SNAPSHOT_BYTES} is refused, and an acting player is told on the action bar. A
     * successful capture plays the mob's capture sound at its position.
     *
     * @return {@code true} only after the snapshot is appended and the live mob is discarded;
     *         {@code false} leaves the mob, bucket, and world unchanged
     */
    public static boolean capture(ItemStack stack, Mob mob, ProtectionContext context, Direction face) {
        if (!canCapture(mob) || !canAccept(stack, mob.getType())) return false;
        if (!mayCaptureAs(mob, context.player())) return false;
        Level level = mob.level();
        BlockPos pos = mob.blockPosition();
        if (!Protections.mayInteractWithEntity(level, context, mob, stack)) return false;

        CompoundTag entityTag = new CompoundTag();
        mob.saveWithoutId(entityTag);
        if (encodedSize(entityTag) > MAX_SNAPSHOT_BYTES) {
            if (context.player() != null) {
                context.player().displayClientMessage(
                        Component.translatable("message.somebuckets.mob_bucket.too_large"), true);
            }
            return false;
        }
        if (needsWater(mob) && !removeSourceWaterAt(level, pos, stack, context, face)) return false;

        SoundEvent captureSound = pickupSound(mob);
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());

        BucketState.addEntitySnapshot(stack, entityTypeId.toString(), entityTag);
        mob.discard();
        level.playSound(null, pos, captureSound, SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (context.player() instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, stack);
        }
        return true;
    }

    /* The size of tag as item packets send it, the measure the client's NBT limit applies to. */
    private static int encodedSize(CompoundTag tag) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeNbt(tag);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    /* A Bucketable mob's own pickup sound; every other mob uses the generic capture sound. */
    private static SoundEvent pickupSound(Mob mob) {
        return mob instanceof Bucketable bucketable ? bucketable.getPickupSound() : SoundEvents.SLIME_ATTACK;
    }

    /*
     * The mob's own bucket: the one MobBucketLinks names for its type, otherwise a Bucketable mob's
     * own bucket, otherwise empty.
     */
    private static ItemStack ownBucket(Entity entity) {
        Item linked = MobBucketLinks.BUCKET_BY_TYPE.get(entity.getType());
        if (linked != null) return new ItemStack(linked);
        return entity instanceof Bucketable bucketable ? bucketable.getBucketItemStack() : ItemStack.EMPTY;
    }

    /*
     * Plays the empty sound of the mob's own bucket at pos, through that bucket so loader and modded
     * overrides apply; a mob without a mob bucket uses the generic release sound.
     */
    private static void playReleaseSound(ServerLevel level, BlockPos pos, Entity entity) {
        if (ownBucket(entity).getItem() instanceof MobBucketItem bucket) {
            ((MobBucketItemAccessor) bucket).somebuckets$playEmptySound(null, level, pos);
        } else {
            level.playSound(null, pos, SoundEvents.SLIME_JUMP, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }

    /*
     * Marks a mob a player moved by bucket the way vanilla marks one emptied from a mob bucket, so it
     * does not despawn: a Bucketable mob as from a bucket, any other mob as persistent.
     */
    private static void markMovedByBucket(Mob mob) {
        if (mob instanceof Bucketable bucketable) {
            bucketable.setFromBucket(true);
        } else {
            mob.setPersistenceRequired();
        }
    }

    /** Whether the entity is {@link Bucketable} or is a living mob classified as {@link MobType#WATER}. */
    public static boolean needsWater(Entity entity) {
        if (entity instanceof Bucketable) return true;
        return entity instanceof LivingEntity living && living.getMobType() == MobType.WATER;
    }

    /* Gives a water-dwelling mob the exact-target water placement used by a vanilla bucket of fish. */
    private static boolean placeWaterFor(Level level, BlockPos pos, ItemStack stack,
                                         ProtectionContext context, Direction face) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) return true;
        return FluidTransactions.emptyFluid(level, context, stack, pos, face, false,
                FluidTransactions.WATER_UNIT, () -> true);
    }

    /*
     * Removes the water source block at pos, the block the captured mob occupies, through the
     * block's pickup contract, authorized as a block break. A non-source or non-water block is left
     * alone. Returns true when there was nothing to remove or removal was authorized and applied.
     */
    private static boolean removeSourceWaterAt(Level level, BlockPos pos, ItemStack stack,
                                               ProtectionContext context, Direction face) {
        if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource()) return true;
        if (!Protections.mayRemove(level, context, pos, face, stack)) return false;
        return FluidTransactions.takeWorldFluid(level, pos, FluidTransactions.WATER_UNIT, context.player());
    }

    private static boolean isUuidInUse(ServerLevel level, UUID uuid) {
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            Entity existing = serverLevel.getEntity(uuid);
            if (existing != null && !existing.isRemoved()) return true;
        }
        return false;
    }

    /**
     * Attempts to recreate and release the oldest stored mob at {@code pos}.
     *
     * <p>A stored entity-type header that no longer resolves to a registered {@link EntityType}
     * (for example after the mob's mod is removed) is a no-op that preserves the entry. A recreated
     * entity that no longer passes {@link #canCapture(Entity)}, for example because its type was
     * added to the uncapturable tag after capture, is likewise refused and preserved.
     *
     * <p>The recreated entity retains its saved UUID unless that UUID belongs to another loaded
     * entity in any server level, in which case a fresh UUID is chosen. Collision failure tells an
     * acting player on the action bar that there is no room. Collision failure and entity-release or
     * required-fluid protection denial preserve the stored entry and destination. The entry is
     * removed only after {@link ServerLevel#addFreshEntity(Entity)} succeeds. Required water is
     * committed before that final insertion; if another mod rejects insertion afterward, the
     * snapshot remains stored but the water is not rolled back. A released mob is marked as moved by
     * the player, as from a vanilla mob bucket, so it does not despawn. A successful release plays
     * the empty sound of the mob's own bucket at {@code pos}, or a generic release sound for a mob
     * without one; a player's release reaches here through {@code useOn}, whose success vanilla
     * counts as an item use.
     *
     * @return {@code true} only after the entity enters the world and the oldest snapshot is removed
     */
    public static boolean releaseOldest(ServerLevel level, BlockPos pos, ItemStack stack,
                                        ProtectionContext context, Direction face) {
        CompoundTag storedTag = BucketState.copyFirstEntitySnapshot(stack);
        if (storedTag.isEmpty()) return false;

        EntityType<?> entityType = BucketState.getCurrentEntityType(stack);
        if (entityType == null) return false;
        Entity entity = entityType.create(level);
        if (entity == null) return false;

        entity.load(storedTag.copy());
        if (!canCapture(entity)) return false;
        markMovedByBucket((Mob) entity);
        while (isUuidInUse(level, entity.getUUID())) {
            entity.setUUID(UUID.randomUUID());
        }
        Vec3 spawnVec = Vec3.atCenterOf(pos);
        entity.setPos(spawnVec.x, spawnVec.y, spawnVec.z);

        if (!level.noCollision(entity)) {
            if (context.player() != null) {
                context.player().displayClientMessage(
                        Component.translatable("message.somebuckets.mob_bucket.no_room"), true);
            }
            return false;
        }
        if (!Protections.mayInteractWithBlock(level, context, pos, face, stack)) return false;
        if (needsWater(entity) && !placeWaterFor(level, pos, stack, context, face)) return false;
        if (!level.addFreshEntity(entity)) return false;
        level.gameEvent(context.player(), GameEvent.ENTITY_PLACE, pos);
        playReleaseSound(level, pos, entity);

        BucketState.removeFirstEntitySnapshot(stack);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int count = BucketState.getEntityCount(stack);
        if (count > 0) {
            EntityType<?> type = BucketState.getCurrentEntityType(stack);
            if (type != null) {
                tooltip.add(Component.translatable(
                        "tooltip.somebuckets.mob_bucket.contents",
                        Component.translatable(type.getDescriptionId()), count, MAX_MOBS));
            }
        }
    }

    /**
     * Captures the target mob into the bucket with its full state, after the first capture accepting
     * only the same entity type. The client reports predicted success; the server runs the authorized
     * capture. A stack of empty buckets fills one.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Mob mob) || !canCapture(mob) || !mayCaptureAs(mob, player)) {
            return InteractionResult.PASS;
        }
        if (!canAccept(stack, mob.getType())) return InteractionResult.PASS;

        Level level = player.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ProtectionContext context = ProtectionContext.player(player, hand);
        if (!HeldTransfers.fillFromHand(level, player, hand, stack,
                bucket -> capture(bucket, mob, context, Direction.UP))) {
            return InteractionResult.PASS;
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Lets a held Mob Bucket capture before the target's own right-click reaction, such as a
     * villager opening trades or a horse being mounted, which would otherwise consume the click.
     * Each loader calls this from its entity-interaction event after listeners that refuse the
     * interaction, such as claim mods, have run. A spectator cannot interact with entities, and the
     * automation player's interaction events are protection checks rather than uses, so both pass.
     *
     * @return the capture result when it consumed the action, otherwise {@code PASS}
     */
    public static InteractionResult preemptInteraction(Player player, InteractionHand hand, Entity target) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator() || Protections.isAutomationActor(player)
                || !(stack.getItem() instanceof MBItem bucket) || !(target instanceof LivingEntity living)) {
            return InteractionResult.PASS;
        }
        InteractionResult result = bucket.interactLivingEntity(stack, player, living, hand);
        return result.consumesAction() ? result : InteractionResult.PASS;
    }

    /**
     * Moves one mob between this bucket and a mob container in the other hand. With another Mob
     * Bucket, this bucket gives its oldest mob when the other accepts it, and otherwise takes the
     * other's oldest mob; either receiver applies the type and capacity rules of capture. A held
     * water-based {@link MobBucketItem} empties its mob into this bucket and becomes a water bucket;
     * a water bucket receives this bucket's oldest mob when that mob has a water-based mob bucket.
     * Every direction changes only the two held stacks, so no world protection applies. A stack of
     * empty Mob Buckets on the receiving side fills one.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);

        if (other.getItem() instanceof MBItem) {
            boolean give = canMoveOldest(stack, other);
            if (!give && !canMoveOldest(other, stack)) return InteractionResultHolder.pass(stack);
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            if (give) {
                moveOldest(level, player, stack, otherHand, other);
            } else {
                moveOldest(level, player, other, hand, stack);
            }
            return finishExchange(level, player, hand, !give);
        }

        EntityType<?> intakeType = heldIntakeType(level, stack, other);
        ItemStack output = intakeType == null ? prepareHeldOutput(level, stack, other) : null;
        if (intakeType == null && output == null) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        if (intakeType != null) {
            CompoundTag snapshot = buildHeldIntake((ServerLevel) level, player, other, intakeType);
            if (snapshot == null) return InteractionResultHolder.pass(stack);
            receive(level, player, hand, stack, BuiltInRegistries.ENTITY_TYPE.getKey(intakeType).toString(),
                    snapshot);
            player.setItemInHand(otherHand, new ItemStack(Items.WATER_BUCKET));
        } else {
            BucketState.removeFirstEntitySnapshot(stack);
            player.setItemInHand(otherHand, output);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, output);
            }
        }
        return finishExchange(level, player, hand, intakeType != null);
    }

    /* Awards the use statistic and plays the intake or output sound for a completed held exchange. */
    private InteractionResultHolder<ItemStack> finishExchange(Level level, Player player, InteractionHand hand,
                                                              boolean intake) {
        player.awardStat(Stats.ITEM_USED.get(this));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                intake ? SoundEvents.SLIME_ATTACK : SoundEvents.SLIME_JUMP,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    /* Whether Mob Bucket from holds a mob that Mob Bucket to may receive. */
    private static boolean canMoveOldest(ItemStack from, ItemStack to) {
        EntityType<?> entityType = BucketState.getCurrentEntityType(from);
        return entityType != null && BucketState.getEntityCount(from) > 0
                && canStoreType(entityType) && canAccept(to, entityType);
    }

    /* Moves from's oldest snapshot unchanged into the Mob Bucket held in toHand. */
    private static void moveOldest(Level level, Player player, ItemStack from, InteractionHand toHand, ItemStack to) {
        EntityType<?> entityType = BucketState.getCurrentEntityType(from);
        String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
        receive(level, player, toHand, to, entityTypeId, BucketState.copyFirstEntitySnapshot(from));
        BucketState.removeFirstEntitySnapshot(from);
    }

    /* Appends a snapshot to the Mob Bucket held in hand, filling one from a stack of empties. */
    private static void receive(Level level, Player player, InteractionHand hand, ItemStack held,
                                String entityTypeId, CompoundTag snapshot) {
        HeldTransfers.fillFromHand(level, player, hand, held, bucket -> {
            BucketState.addEntitySnapshot(bucket, entityTypeId, snapshot);
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, bucket);
            }
            return true;
        });
    }

    /**
     * The mob type a held water-based {@link MobBucketItem} would give this bucket, without changing
     * either stack: the one {@link MobBucketLinks} names for the bucket, otherwise the bucket's
     * declared type. Decided the same way on both sides; only the server builds the snapshot.
     *
     * @return the type, or {@code null} when this bucket cannot take the mob
     */
    @Nullable
    private static EntityType<?> heldIntakeType(Level level, ItemStack stack, ItemStack other) {
        if (other.getCount() != 1) return null;
        BucketOperations.MobBucketContents contents = BucketOperations.get().mobBucketContents(other);
        if (contents == null || !contents.fluid().isSame(Fluids.WATER)) return null;
        EntityType<?> entityType = MobBucketLinks.TYPE_BY_BUCKET.getOrDefault(other.getItem(), contents.entityType());
        if (!canStoreType(entityType) || !canAccept(stack, entityType)) return null;
        return entityType.create(level) instanceof Mob ? entityType : null;
    }

    /**
     * Builds the mob in a held water-based {@link MobBucketItem} as emptying that bucket into the
     * world does: created as a bucket spawn, which runs its spawn initialization and applies the
     * bucket's name and entity data, then loaded from the bucket's own format for a
     * {@link Bucketable} mob, or from vanilla's default bucket data for any other mob, and marked as
     * moved by bucket. Changes neither stack.
     *
     * @return the snapshot to store, or {@code null} when the mob could not be created
     */
    @Nullable
    private static CompoundTag buildHeldIntake(ServerLevel level, Player player, ItemStack other,
                                               EntityType<?> entityType) {
        CompoundTag bucketTag = other.hasTag() ? other.getTag().copy() : new CompoundTag();
        if (!(createAsBucketSpawn(entityType, level, player, other, bucketTag) instanceof Mob mob)) return null;
        if (mob instanceof Bucketable bucketable) {
            bucketable.loadFromBucketTag(bucketTag);
        } else {
            Bucketable.loadDefaultDataFromBucketTag(mob, bucketTag);
        }
        markMovedByBucket(mob);

        CompoundTag snapshot = new CompoundTag();
        mob.saveWithoutId(snapshot);
        return snapshot;
    }

    /* Creates, without adding to the world, the entity a bucket spawn of bucket at the player makes. */
    @Nullable
    private static <T extends Entity> T createAsBucketSpawn(EntityType<T> entityType, ServerLevel level,
                                                            Player player, ItemStack bucket, CompoundTag bucketTag) {
        return entityType.create(level, bucketTag, EntityType.createDefaultStackConfig(level, bucket, player),
                player.blockPosition(), MobSpawnType.BUCKET, false, false);
    }

    /**
     * Builds the mob bucket that a held water bucket becomes when it receives this bucket's oldest
     * mob, without changing either stack: the bucket {@link MobBucketLinks} names for the mob's type,
     * otherwise a {@link Bucketable} mob's own bucket. The result keeps only what that bucket's
     * format records: the mob's own for a {@link Bucketable} mob, otherwise vanilla's default data.
     *
     * @return the filled mob bucket, or {@code null} when the oldest mob has no water-based bucket
     */
    @Nullable
    private static ItemStack prepareHeldOutput(Level level, ItemStack stack, ItemStack other) {
        if (!other.is(Items.WATER_BUCKET) || other.getCount() != 1) return null;
        EntityType<?> entityType = BucketState.getCurrentEntityType(stack);
        CompoundTag storedTag = BucketState.copyFirstEntitySnapshot(stack);
        if (entityType == null || storedTag.isEmpty()) return null;
        if (!(entityType.create(level) instanceof Mob mob)) return null;

        mob.load(storedTag);
        ItemStack filled = ownBucket(mob);
        BucketOperations.MobBucketContents contents = BucketOperations.get().mobBucketContents(filled);
        if (contents == null || !contents.fluid().isSame(Fluids.WATER)) return null;
        if (mob instanceof Bucketable bucketable) {
            bucketable.saveToBucketTag(filled);
        } else {
            Bucketable.saveDefaultDataToBucketTag(mob, filled);
        }
        return filled;
    }

    /*
     * Shipped links between mob types and other mods' water-based mob buckets that vanilla's
     * Bucketable contract cannot express: a bucket for a mob that is not Bucketable, or one whose
     * declared entity type is not the mob it holds. Read from the mod jar on first use, after
     * registries are complete, identically on server and client; entries naming an absent mob or
     * item are skipped. Each mob type and each bucket appears at most once.
     */
    private static final class MobBucketLinks {
        static final Map<EntityType<?>, Item> BUCKET_BY_TYPE = new HashMap<>();
        static final Map<Item, EntityType<?>> TYPE_BY_BUCKET = new HashMap<>();

        static {
            InputStream input = MBItem.class.getResourceAsStream("/somebuckets/mob_bucket_items.json");
            if (input == null) throw new IllegalStateException("Missing mob bucket item links");
            JsonObject links;
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                links = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read mob bucket item links", exception);
            }
            for (Map.Entry<String, JsonElement> link : links.entrySet()) {
                Optional<EntityType<?>> entityType =
                        BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(link.getKey()));
                Optional<Item> bucket =
                        BuiltInRegistries.ITEM.getOptional(new ResourceLocation(link.getValue().getAsString()));
                if (entityType.isEmpty() || bucket.isEmpty()) continue;
                if (BUCKET_BY_TYPE.put(entityType.get(), bucket.get()) != null
                        || TYPE_BY_BUCKET.put(bucket.get(), entityType.get()) != null) {
                    throw new IllegalStateException("Duplicate mob bucket item link " + link.getKey());
                }
            }
        }
    }

    /* ------------------------- UI bar ------------------------- */

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BucketState.getEntityCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return barWidth(BucketState.getEntityCount(stack), MAX_MOBS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return DEFAULT_BUCKET_BAR_COLOR;
    }

    /**
     * On a sneak-use against a block, releases the oldest stored mob into the space adjacent to the
     * clicked face. A non-sneaking use or an empty bucket passes. Client-side play is prediction; the
     * server authorizes the release and recreates the entity.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();

        if (BucketState.getEntityCount(stack) <= 0) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        ProtectionContext protectionContext = ProtectionContext.player(player, context.getHand());
        if (!releaseOldest((ServerLevel) level, spawnPos, stack, protectionContext, context.getClickedFace())) {
            return InteractionResult.PASS;
        }
        return InteractionResult.CONSUME;
    }
}
