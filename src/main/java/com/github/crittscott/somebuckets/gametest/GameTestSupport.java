package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.register.ModItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Objects;

final class GameTestSupport {
    static final String TEMPLATE = "empty_9x6x9";
    static final int SHORT_TIMEOUT = 20;
    static final int WORLD_TIMEOUT = 40;

    private GameTestSupport() {}

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    static ItemStack big8() {
        return new ItemStack(ModItems.BIG_BUCKET_8.get());
    }

    static ItemStack big64() {
        return new ItemStack(ModItems.BIG_BUCKET_64.get());
    }

    static ItemStack source() {
        return new ItemStack(ModItems.SOURCE_BUCKET.get());
    }

    static ItemStack junk() {
        return new ItemStack(ModItems.JUNK_BUCKET.get());
    }

    static ItemStack trash() {
        return new ItemStack(ModItems.TRASH_BUCKET.get());
    }

    static ItemStack mob() {
        return new ItemStack(ModItems.MOB_BUCKET.get());
    }

    static ItemStack fluid(ItemStack stack, Fluid fluid, int amount) {
        NBTUtil.setFluidStack(stack, new FluidStack(fluid, amount));
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
        check("none".equals(NBTUtil.getMode(stack)), "Expected mode none, got " + NBTUtil.getMode(stack));
    }

    static void assertFluid(ItemStack stack, Fluid fluid, int amount) {
        FluidStack stored = NBTUtil.getFluidStack(stack);
        check("fluid".equals(NBTUtil.getMode(stack)), "Expected fluid mode, got " + NBTUtil.getMode(stack));
        check(!stored.isEmpty(), "Expected fluid, got empty FluidStack");
        check(stored.getFluid() == fluid, "Expected fluid " + fluid + ", got " + stored.getFluid());
        check(stored.getAmount() == amount, "Expected " + amount + " mB, got " + stored.getAmount());
    }

    static void assertMilk(ItemStack stack, int amount) {
        check("milk".equals(NBTUtil.getMode(stack)), "Expected milk mode, got " + NBTUtil.getMode(stack));
        check(NBTUtil.getAmount(stack) == amount,
                "Expected " + amount + " mB of milk, got " + NBTUtil.getAmount(stack));
    }

    static void assertPowder(ItemStack stack, int units) {
        check("powder_snow".equals(NBTUtil.getMode(stack)),
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

    static BlockPos absolute(GameTestHelper helper, int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y, z));
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

    static <T extends Entity> List<T> entities(GameTestHelper helper, Class<T> type, BlockPos relative,
                                               double radius) {
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(relative));
        return helper.getLevel().getEntitiesOfClass(type,
                new AABB(center, center).inflate(radius), Entity::isAlive);
    }

    static DispenserBlockEntity dispenser(GameTestHelper helper, BlockPos relative, Direction facing,
                                           ItemStack stack) {
        helper.setBlock(relative, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing));
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

    static CompoundTag snapshotTag(ItemStack stack) {
        return stack.getTag() == null ? null : stack.getTag().copy();
    }

    static void assertTagEquals(CompoundTag expected, ItemStack actual, String message) {
        check(Objects.equals(expected, actual.getTag()),
                message + "; expected=" + expected + ", actual=" + actual.getTag());
    }
}
