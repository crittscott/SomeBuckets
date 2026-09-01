package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.register.ModDataComponentTypes.JunkContents;
import com.github.crittscott.somebuckets.util.BucketState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-frame cache of a Junk Bucket's decoded contents and icon layout. The loader item renderers
 * call {@link #get} once per rendered bucket; the stack deserialization and arrangement are reused
 * until the stored items or layout seed change, or {@link #clearCache()} runs on a resource reload.
 */
@Environment(EnvType.CLIENT)
public final class JunkBucketRenderData {

    /** A bucket's decoded stored stacks and the placements drawn for them, oldest entry in front. */
    public record Frame(@Nullable JunkContents source, List<ItemStack> contents,
                        List<JunkBucketIcons.Placement> placements) {
        private static final Frame EMPTY = new Frame(null, List.of(), List.of());
    }

    private static final int MAX_ENTRIES = 8;
    private static final Map<Integer, Frame> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Frame> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private JunkBucketRenderData() {}

    /** Discards every cached frame. Call whenever models or the opening mask may have changed. */
    public static synchronized void clearCache() {
        CACHE.clear();
    }

    /**
     * Returns the contents and layout for {@code bucket}. The result is decoded on a cache miss and
     * keyed by the identity of the immutable stored-junk component, which is replaced wholesale on
     * every edit; a stored {@link Frame#source} reference confirms each hit so an identity-hash
     * collision only forces a fresh decode rather than a wrong render.
     *
     * @param bucket the Junk Bucket stack to render
     * @param level render level; a {@code null} level or an empty bucket yields an empty frame
     * @return the cached or freshly decoded frame
     */
    public static synchronized Frame get(ItemStack bucket, @Nullable Level level) {
        JunkContents junk = BucketState.getStoredItemsComponent(bucket);
        if (level == null || junk == null || junk.items().isEmpty()) return Frame.EMPTY;

        int key = System.identityHashCode(junk);
        Frame cached = CACHE.get(key);
        if (cached != null && cached.source() == junk) return cached;

        List<ItemStack> contents = BucketState.getStoredItems(bucket);
        if (contents.isEmpty()) return Frame.EMPTY;

        Frame frame = new Frame(junk, List.copyOf(contents),
                JunkBucketIcons.arrange(contents, junk.layoutSeed()));
        CACHE.put(key, frame);
        return frame;
    }
}
