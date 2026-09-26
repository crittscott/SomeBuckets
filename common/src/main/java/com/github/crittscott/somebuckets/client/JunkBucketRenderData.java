package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import com.github.crittscott.somebuckets.item.JBItem;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.JunkContents;
import com.github.crittscott.somebuckets.util.BucketState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-frame cache of a Junk Bucket's filtered contents and icon layout. The loader item renderers
 * call {@link #get} once per rendered bucket; the arrangement is reused until the stored items or
 * layout seed change, or {@link #clearCache()} runs on a resource reload.
 */
@Environment(EnvType.CLIENT)
public final class JunkBucketRenderData {

    /** A bucket's stored stacks and the placements drawn for them, oldest entry in front. */
    public record Frame(List<ItemStack> contents, List<JunkBucketIcons.Placement> placements) {
        private static final Frame EMPTY = new Frame(List.of(), List.of());
    }

    /*
     * Keyed by the immutable stored-junk component, which is replaced wholesale on every edit and
     * compares by value. Entries expire once no stack holds their component instance.
     */
    private static final Map<JunkContents, Frame> CACHE = new WeakHashMap<>();

    private JunkBucketRenderData() {}

    /** Discards every cached frame. Call whenever models or the opening mask may have changed. */
    public static synchronized void clearCache() {
        CACHE.clear();
    }

    /**
     * Returns the contents and layout for {@code bucket}, arranging them on a cache miss. The
     * returned stacks are the component's own and must not be mutated.
     *
     * @param bucket the Junk Bucket stack to render
     * @param level render level; a {@code null} level or an empty bucket yields an empty frame
     * @return the cached or freshly arranged frame
     */
    public static synchronized Frame get(ItemStack bucket, @Nullable Level level) {
        JunkContents junk = BucketState.getStoredItemsComponent(bucket);
        if (level == null || junk == null || junk.items().isEmpty()) return Frame.EMPTY;

        Frame cached = CACHE.get(junk);
        if (cached != null) return cached;

        List<ItemStack> contents = new ArrayList<>(BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS);
        for (ItemStack stack : junk.items()) {
            if (contents.size() >= BucketDefinitions.JUNK_BUCKET_CAPACITY_STACKS) break;
            if (!stack.isEmpty() && !(stack.getItem() instanceof JBItem)) contents.add(stack);
        }
        Frame frame = contents.isEmpty() ? Frame.EMPTY
                : new Frame(List.copyOf(contents), JunkBucketIcons.arrange(contents, junk.layoutSeed()));
        CACHE.put(junk, frame);
        return frame;
    }
}
