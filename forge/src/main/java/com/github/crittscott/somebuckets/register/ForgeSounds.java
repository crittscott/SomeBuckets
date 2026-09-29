package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Registers the mod's custom sound events. */
public final class ForgeSounds {
    /** Deferred register for the mod's sound events. */
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SomeBuckets.MODID);

    static {
        SOUNDS.register(BucketDefinitions.TB_EJECT_SOUND_ID.getPath(), () -> BucketDefinitions.TB_EJECT_SOUND);
    }

    /** Attaches sound-event registration to the mod event bus. */
    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
