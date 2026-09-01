package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Supplies vanilla and loader-neutral setup and assertions to both GameTest suites. */
abstract class SharedGameTestSupport {
    static final int SHORT_TIMEOUT = 20;
    static final int WORLD_TIMEOUT = 40;

    protected SharedGameTestSupport() {}

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    static ItemStack fluid(ItemStack stack, Fluid fluid, int amount) {
        BucketState.setStoredFluid(stack, new StoredFluid(fluid, amount, null));
        return stack;
    }

    static ItemStack milk(ItemStack stack, int amount) {
        BucketState.setMilkAmount(stack, amount);
        return stack;
    }

    static ItemStack powder(ItemStack stack, int units) {
        BucketState.setPowderUnits(stack, units);
        return stack;
    }

    static void assertEmpty(ItemStack stack) {
        check(BucketState.isEmptyBucket(stack), "Expected empty bucket, got " + stack);
        check(BucketState.getMode(stack) == BucketState.Mode.NONE,
                "Expected mode none, got " + BucketState.getMode(stack));
    }

    /** Asserts the stack carries no bucket-state component and reports as an empty bucket. */
    static void assertNoBucketState(ItemStack stack, String context) {
        check(BucketState.isEmptyBucket(stack), context + ": expected empty bucket, got " + stack);
        check(!stack.has(ModDataComponentTypes.FLUID_CONTENT)
                        && !stack.has(ModDataComponentTypes.MILK_AMOUNT)
                        && !stack.has(ModDataComponentTypes.POWDER_UNITS)
                        && !stack.has(ModDataComponentTypes.CAPTURED_MOBS)
                        && !stack.has(ModDataComponentTypes.JUNK_CONTENTS),
                context + ": a bucket-state component is still present on " + stack);
    }

    static void assertFluid(ItemStack stack, Fluid fluid, int amount) {
        StoredFluid stored = BucketState.getStoredFluid(stack);
        check(BucketState.getMode(stack) == BucketState.Mode.FLUID,
                "Expected fluid mode, got " + BucketState.getMode(stack));
        check(!stored.isEmpty(), "Expected fluid, got empty StoredFluid");
        check(stored.fluid() == fluid, "Expected fluid " + fluid + ", got " + stored.fluid());
        check(stored.amount() == amount, "Expected " + amount + " mB, got " + stored.amount());
    }

    static void assertMilk(ItemStack stack, int amount) {
        check(BucketState.getMode(stack) == BucketState.Mode.MILK,
                "Expected milk mode, got " + BucketState.getMode(stack));
        check(BucketState.getAmount(stack) == amount,
                "Expected " + amount + " mB of milk, got " + BucketState.getAmount(stack));
    }

    static void assertPowder(ItemStack stack, int units) {
        check(BucketState.getMode(stack) == BucketState.Mode.POWDER_SNOW,
                "Expected powder_snow mode, got " + BucketState.getMode(stack));
        check(BucketState.getPowderUnits(stack) == units,
                "Expected " + units + " powder units, got " + BucketState.getPowderUnits(stack));
    }

    static void assertSameStack(ItemStack expected, ItemStack actual, String message) {
        boolean same = expected.getCount() == actual.getCount()
                && ItemStack.isSameItemSameComponents(expected, actual);
        check(same, message + "; expected=" + expected + ", actual=" + actual);
    }

    static CompoundTag copyCustomData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    static void updateCustomData(ItemStack stack, Consumer<CompoundTag> updater) {
        CompoundTag tag = copyCustomData(stack);
        if (tag == null) tag = new CompoundTag();
        updater.accept(tag);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    static void assertStored(GameTestHelper helper, ItemStack bucket, ItemStack... expected) {
        List<ItemStack> actual = BucketState.getStoredItems(bucket);
        check(actual.size() == expected.length,
                "Expected " + expected.length + " stored stacks, got " + actual.size() + ": " + actual);
        for (int i = 0; i < expected.length; i++) {
            assertSameStack(expected[i], actual.get(i), "Stored stack mismatch at index " + i);
        }
    }

    static BlockHitResult hit(GameTestHelper helper, BlockPos relative, Direction face) {
        BlockPos absolute = helper.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(absolute), face, absolute, false);
    }

    static Player survivalPlayer(GameTestHelper helper, BlockPos relative) {
        return serverPlayer(helper, relative);
    }

    static ServerPlayer serverPlayer(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "sb-gametest"),
                ClientInformation.createDefault());
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(relative));
        player.setPos(position.x, position.y, position.z);
        return player;
    }

    /** A synthetic player with the minimal packet connection needed by effect synchronization. */
    static ServerPlayer connectedServerPlayer(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "sb-connected-gametest"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                cookie.gameProfile(), cookie.clientInformation());

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        ServerGamePacketListenerImpl listener = new ServerGamePacketListenerImpl(
                level.getServer(), connection, player, cookie);
        connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(
                RegistryFriendlyByteBuf.decorator(level.getServer().registryAccess())), listener);

        Vec3 position = Vec3.atCenterOf(helper.absolutePos(relative));
        player.setPos(position.x, position.y, position.z);
        return player;
    }

    /** A survival player at {@code aboveTarget} looking straight down. */
    static Player survivalPlayerLookingDown(GameTestHelper helper, BlockPos aboveTarget) {
        Player player = survivalPlayer(helper, aboveTarget);
        player.setXRot(90.0F);
        return player;
    }

    /** A synthetic player aimed at a known block, with the resulting ray trace checked up front. */
    static Player survivalPlayerLookingAt(GameTestHelper helper, BlockPos playerPosition,
                                          BlockPos target) {
        Player player = survivalPlayer(helper, playerPosition);
        BlockPos absoluteTarget = helper.absolutePos(target);
        aimAt(player, Vec3.atCenterOf(absoluteTarget));
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        check(hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(absoluteTarget),
                "Player ray trace hit " + hit + " instead of " + absoluteTarget);
        return player;
    }

    /** A synthetic player aimed through a cleared vertical column, guaranteeing an air use. */
    static Player survivalPlayerLookingAtAir(GameTestHelper helper, BlockPos playerPosition) {
        for (int offset = 0; offset <= 8; offset++) {
            helper.setBlock(playerPosition.above(offset), Blocks.AIR);
        }
        Player player = survivalPlayer(helper, playerPosition);
        aimAt(player, Vec3.atCenterOf(helper.absolutePos(playerPosition.above(8))));
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        check(hit.getType() == HitResult.Type.MISS,
                "Air-use player ray trace unexpectedly hit " + hit);
        return player;
    }

    private static void aimAt(Player player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double deltaX = target.x - eye.x;
        double deltaY = target.y - eye.y;
        double deltaZ = target.z - eye.z;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double degrees = 180.0D / Math.PI;
        player.setYRot((float) (Math.atan2(deltaZ, deltaX) * degrees) - 90.0F);
        player.setXRot((float) -(Math.atan2(deltaY, horizontal) * degrees));
    }

    static <T extends Entity> T spawn(GameTestHelper helper, EntityType<T> type, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        T entity = type.create(level);
        check(entity != null, "Could not create entity " + type);
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(relative));
        entity.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        check(level.addFreshEntity(entity), "Could not add entity " + type);
        return entity;
    }

    static ItemEntity spawnItem(GameTestHelper helper, ItemStack stack, BlockPos relative) {
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(relative));
        ItemEntity entity = new ItemEntity(helper.getLevel(), position.x, position.y, position.z, stack);
        check(helper.getLevel().addFreshEntity(entity), "Could not add item entity " + stack);
        return entity;
    }

    static <T extends Entity> List<T> entities(GameTestHelper helper, Class<T> type,
                                               BlockPos relative, double radius) {
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(relative));
        return helper.getLevel().getEntitiesOfClass(type,
                new AABB(center, center).inflate(radius), Entity::isAlive);
    }

    static DispenserBlockEntity dispenser(GameTestHelper helper, BlockPos relative,
                                           Direction facing, ItemStack stack) {
        helper.setBlock(relative,
                Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing));
        DispenserBlockEntity dispenser = (DispenserBlockEntity) helper.getBlockEntity(relative);
        check(dispenser != null, "Dispenser block entity was not created");
        dispenser.setItem(0, stack);
        return dispenser;
    }

    static void triggerDispenser(GameTestHelper helper, BlockPos relative) {
        helper.pulseRedstone(relative.above(), 2L);
    }

    static void assertBlock(GameTestHelper helper, BlockPos relative, Block block) {
        Block actual = helper.getBlockState(relative).getBlock();
        check(actual == block, "Expected " + block + " at " + relative + ", got " + actual);
    }
}
