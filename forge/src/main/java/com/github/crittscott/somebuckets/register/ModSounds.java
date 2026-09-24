package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers the mod's custom sound events. */
public class ModSounds {
    /** Deferred register for the mod's sound events. */
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SomeBuckets.MODID);

    /** Reversed evaporation sound used when a Trash Bucket ejects an item. */
    public static final RegistryObject<SoundEvent> TB_EJECT = SOUNDS.register(ModSoundIds.TB_EJECT_ID.getPath(),
            () -> SoundEvent.createVariableRangeEvent(ModSoundIds.TB_EJECT_ID));

    /** Attaches sound-event registration to the mod event bus. */
    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
