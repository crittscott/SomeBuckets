package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the mod's custom sound events. */
public final class NeoForgeSounds {
    /** Deferred register for the mod's sound events. */
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SomeBuckets.MODID);

    static {
        SOUNDS.register(BucketDefinitions.TB_EJECT_SOUND_ID.getPath(), () -> BucketDefinitions.TB_EJECT_SOUND);
    }

    private NeoForgeSounds() {}

    /** Attaches sound-event registration to the mod event bus. */
    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
