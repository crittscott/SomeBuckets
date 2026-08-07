package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.protection.ProtectionAction;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.Protections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class MBItem extends Item {
    private static final TagKey<EntityType<?>> MB_BLACKLIST =
            TagKey.create(ForgeRegistries.ENTITY_TYPES.getRegistryKey(),
                    new ResourceLocation("somebuckets", "mb_blacklist"));

    public MBItem(Properties properties) {
        super(properties);
    }

    /**
     * Whether an entity may be stored in a Mob Bucket at all, independent of what the bucket already holds.
     * Excludes players and other non-{@link Mob} living entities, types that cannot be serialized, blacklisted
     * types, and entities involved in a ride, since the other half of the pair is not part of the snapshot.
     */
    public static boolean canCapture(Entity entity) {
        if (!(entity instanceof Mob mob)) return false;
        if (mob.isRemoved()) return false;
        if (!mob.getType().canSerialize()) return false;
        if (mob.getType().is(MB_BLACKLIST)) return false;
        return !mob.isPassenger() && !mob.isVehicle();
    }

    /** Stores and removes one eligible mob after the acting player or automation is authorized. */
    public static boolean capture(ItemStack stack, Mob mob, ProtectionContext context) {
        if (!canCapture(mob) || !NBTUtil.canAcceptEntity(stack, mob.getType())) return false;
        if (!Protections.mayAct(mob.level(), context, ProtectionAction.ENTITY_INTERACT,
                mob.blockPosition(), Direction.UP, stack, mob)) return false;

        ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (entityTypeId == null) return false;

        CompoundTag entityTag = new CompoundTag();
        mob.saveWithoutId(entityTag);

        if (NBTUtil.getEntityCount(stack) == 0) {
            NBTUtil.setEntityHeader(stack, entityTypeId.toString());
        }
        NBTUtil.addEntitySnapshot(stack, entityTag);
        mob.discard();
        return true;
    }

    /** Whether a stored mob suffocates out of water and must be released into it. */
    public static boolean needsWater(Entity entity) {
        if (entity instanceof Bucketable) return true;
        return entity instanceof LivingEntity living && living.getMobType() == MobType.WATER;
    }

    /**
     * Give a water-dwelling mob somewhere to live, as a vanilla bucket of fish does: waterlog the target if it
     * accepts water, otherwise replace it with a water source. False when the position cannot hold water.
     */
    private static boolean placeWaterFor(Level level, BlockPos pos, ItemStack stack,
                                         ProtectionContext context, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.WATER)) return true;

        LiquidBlockContainer container = state.getBlock() instanceof LiquidBlockContainer liquidContainer
                ? liquidContainer : null;
        boolean canWaterlog = container != null
                && container.canPlaceLiquid(level, pos, state, Fluids.WATER);
        if (!canWaterlog && !state.canBeReplaced(Fluids.WATER)) return false;

        if (!Protections.mayAct(level, context, ProtectionAction.FLUID_EDIT, pos, face, stack, null)) {
            return false;
        }

        if (canWaterlog) {
            container.placeLiquid(level, pos, state, Fluids.WATER.defaultFluidState());
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(context.player(), GameEvent.FLUID_PLACE, pos);
            return true;
        }

        if (!state.liquid()) level.destroyBlock(pos, true);
        if (!level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL)) return false;
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(context.player(), GameEvent.FLUID_PLACE, pos);
        return true;
    }

    private static boolean isUuidInUse(ServerLevel level, UUID uuid) {
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            Entity existing = serverLevel.getEntity(uuid);
            if (existing != null && !existing.isRemoved()) return true;
        }
        return false;
    }

    /** Recreates the oldest stored mob after authorizing both the entity and any required water edit. */
    public static boolean releaseOldest(Level level, BlockPos pos, ItemStack stack,
                                        ProtectionContext context, Direction face) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        CompoundTag storedTag = NBTUtil.copyFirstEntitySnapshot(stack);
        if (storedTag.isEmpty()) return false;

        EntityType<?> entityType = NBTUtil.getCurrentEntityType(stack);
        if (entityType == null) return false;

        Entity entity = entityType.create(serverLevel);
        if (entity == null) return false;

        CompoundTag loadTag = storedTag.copy();
        entity.load(loadTag);
        while (isUuidInUse(serverLevel, entity.getUUID())) {
            entity.setUUID(UUID.randomUUID());
        }
        Vec3 spawnVec = Vec3.atCenterOf(pos);
        entity.setPos(spawnVec.x, spawnVec.y, spawnVec.z);

        if (!level.noCollision(entity)) return false;
        if (!Protections.mayAct(level, context, ProtectionAction.ENTITY_RELEASE, pos, face, stack, entity)) {
            return false;
        }
        if (needsWater(entity) && !placeWaterFor(level, pos, stack, context, face)) return false;
        if (!level.addFreshEntity(entity)) return false;
        level.gameEvent(context.player(), GameEvent.ENTITY_PLACE, pos);

        NBTUtil.removeFirstEntitySnapshot(stack);
        NBTUtil.normalizeEmptyState(stack);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int count = NBTUtil.getEntityCount(stack);
        if (count > 0) {
            EntityType<?> type = NBTUtil.getCurrentEntityType(stack);
            if (type != null) {
                tooltip.add(Component.translatable(
                        "tooltip.somebuckets.mob_bucket.contents",
                        Component.translatable(type.getDescriptionId()), count, 8));
            }
        }
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Mob mob) || !canCapture(mob)) {
            return InteractionResult.PASS;
        }

        // Check if we can accept this entity type
        if (!NBTUtil.canAcceptEntity(stack, mob.getType())) {
            return InteractionResult.PASS;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        if (!capture(stack, mob, ProtectionContext.player(player, hand))) return InteractionResult.PASS;

        // Update the ItemStack in the player's hand to reflect NBT changes
        player.setItemInHand(hand, stack);

        // Play sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 1.0F);

        return InteractionResult.sidedSuccess(false);
    }

    /* ------------------------- UI bar ------------------------- */

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return NBTUtil.getEntityCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * (float) NBTUtil.getEntityCount(stack) / 8.0f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3f76e4;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();

        // Must have stored entity to release
        if (NBTUtil.getEntityCount(stack) <= 0) {
            return InteractionResult.PASS;
        }

        // Only act on shift-right-click (matching JBItem pattern)
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        ProtectionContext protectionContext = ProtectionContext.player(player, context.getHand());
        if (!releaseOldest(level, spawnPos, stack, protectionContext, context.getClickedFace())) {
            return InteractionResult.PASS;
        }

        // Play sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 1.0F);

        return InteractionResult.sidedSuccess(false);
    }
}
