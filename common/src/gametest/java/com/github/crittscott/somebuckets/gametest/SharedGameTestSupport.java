package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Supplies vanilla and loader-neutral setup and assertions to both GameTest suites. */
abstract class SharedGameTestSupport {
    static final int SHORT_TIMEOUT = 20;
    static final int WORLD_TIMEOUT = 40;

    protected SharedGameTestSupport() {}

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    static ItemStack fluid(ItemStack stack, Fluid fluid, int amount) {
        NBTUtil.setStoredFluid(stack, new StoredFluid(fluid, amount, null));
        return stack;
    }

    static ItemStack milk(ItemStack stack, int amount) {
        NBTUtil.setMilkAmount(stack, amount);
        return stack;
    }

    static ItemStack powder(ItemStack stack, int units) {
        NBTUtil.setPowderUnits(stack, units);
        return stack;
    }

    static void assertEmpty(ItemStack stack) {
        check(NBTUtil.isEmptyBucket(stack), "Expected empty bucket, got " + stack.getTag());
        check(NBTUtil.getMode(stack) == NBTUtil.Mode.NONE,
                "Expected mode none, got " + NBTUtil.getMode(stack));
    }

    static void assertFluid(ItemStack stack, Fluid fluid, int amount) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        check(NBTUtil.getMode(stack) == NBTUtil.Mode.FLUID,
                "Expected fluid mode, got " + NBTUtil.getMode(stack));
        check(!stored.isEmpty(), "Expected fluid, got empty StoredFluid");
        check(stored.fluid() == fluid, "Expected fluid " + fluid + ", got " + stored.fluid());
        check(stored.amount() == amount, "Expected " + amount + " mB, got " + stored.amount());
    }

    static void assertMilk(ItemStack stack, int amount) {
        check(NBTUtil.getMode(stack) == NBTUtil.Mode.MILK,
                "Expected milk mode, got " + NBTUtil.getMode(stack));
        check(NBTUtil.getAmount(stack) == amount,
                "Expected " + amount + " mB of milk, got " + NBTUtil.getAmount(stack));
    }

    static void assertPowder(ItemStack stack, int units) {
        check(NBTUtil.getMode(stack) == NBTUtil.Mode.POWDER_SNOW,
                "Expected powder_snow mode, got " + NBTUtil.getMode(stack));
        check(NBTUtil.getPowderUnits(stack) == units,
                "Expected " + units + " powder units, got " + NBTUtil.getPowderUnits(stack));
    }

    static void assertSameStack(ItemStack expected, ItemStack actual, String message) {
        boolean same = expected.getItem() == actual.getItem()
                && expected.getCount() == actual.getCount()
                && Objects.equals(expected.getTag(), actual.getTag());
        check(same, message + "; expected=" + expected + " " + expected.getTag()
                + ", actual=" + actual + " " + actual.getTag());
    }

    static void assertStored(ItemStack bucket, ItemStack... expected) {
        List<ItemStack> actual = NBTUtil.getStoredItems(bucket);
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
        Player player = helper.makeMockSurvivalPlayer();
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(relative));
        player.setPos(position.x, position.y, position.z);
        return player;
    }

    static RecordingServerPlayer serverPlayer(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        RecordingServerPlayer player = new RecordingServerPlayer(level);
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(relative));
        player.setPos(position.x, position.y, position.z);
        return player;
    }

    /**
     * A connectionless server player that records the messages the server would display to it,
     * since a real one would send them over its absent network connection.
     */
    static final class RecordingServerPlayer extends ServerPlayer {
        final List<Component> actionBarMessages = new ArrayList<>();
        final List<Component> chatMessages = new ArrayList<>();

        private RecordingServerPlayer(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "sb-gametest"));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            (actionBar ? actionBarMessages : chatMessages).add(message);
        }
    }

    /** A survival player at {@code aboveTarget} looking straight down. */
    static Player survivalPlayerLookingDown(GameTestHelper helper, BlockPos aboveTarget) {
        Player player = survivalPlayer(helper, aboveTarget);
        player.setXRot(90.0F);
        return player;
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

    /** A loader protection event that a test listener can refuse, standing in for a claim mod. */
    enum ProtectionEvent { BREAK, PLACE, BLOCK_INTERACT, ENTITY_INTERACT, PICKUP }

    /** Decides whether the test listener refuses one protection event. */
    @FunctionalInterface
    interface ProtectionRefusal {
        boolean refuses(ProtectionEvent event, Player actor, BlockPos pos);
    }

    /** Scope of an installed {@link ProtectionRefusal}; closing it stops refusing. */
    interface RefusalScope extends AutoCloseable {
        @Override
        void close();
    }

    private static final List<ProtectionRefusal> PROTECTION_REFUSALS = new CopyOnWriteArrayList<>();

    /**
     * Refuses the loader protection events {@code refusal} selects until the returned scope closes.
     * Each loader's test listeners, registered once on its event buses, consult every open scope,
     * since tests in a batch run concurrently and a scope may stay open across ticks.
     */
    static RefusalScope refuseProtection(ProtectionRefusal refusal) {
        GameTestSupport.installProtectionListeners();
        PROTECTION_REFUSALS.add(refusal);
        return () -> PROTECTION_REFUSALS.remove(refusal);
    }

    /** Called by the loader test listeners for every protection event they observe. */
    static boolean refuses(ProtectionEvent event, @Nullable Player actor, BlockPos pos) {
        if (actor == null) return false;
        for (ProtectionRefusal refusal : PROTECTION_REFUSALS) {
            if (refusal.refuses(event, actor, pos)) return true;
        }
        return false;
    }

    /** Whether {@code actor} is the dispenser automation player, standing at {@code source}. */
    static boolean isDispenserActor(Player actor, BlockPos source) {
        return "[SomeBuckets]".equals(actor.getGameProfile().getName())
                && BlockPos.containing(actor.position()).equals(source);
    }
}
