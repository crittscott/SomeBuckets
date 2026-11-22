package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.util.BucketState;
import com.github.crittscott.somebuckets.util.TwoColorPalette.Colors;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/** Reload-scoped Mob Bucket palettes, sampled lazily from rendered spawn eggs on the client. */
@Environment(EnvType.CLIENT)
public final class MobEggColors {
    public static final int SAMPLE_SIZE = EggPaletteSampler.IMAGE_SIZE;
    private static final int MISSING_COLOR = 0xFF808080;
    private static final Identifier OVERRIDES_FILE = SomeBuckets.id("mob_egg_colors.json");
    private static final Codec<Integer> RGB_CODEC = TextColor.CODEC.xmap(TextColor::getValue, TextColor::fromRgb);
    private static final Codec<Colors> COLORS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RGB_CODEC.fieldOf("primary").forGetter(Colors::primary),
            RGB_CODEC.fieldOf("secondary").forGetter(Colors::secondary)
    ).apply(instance, Colors::new));
    private static final Codec<Map<Identifier, Colors>> OVERRIDES_CODEC =
            Codec.unboundedMap(Identifier.CODEC, COLORS_CODEC).fieldOf("overrides").codec();

    private static State current = new State(Map.of());

    private MobEggColors() {}

    /** Only override JSON is prepared in the resource-reload worker. GPU work starts on demand. */
    record Loaded(Map<Identifier, Colors> overrides) {}

    /** Client-thread snapshot for a diagnostic sweep; its pixel arrays are read-only. */
    public record ReportSamples(Map<Identifier, Colors> overrides, Map<Item, Sample> eggs) {}

    /** Straight-alpha, top-to-bottom ARGB thumbnail and either its palette or a failure explanation. */
    public record Sample(@Nullable Colors colors, int[] pixels, @Nullable String error) {}

    /** The two overlay colors of the captured mob, with gray used while a sample is pending or absent. */
    public record Tint(int index) implements ItemTintSource {
        public static final MapCodec<Tint> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.intRange(0, 1).fieldOf("index").forGetter(Tint::index)
        ).apply(instance, Tint::new));

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
            Colors colors = colors(stack);
            return colors == null ? MISSING_COLOR : index == 0 ? colors.primary() : colors.secondary();
        }

        @Override
        public MapCodec<Tint> type() {
            return MAP_CODEC;
        }
    }

    private static final class State {
        private final Map<Identifier, Colors> overrides;
        private final Queue<Item> queue = new ArrayDeque<>();
        private final Map<Item, CompletableFuture<Sample>> samples = new LinkedHashMap<>();
        private boolean busy;
        private boolean scheduled;
        private EggPaletteSampler sampler;

        private State(Map<Identifier, Colors> overrides) {
            this.overrides = overrides;
        }
    }

    /** Current colors, with one request queued on first use; null while pending or unavailable. */
    @Nullable
    static Colors colors(ItemStack bucket) {
        EntityType<?> type = BucketState.getCurrentEntityType(bucket);
        if (type == null) return null;
        Colors override = current.overrides.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (override != null) return override;
        SpawnEggItem egg = SpawnEggItem.byId(type);
        if (egg == null) return null;
        Sample sample = request(current, egg).getNow(null);
        return sample == null ? null : sample.colors();
    }

    /** Replaces all derived state; an old GPU callback can never populate the new cache. */
    static void install(Loaded loaded) {
        State previous = current;
        current = new State(loaded.overrides());
        previous.queue.clear();
        if (previous.sampler != null) previous.sampler.close();
        CancellationException cancelled = new CancellationException("Client resources reloaded");
        previous.samples.values().forEach(future -> future.completeExceptionally(cancelled));
        SomeBuckets.LOGGER.info("Mob egg color overrides loaded: {}", loaded.overrides().size());
    }

    static Loaded load(ResourceManager resources) {
        Map<Identifier, Colors> overrides = new LinkedHashMap<>();
        for (Resource resource : resources.getResourceStack(OVERRIDES_FILE)) readOverrides(resource, overrides);
        return new Loaded(Map.copyOf(overrides));
    }

    static boolean pending(ItemStack bucket) {
        EntityType<?> type = BucketState.getCurrentEntityType(bucket);
        if (type == null || current.overrides.containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(type))) return false;
        SpawnEggItem egg = SpawnEggItem.byId(type);
        return egg != null && !request(current, egg).isDone();
    }

    private static void pump(State state) {
        if (state != current) return;
        state.scheduled = false;
        Item egg = state.queue.poll();
        if (egg == null) return;
        state.busy = true;
        try {
            if (state.sampler == null) state.sampler = new EggPaletteSampler();
            state.sampler.capture(egg, sample -> sampled(state, egg, sample));
        } catch (RuntimeException | LinkageError exception) {
            if (state.sampler != null) state.sampler.close();
            state.sampler = null;
            sampled(state, egg, new Sample(null, new int[0], exception.toString()));
        }
    }

    private static void readOverrides(Resource resource, Map<Identifier, Colors> overrides) {
        Map<Identifier, Colors> table;
        try (Reader reader = resource.openAsReader()) {
            table = OVERRIDES_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(IllegalArgumentException::new);
        } catch (IOException | RuntimeException exception) {
            SomeBuckets.LOGGER.warn("Ignoring unreadable {} from pack {}", OVERRIDES_FILE,
                    resource.sourcePackId(), exception);
            return;
        }
        table.forEach((id, colors) -> overrides.put(id,
                new Colors(ARGB.opaque(colors.primary()), ARGB.opaque(colors.secondary()))));
    }

    private static CompletableFuture<Sample> request(State state, Item egg) {
        CompletableFuture<Sample> future = state.samples.get(egg);
        if (future != null) return future;
        future = new CompletableFuture<>();
        state.samples.put(egg, future);
        state.queue.add(egg);
        schedule(state);
        return future;
    }

    /** Queues every registered egg, including overridden ones, and completes after their readbacks. */
    public static CompletableFuture<ReportSamples> sampleAll() {
        State state = current;
        Map<Item, CompletableFuture<Sample>> requested = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SpawnEggItem) requested.put(item, request(state, item));
        }
        return CompletableFuture.allOf(requested.values().toArray(CompletableFuture<?>[]::new))
                .thenApply(ignored -> {
                    Map<Item, Sample> samples = new LinkedHashMap<>();
                    requested.forEach((egg, future) -> samples.put(egg, future.join()));
                    return new ReportSamples(state.overrides, Map.copyOf(samples));
                });
    }

    private static void sampled(State state, Item egg, Sample sample) {
        if (state != current) return;
        state.busy = false;
        state.samples.get(egg).complete(sample);
        if (sample.error() != null) {
            SomeBuckets.LOGGER.warn("Could not sample spawn egg {}: {}; using gray Mob Bucket tint",
                    BuiltInRegistries.ITEM.getKey(egg), sample.error());
        }
        if (state.queue.isEmpty()) {
            if (state.sampler != null) state.sampler.close();
            state.sampler = null;
        } else {
            schedule(state);
        }
    }

    private static void schedule(State state) {
        if (state.scheduled || state.busy) return;
        state.scheduled = true;
        // Fence callbacks run between frames. schedule always enqueues, unlike execute on this thread.
        RenderSystem.queueFencedTask(() -> Minecraft.getInstance().schedule(() -> pump(state)));
    }


}
