package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

public class MBItem extends Item {
    private static final TagKey<EntityType<?>> MB_BLACKLIST =
            TagKey.create(ForgeRegistries.ENTITY_TYPES.getRegistryKey(),
                    new ResourceLocation("somebuckets", "mb_blacklist"));

    public MBItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int count = NBTUtil.getEntityCount(stack);
        if (count > 0) {
            EntityType<?> type = NBTUtil.getCurrentEntityType(stack);
            if (type != null) {
                tooltip.add(Component.literal(Component.translatable(type.getDescriptionId()).getString() + " " + count + "/8"));
            }
        }
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // Check if we can accept this entity type
        if (!NBTUtil.canAcceptEntity(stack, target.getType())) {
            return InteractionResult.PASS;
        }

        // Check blacklist (bosses, etc.)
        if (target.getType().is(MB_BLACKLIST)) {
            return InteractionResult.PASS;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        // Save entity data without ID (proper method for recreation)
        CompoundTag entityTag = new CompoundTag();
        target.saveWithoutId(entityTag);

        // Set header on first entity
        if (NBTUtil.getEntityCount(stack) == 0) {
            String entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
            NBTUtil.setEntityHeader(stack, entityTypeId);
        }

        // Add snapshot
        NBTUtil.addEntitySnapshot(stack, entityTag);

        // Update the ItemStack in the player's hand to reflect NBT changes
        player.setItemInHand(hand, stack);

        // Remove entity from world
        target.discard();

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

        // Get spawn position
        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 spawnVec = Vec3.atCenterOf(spawnPos);

        // Retrieve entity data
        CompoundTag entityTag = NBTUtil.removeFirstEntitySnapshot(stack);
        if (entityTag.isEmpty()) {
            return InteractionResult.PASS;
        }

        // Get entity type for recreation
        String entityTypeId = stack.getOrCreateTag().getString(NBTUtil.ENTITY_TYPE);
        if (entityTypeId.isEmpty()) {
            return InteractionResult.PASS;
        }

        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityTypeId));
        if (entityType == null) {
            return InteractionResult.PASS;
        }

        // Create entity first, then load data (matching working MobBucketItem pattern)
        Entity entity = entityType.create(level);
        if (entity == null) {
            // Failed to create - put the entity back
            NBTUtil.addEntitySnapshot(stack, entityTag);
            return InteractionResult.PASS;
        }

        // Remove UUID to avoid conflicts, then load data
        entityTag.remove("UUID");
        entity.load(entityTag);
        entity.setPos(spawnVec.x, spawnVec.y, spawnVec.z);

        // Check if space is clear
        if (!level.noCollision(entity)) {
            // Space too small - put the entity back
            NBTUtil.addEntitySnapshot(stack, entityTag);
            return InteractionResult.PASS;
        }

        // Add to world
        level.addFreshEntity(entity);

        // Normalize empty state
        NBTUtil.normalizeEmptyState(stack);

        // Play sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 1.0F);

        return InteractionResult.sidedSuccess(false);
    }
}
