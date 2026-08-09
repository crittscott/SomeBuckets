package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Registers Fabric sound events. */
public final class FabricSounds {
    public static final ResourceLocation TB_EJECT_ID =
            new ResourceLocation(SomeBuckets.MODID, "tb_eject");
    public static final SoundEvent TB_EJECT = SoundEvent.createVariableRangeEvent(TB_EJECT_ID);

    private FabricSounds() {}

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, TB_EJECT_ID, TB_EJECT);
    }
}
