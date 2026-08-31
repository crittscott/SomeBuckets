package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.util.NBTUtil;
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
    public record Frame(List<ItemStack> contents, List<JunkBucketIcons.Placement> placements) {
        private static final Frame EMPTY = new Frame(List.of(), List.of());
    }

    private static final int MAX_ENTRIES = 8;
    private static final Map<Long, Frame> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Frame> eldest) {
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
     * keyed by content identity, so buckets holding the same items and seed share one entry. An empty
     * bucket, or a null level (no registry access to deserialize with), yields an empty frame.
     */
    public static synchronized Frame get(ItemStack bucket, @Nullable Level level) {
        if (level == null || NBTUtil.getStoredItemCount(bucket) == 0) return Frame.EMPTY;

        long fingerprint = NBTUtil.storedItemsFingerprint(bucket);
        Frame cached = CACHE.get(fingerprint);
        if (cached != null) return cached;

        List<ItemStack> contents = NBTUtil.getStoredItems(bucket, level.registryAccess());
        if (contents.isEmpty()) return Frame.EMPTY;

        Frame frame = new Frame(List.copyOf(contents),
                JunkBucketIcons.arrange(contents, NBTUtil.getJunkLayoutSeed(bucket)));
        CACHE.put(fingerprint, frame);
        return frame;
    }
}
