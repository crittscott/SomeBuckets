package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers the mod's custom sound events. */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SomeBuckets.MODID);

    public static final RegistryObject<SoundEvent> TB_EJECT = SOUNDS.register("tb_eject",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(SomeBuckets.MODID, "tb_eject")));

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
