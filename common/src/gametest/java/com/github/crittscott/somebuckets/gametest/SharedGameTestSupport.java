package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.StoredFluid;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Supplies vanilla and loader-neutral setup and assertions to all three loader GameTest suites. */
abstract class SharedGameTestSupport {
    static final int SHORT_TIMEOUT = 20;
    static final int WORLD_TIMEOUT = 40;
    /** Room for a mob's brain sensors to scan at least twice. */
    static final int SENSOR_TIMEOUT = 100;

    /** Every shared scenario class. Each loader wraps every scenario in a same-named GameTest method. */
    private static final List<Class<?>> SCENARIO_CLASSES = List.of(
            AutomationScenarios.class, BBScenarios.class, BlockCapabilityScenarios.class,
            CauldronScenarios.class, LootScenarios.class, MBScenarios.class, NativeFluidStorageScenarios.class,
            PresentationScenarios.class, ProtectionScenarios.class, RecipeScenarios.class, SBScenarios.class,
            StateScenarios.class, StorageBucketScenarios.class, TransferScenarios.class);

    protected SharedGameTestSupport() {}

    /** Saves a complete entity snapshot through vanilla's registry-aware storage API. */
    static CompoundTag snapshot(Entity entity) {
        try (var problems = new ProblemReporter.ScopedCollector(entity.problemPath(), SomeBuckets.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(problems, entity.registryAccess());
            entity.saveWithoutId(output);
            check(problems.isEmpty(), "Entity snapshot could not be saved");
            return output.buildResult();
        }
    }

    /** Encodes a nonempty stack with the test level's registry context. */
    static CompoundTag saveStack(GameTestHelper helper, ItemStack stack) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return (CompoundTag) ItemStack.CODEC.encodeStart(ops, stack)
                .getOrThrow(message -> new GameTestAssertException(Component.literal(message), 0));
    }

    /** Decodes a saved stack through its persistent component codecs and admission checks. */
    static ItemStack loadStack(GameTestHelper helper, CompoundTag saved) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, saved)
                .getOrThrow(message -> new GameTestAssertException(Component.literal(message), 0));
    }

    /**
     * Fails, naming each one, when a shared scenario has no same-named GameTest method in
     * {@code loaderTestClasses}; an unwrapped scenario would otherwise silently never run.
     */
    static void assertEveryScenarioWrapped(Class<? extends Annotation> testAnnotation,
                                           List<Class<?>> loaderTestClasses, Set<String> exclusions) {
        Set<String> wrapped = new HashSet<>();
        for (Class<?> tests : loaderTestClasses) {
            for (Method method : tests.getDeclaredMethods()) {
                if (method.isAnnotationPresent(testAnnotation)) wrapped.add(method.getName());
            }
        }
        assertEveryScenarioPresent(wrapped, exclusions);
    }

    /** Checks the instances actually loaded by the test server. */
    static void assertEveryScenarioRegistered(GameTestHelper helper) {
        Set<String> registered = new HashSet<>();
        helper.getLevel().registryAccess().lookupOrThrow(Registries.TEST_INSTANCE).listElements()
                .map(holder -> holder.key().identifier())
                .filter(id -> id.getNamespace().equals("somebuckets"))
                .forEach(id -> registered.add(id.getPath()));
        assertEveryScenarioPresent(registered, Set.of());
    }

    private static void assertEveryScenarioPresent(Set<String> wrapped, Set<String> exclusions) {
        List<String> missing = new ArrayList<>();
        Set<String> discovered = new HashSet<>();
        List<String> staleExclusions = new ArrayList<>();
        for (Class<?> scenarios : SCENARIO_CLASSES) {
            for (Method method : scenarios.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                boolean scenario = Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers)
                        && !method.isSynthetic() && method.getReturnType() == void.class
                        && method.getParameterCount() >= 1 && method.getParameterTypes()[0] == GameTestHelper.class;
                if (!scenario) continue;
                String id = scenarioId(scenarios, method.getName());
                discovered.add(id);
                if (exclusions.contains(id)) {
                    if (wrapped.contains(method.getName())) staleExclusions.add(id + " is also wrapped");
                } else if (!wrapped.contains(method.getName())) {
                    missing.add(id);
                }
            }
        }
        for (String exclusion : exclusions) {
            if (!discovered.contains(exclusion)) staleExclusions.add(exclusion + " is not a shared scenario");
        }
        check(missing.isEmpty(), "Shared scenarios without a loader GameTest wrapper: " + missing);
        check(staleExclusions.isEmpty(), "Stale shared-scenario exclusions: " + staleExclusions);
    }

    static String scenarioId(Class<?> scenarios, String methodName) {
        return scenarios.getSimpleName() + "#" + methodName;
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(Component.literal(message), 0);
    }

    /**
     * Captures the Source Bucket policy in force, whatever configured it; running the result restores
     * exactly that policy.
     */
    static Runnable sourcePolicyRestorer() {
        List<Identifier> fluids = SBPolicy.resolvedFluidIds();
        boolean milk = SBPolicy.allowsMilk();
        return () -> SBPolicy.replaceFromServer(fluids, milk);
    }

    static ItemStack fluid(ItemStack stack, Fluid fluid, int amount) {
        BucketState.setStoredFluid(stack, new StoredFluid(fluid, amount));
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

    static Item requireVanillaItem(String path) {
        Identifier id = Identifier.fromNamespaceAndPath("minecraft", path);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        check(item != Items.AIR, "Missing GameTest item " + id);
        return item;
    }

    static ItemStack big8() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(BucketDefinitions.BIG_BUCKET_ID));
    }

    static ItemStack big64() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(BucketDefinitions.HUGE_BUCKET_ID));
    }

    static ItemStack source() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(BucketDefinitions.SOURCE_BUCKET_ID));
    }

    static ItemStack junk() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(BucketDefinitions.JUNK_BUCKET_ID));
    }

    static ItemStack trash() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(BucketDefinitions.TRASH_BUCKET_ID));
    }

    static ItemStack mob() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(BucketDefinitions.MOB_BUCKET_ID));
    }

    /**
     * The saved data of a freshly created {@code type} mob, as a Mob Bucket stores it, tagged
     * {@code TestMarker} when {@code marker} is given.
     */
    static CompoundTag mobSnapshot(Level level, EntityType<?> type, @Nullable String marker) {
        Entity entity = type.create(level, EntitySpawnReason.TRIGGERED);
        check(entity != null, "Could not create " + type + " snapshot fixture");
        CompoundTag snapshot = snapshot(entity);
        if (marker != null) snapshot.putString("TestMarker", marker);
        return snapshot;
    }

    /** Appends one freshly created {@code type} mob to the Mob Bucket {@code bucket}. */
    static void addSnapshot(ItemStack bucket, Level level, EntityType<?> type) {
        BucketState.addEntitySnapshot(bucket, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), mobSnapshot(level, type, null));
    }

    /** A Mob Bucket holding one freshly created {@code type} mob, tagged as {@link #mobSnapshot} does. */
    static ItemStack storedMob(Level level, EntityType<?> type, @Nullable String marker) {
        ItemStack bucket = mob();
        BucketState.addEntitySnapshot(bucket, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), mobSnapshot(level, type, marker));
        return bucket;
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

    /**
     * Dispatches a player's right-click on a block as vanilla does once the block itself has not
     * handled it: the held item's {@code useOn}, then, when that passes, {@link GameTestSupport#useItem}.
     * The loader's block-use event and the block's own use do not run, which matches vanilla for a
     * sneaking player holding an item and for a block that does nothing on use.
     */
    static InteractionResult clickBlock(Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = player.getItemInHand(hand).useOn(new UseOnContext(player, hand, hit));
        return result != InteractionResult.PASS ? result : GameTestSupport.useItem(player, hand);
    }

    /**
     * Runs the held item's own {@code use} as the server does once the loader's item-use event has
     * passed: a stack the use hands back in place of the held one replaces it in the hand.
     */
    static InteractionResult useHeldItem(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        InteractionResult result = held.use(player.level(), player, hand);
        if (result instanceof InteractionResult.Success success && success.heldItemTransformedTo() != null
                && success.heldItemTransformedTo() != held) {
            player.setItemInHand(hand, success.heldItemTransformedTo());
        }
        return result;
    }

    static Player survivalPlayer(GameTestHelper helper, BlockPos relative) {
        return serverPlayer(helper, relative);
    }

    /**
     * A survival player with a minimal packet connection, so code that sends it packets
     * behaves as it does for a logged-in player.
     */
    static ServerPlayer serverPlayer(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "sb-gametest"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                cookie.gameProfile(), cookie.clientInformation());

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        ServerGamePacketListenerImpl listener = new ServerGamePacketListenerImpl(
                level.getServer(), connection, player, cookie);
        connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(
                RegistryFriendlyByteBuf.decorator(level.getServer().registryAccess()), listener), listener);

        player.setGameMode(GameType.SURVIVAL);
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

    /**
     * A synthetic player aimed at a known block, with the resulting ray trace checked up front. The
     * test structure is capped by a barrier ceiling at relative height 6, so keep the player's block at
     * relative height 3 or lower: a higher eye starts its ray inside or above the barrier.
     */
    static ServerPlayer survivalPlayerLookingAt(GameTestHelper helper, BlockPos playerPosition,
                                                BlockPos target) {
        ServerPlayer player = serverPlayer(helper, playerPosition);
        BlockPos absoluteTarget = helper.absolutePos(target);
        aimAt(player, Vec3.atCenterOf(absoluteTarget));
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        check(hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(absoluteTarget),
                "Player ray trace hit " + describe(hit) + " instead of " + absoluteTarget);
        return player;
    }

    private static String describe(HitResult hit) {
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? "block " + blockHit.getBlockPos() + " face " + blockHit.getDirection()
                : hit.getType() + " at " + hit.getLocation();
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
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * degrees) - 90.0F;
        player.setYRot(yaw);
        // A living entity's ray trace follows its head, not its body.
        player.setYHeadRot(yaw);
        player.setXRot((float) -(Math.atan2(deltaY, horizontal) * degrees));
    }

    static <T extends Entity> T spawn(GameTestHelper helper, EntityType<T> type, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        T entity = type.create(level, EntitySpawnReason.TRIGGERED);
        check(entity != null, "Could not create entity " + type);
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(relative));
        entity.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
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

    /** Every nonempty stack in {@code player}'s inventory that {@code filter} accepts. */
    static List<ItemStack> inventoryStacks(Player player, Predicate<ItemStack> filter) {
        List<ItemStack> matches = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && filter.test(stack)) matches.add(stack);
        }
        return matches;
    }

    static DispenserBlockEntity dispenser(GameTestHelper helper, BlockPos relative,
                                           Direction facing, ItemStack stack) {
        helper.setBlock(relative,
                Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing));
        DispenserBlockEntity dispenser = helper.getBlockEntity(relative, DispenserBlockEntity.class);
        check(dispenser != null, "Dispenser block entity was not created");
        dispenser.setItem(0, stack);
        return dispenser;
    }

    static void triggerDispenser(GameTestHelper helper, BlockPos relative) {
        helper.pulseRedstone(relative.above(), 2L);
    }

    /**
     * Fires the dispenser at {@code relative} within this call by running the block tick a redstone
     * pulse schedules, so a temporary level-wide setting (world border, permission) applied around
     * the call is in force when it dispenses. {@link #triggerDispenser} fires it ticks later.
     */
    static void dispenseNow(GameTestHelper helper, BlockPos relative) {
        BlockState state = helper.getBlockState(relative);
        check(state.getBlock() instanceof DispenserBlock, "No dispenser at " + relative + ": " + state);
        state.tick(helper.getLevel(), helper.absolutePos(relative), helper.getLevel().getRandom());
    }

    static void assertBlock(GameTestHelper helper, BlockPos relative, Block block) {
        Block actual = helper.getBlockState(relative).getBlock();
        check(actual == block, "Expected " + block + " at " + relative + ", got " + actual);
    }

    /**
     * Records the game events emitted at one block. The listener is attached to the level only while
     * {@link #during} runs.
     */
    static final class EventRecorder implements GameEventListener {
        private final ServerLevel level;
        private final BlockPos absoluteTarget;
        private final List<Holder<GameEvent>> events = new ArrayList<>();
        private final List<GameEvent.Context> contexts = new ArrayList<>();

        EventRecorder(GameTestHelper helper, BlockPos relativeTarget) {
            level = helper.getLevel();
            absoluteTarget = helper.absolutePos(relativeTarget);
        }

        <R> R during(Supplier<R> action) {
            DynamicGameEventListener<EventRecorder> listener = new DynamicGameEventListener<>(this);
            listener.add(level);
            try {
                return action.get();
            } finally {
                listener.remove(level);
            }
        }

        long count(Holder<GameEvent> event) {
            return events.stream().filter(observed -> observed == event).count();
        }

        boolean isEmpty() {
            return events.isEmpty();
        }

        List<GameEvent.Context> contexts() {
            return contexts;
        }

        @Override
        public BlockPositionSource getListenerSource() {
            return new BlockPositionSource(absoluteTarget);
        }

        @Override
        public int getListenerRadius() {
            return 16;
        }

        @Override
        public boolean handleGameEvent(ServerLevel serverLevel, Holder<GameEvent> event,
                                       GameEvent.Context context, Vec3 position) {
            if (BlockPos.containing(position).equals(absoluteTarget)) {
                events.add(event);
                contexts.add(context);
            }
            return true;
        }
    }

    /**
     * Reads a one-criterion advancement from the GameTest data pack. The server registers its
     * trigger when it initializes the test player's advancements.
     */
    static final class CriterionProbe {
        private final Identifier id;
        private final AdvancementProgress progress;
        private boolean fired;

        CriterionProbe(ServerPlayer player, String name) {
            id = SomeBuckets.id("gametest/" + name);
            ServerLevel level = (ServerLevel) player.level();
            AdvancementHolder advancement = level.getServer().getAdvancements().get(id);
            check(advancement != null, "Missing GameTest advancement " + id);
            progress = player.getAdvancements().getOrStartProgress(advancement);
        }

        <R> R during(Supplier<R> action) {
            check(!progress.isDone(), "GameTest advancement already complete before the action: " + id);
            R result = action.get();
            fired = progress.isDone();
            return result;
        }

        boolean fired() {
            return fired;
        }
    }
}
