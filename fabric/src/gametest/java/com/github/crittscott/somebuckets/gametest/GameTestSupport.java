package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.item.FluidBucketItem;
import com.github.crittscott.somebuckets.platform.BucketOperations;
import com.github.crittscott.somebuckets.platform.FabricBucketOperations;
import com.github.crittscott.somebuckets.register.FabricItems;
import com.github.crittscott.somebuckets.util.NBTUtil;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class GameTestSupport {
    static final String TEMPLATE = "somebuckets:empty_9x6x9";
    static final int SHORT_TIMEOUT = 20;
    static final int WORLD_TIMEOUT = 40;
    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / FluidBucketItem.BUCKET_VOLUME_MB;

    private GameTestSupport() {}

    /**
     * The installed {@link BucketOperations} down-cast to its concrete Fabric type, for the
     * automation-context ({@code ProtectionContext}) overloads that only exist there. Forge's test
     * suite reaches the same overloads through its own loader-specific {@code BBFluidLogic}/
     * {@code SBFluidLogic} singletons; Fabric folds that logic into {@link FabricBucketOperations}
     * itself instead of a separate class.
     */
    static FabricBucketOperations fabricOps() {
        return (FabricBucketOperations) BucketOperations.get();
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    static ItemStack big8() {
        return new ItemStack(FabricItems.BIG_BUCKET_8);
    }

    static ItemStack source() {
        return new ItemStack(FabricItems.SOURCE_BUCKET);
    }

    static ItemStack junk() {
        return new ItemStack(FabricItems.JUNK_BUCKET);
    }

    static ItemStack trash() {
        return new ItemStack(FabricItems.TRASH_BUCKET);
    }

    static ItemStack mob() {
        return new ItemStack(FabricItems.MOB_BUCKET);
    }

    static ItemStack fluid(ItemStack stack, net.minecraft.world.level.material.Fluid fluid, int amount) {
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
        check(NBTUtil.getMode(stack) == NBTUtil.Mode.NONE, "Expected mode none, got " + NBTUtil.getMode(stack));
    }

    static void assertFluid(ItemStack stack, net.minecraft.world.level.material.Fluid fluid, int amount) {
        StoredFluid stored = NBTUtil.getStoredFluid(stack);
        check(NBTUtil.getMode(stack) == NBTUtil.Mode.FLUID, "Expected fluid mode, got " + NBTUtil.getMode(stack));
        check(!stored.isEmpty(), "Expected fluid, got empty StoredFluid");
        check(stored.fluid() == fluid, "Expected fluid " + fluid + ", got " + stored.fluid());
        check(stored.amount() == amount, "Expected " + amount + " mB, got " + stored.amount());
    }

    static void assertMilk(ItemStack stack, int amount) {
        check(NBTUtil.getMode(stack) == NBTUtil.Mode.MILK, "Expected milk mode, got " + NBTUtil.getMode(stack));
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

    static ServerPlayer serverPlayer(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "sb-gametest"));
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(relative));
        player.setPos(position.x, position.y, position.z);
        return player;
    }

    /** A survival player at {@code aboveTarget} looking straight down, for raytrace-driven {@code use}. */
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

    static SidedFluidBlockEntity fluidTank(GameTestHelper helper, BlockPos relative,
                                           Direction exposedFace, int capacityMb, StoredFluid contents) {
        helper.setBlock(relative, Blocks.STRUCTURE_BLOCK);
        BlockPos absolute = helper.absolutePos(relative);
        SidedFluidBlockEntity blockEntity = new SidedFluidBlockEntity(
                absolute, helper.getBlockState(relative), exposedFace, capacityMb, contents);
        helper.getLevel().setBlockEntity(blockEntity);
        check(helper.getLevel().getBlockEntity(absolute) == blockEntity,
                "Test fluid block entity was not installed");
        return blockEntity;
    }

    static void triggerDispenser(GameTestHelper helper, BlockPos relative) {
        helper.pulseRedstone(relative.above(), 2L);
    }

    static void assertBlock(GameTestHelper helper, BlockPos relative, Block block) {
        Block actual = helper.getBlockState(relative).getBlock();
        check(actual == block, "Expected " + block + " at " + relative + ", got " + actual);
    }

    /**
     * A single-slot container holding one bucket stack, standing in for a player inventory slot so a
     * bare {@link ItemStack} can expose its {@code FluidStorage.ITEM} the way production code only ever
     * does through a real container context.
     */
    static SimpleContainer containerOf(ItemStack stack) {
        return new SimpleContainer(stack);
    }

    static Storage<FluidVariant> fluidStorage(SimpleContainer container) {
        ContainerItemContext context = ContainerItemContext.ofSingleSlot(
                InventoryStorage.of(container, null).getSlot(0));
        return FluidStorage.ITEM.find(container.getItem(0), context);
    }

    static long insert(Storage<FluidVariant> storage, FluidVariant variant, long amountDroplets, boolean execute) {
        try (Transaction transaction = Transaction.openOuter()) {
            long moved = storage.insert(variant, amountDroplets, transaction);
            if (execute) transaction.commit();
            return moved;
        }
    }

    static long extract(Storage<FluidVariant> storage, FluidVariant variant,
                        long amountDroplets, boolean execute) {
        try (Transaction transaction = Transaction.openOuter()) {
            long moved = storage.extract(variant, amountDroplets, transaction);
            if (execute) transaction.commit();
            return moved;
        }
    }

    static boolean isEmpty(Storage<FluidVariant> storage) {
        for (var view : storage) {
            if (!view.isResourceBlank() && view.getAmount() > 0) return false;
        }
        return true;
    }

    static final class SidedFluidBlockEntity extends BlockEntity {
        private static volatile boolean registered = false;

        private final Direction exposedFace;
        private final SingleFluidStorage storage;

        private SidedFluidBlockEntity(BlockPos pos, BlockState state, Direction exposedFace,
                                      int capacityMb, StoredFluid contents) {
            super(BlockEntityType.STRUCTURE_BLOCK, pos, state);
            ensureRegistered();
            this.exposedFace = exposedFace;
            long capacityDroplets = (long) capacityMb * DROPLETS_PER_MB;
            this.storage = SingleFluidStorage.withFixedCapacity(capacityDroplets, () -> {});
            if (!contents.isEmpty()) {
                FluidVariant variant = FluidVariant.of(contents.fluid(), contents.variantTag());
                long amountDroplets = (long) contents.amount() * DROPLETS_PER_MB;
                try (Transaction transaction = Transaction.openOuter()) {
                    storage.insert(variant, amountDroplets, transaction);
                    transaction.commit();
                }
            }
        }

        StoredFluid contents() {
            FluidVariant variant = storage.getResource();
            if (variant.isBlank() || storage.getAmount() == 0) return StoredFluid.EMPTY;
            int amountMb = (int) (storage.getAmount() / DROPLETS_PER_MB);
            return new StoredFluid(variant.getFluid(), amountMb, variant.copyNbt());
        }

        /**
         * {@code registerForBlockEntity} binds its provider's parameter type to the passed
         * {@link BlockEntityType}'s own declared Java type ({@code StructureBlockEntity} for
         * {@link BlockEntityType#STRUCTURE_BLOCK}), which this fixture doesn't extend. A fallback
         * provider takes a plain {@link BlockEntity} instead, matching how Fabric API itself wires
         * {@code SidedStorageBlockEntity} support in {@code FluidStorage}'s own static initializer.
         */
        private static synchronized void ensureRegistered() {
            if (registered) return;
            registered = true;
            FluidStorage.SIDED.registerFallback((level, pos, state, blockEntity, direction) -> {
                if (blockEntity instanceof SidedFluidBlockEntity sided && direction == sided.exposedFace) {
                    return sided.storage;
                }
                return null;
            });
        }
    }
}
