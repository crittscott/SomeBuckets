package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registers Fabric sound events. */
public final class FabricSounds {
    private FabricSounds() {}

    /** Registers the mod's sound events. */
    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, BucketDefinitions.TB_EJECT_SOUND_ID,
                BucketDefinitions.TB_EJECT_SOUND);
    }
}
