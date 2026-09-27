package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Registers Fabric sound events. */
public final class FabricSounds {
    /** Reversed evaporation sound used when a Trash Bucket ejects an item. */
    public static final SoundEvent TB_EJECT = SoundEvent.createVariableRangeEvent(BucketDefinitions.TB_EJECT_SOUND_ID);

    private FabricSounds() {}

    /** Registers the mod's sound events. */
    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, BucketDefinitions.TB_EJECT_SOUND_ID, TB_EJECT);
    }
}
