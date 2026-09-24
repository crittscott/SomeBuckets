package com.github.crittscott.somebuckets.util;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.register.ModDataComponentTypes.CapturedMobs;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-session authority for compact Mob Bucket component synchronization. */
public final class CapturedMobNetworkRegistry {
    private static final int MAX_SESSION_ENTRIES = 65_536;
    private static final Map<UUID, CapturedMobs> VALUES = new LinkedHashMap<>(256, 0.75F, true);

    private CapturedMobNetworkRegistry() {}

    /** Records a full payload before its compact summary is written to the network. */
    public static synchronized void publish(CapturedMobs mobs) {
        if (mobs.isSummary()) return;
        VALUES.put(mobs.contentId(), mobs);
        if (VALUES.size() > MAX_SESSION_ENTRIES) {
            UUID eldest = VALUES.keySet().iterator().next();
            VALUES.remove(eldest);
            SomeBuckets.LOGGER.warn("Mob Bucket network registry reached {}; expired its oldest token",
                    MAX_SESSION_ENTRIES);
        }
    }

    /** Resolves a returned token only when both untrusted display hints match the issued value. */
    public static synchronized Optional<CapturedMobs> resolve(UUID id, ResourceLocation type, int count) {
        CapturedMobs mobs = VALUES.get(id);
        if (mobs == null || !mobs.entityType().equals(type) || mobs.count() != count) {
            return Optional.empty();
        }
        return Optional.of(mobs);
    }

    /** Drops all authority issued by the previous logical server. */
    public static synchronized void clear() {
        VALUES.clear();
    }
}
