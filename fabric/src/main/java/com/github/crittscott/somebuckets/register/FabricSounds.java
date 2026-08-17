package com.github.crittscott.somebuckets.register;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Registers Fabric sound events. */
public final class FabricSounds {
    public static final SoundEvent TB_EJECT = SoundEvent.createVariableRangeEvent(ModSoundIds.TB_EJECT_ID);

    private FabricSounds() {}

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, ModSoundIds.TB_EJECT_ID, TB_EJECT);
    }
}
