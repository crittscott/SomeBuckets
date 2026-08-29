package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the mod's custom sound events. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SomeBuckets.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> TB_EJECT = SOUNDS.register(
            ModSoundIds.TB_EJECT_ID.getPath(),
            () -> SoundEvent.createVariableRangeEvent(ModSoundIds.TB_EJECT_ID));

    private ModSounds() {}

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
