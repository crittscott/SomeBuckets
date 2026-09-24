package com.github.crittscott.somebuckets.register;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/** Enters the shared bucket-state {@link DataComponentType}s into the game registry on Forge. */
public final class ModDataComponents {
    /** Deferred register containing all shared bucket-state component types. */
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SomeBuckets.MODID);

    static {
        ModDataComponentTypes.forEach((id, type) -> COMPONENTS.register(id.getPath(), () -> type));
    }

    private ModDataComponents() {}

    /** Attaches data-component registration to the mod event bus. */
    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
