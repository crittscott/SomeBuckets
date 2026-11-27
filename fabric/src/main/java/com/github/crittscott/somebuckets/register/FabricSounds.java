package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.item.BucketDefinitions;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Registers Fabric sound events. */
public final class FabricSounds {
    public static final SoundEvent TB_EJECT =
            SoundEvent.createVariableRangeEvent(BucketDefinitions.TRASH_BUCKET_EJECT_SOUND_ID);

    private FabricSounds() {}

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, BucketDefinitions.TRASH_BUCKET_EJECT_SOUND_ID, TB_EJECT);
    }
}
