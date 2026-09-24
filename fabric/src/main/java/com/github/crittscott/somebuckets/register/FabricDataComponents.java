package com.github.crittscott.somebuckets.register;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Enters the shared bucket-state data component types into the game registry on Fabric. */
public final class FabricDataComponents {
    private FabricDataComponents() {}

    /** Registers every shared bucket-state component type. */
    public static void register() {
        ModDataComponentTypes.forEach(
                (id, type) -> Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, type));
    }
}
