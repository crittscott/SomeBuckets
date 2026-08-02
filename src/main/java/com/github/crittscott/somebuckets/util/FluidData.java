package com.github.crittscott.somebuckets.util;

import java.util.HashMap;
import java.util.Map;

public enum FluidData {
    // Vanilla fluids
    WATER("minecraft:water", 0.1f, 0x3f76e4),
    LAVA("minecraft:lava", 0.2f, 0xff4500),

    // Mekanism fluids
    BRINE("mekanism:brine", 0.21f, 0x978F66),
    CHLORINE("mekanism:chlorine", 0.22f, 0x899352),
    ETHENE("mekanism:ethene", 0.23f, 0x8C7D94),
    HEAVY_WATER("mekanism:heavy_water", 0.24f, 0x2A2C41),
    HYDROFLUORIC_ACID("mekanism:hydrofluoric_acid", 0.25f, 0x7B7B76),
    HYDROGEN("mekanism:hydrogen", 0.26f, 0x969696),
    HYDROGEN_CHLORIDE("mekanism:hydrogen_chloride", 0.27f, 0x6C8F8C),
    LITHIUM("mekanism:lithium", 0.28f, 0x8D6A19),
    NUTRITIONAL_PASTE("mekanism:nutritional_paste", 0.29f, 0x8D4F69),
    OXYGEN("mekanism:oxygen", 0.30f, 0x4F8896),
    SODIUM("mekanism:sodium", 0.31f, 0x8D9792),
    STEAM("mekanism:steam", 0.32f, 0x979797),
    SULFUR_DIOXIDE("mekanism:sulfur_dioxide", 0.33f, 0x6C6760),
    SULFUR_TRIOXIDE("mekanism:sulfur_trioxide", 0.34f, 0x7F4E4E),
    SULFURIC_ACID("mekanism:sulfuric_acid", 0.35f, 0x59582F),
    SUPERHEATED_SODIUM("mekanism:superheated_sodium", 0.36f, 0x80624D),
    URANIUM_HEXAFLUORIDE("mekanism:uranium_hexafluoride", 0.37f, 0x586448),
    URANIUM_OXIDE("mekanism:uranium_oxide", 0.38f, 0x869315);

    private final String registryName;
    private final float predicateValue;
    private final int barColor;

    private static final Map<String, FluidData> BY_REGISTRY_NAME = new HashMap<>();

    static {
        for (FluidData fluid : values()) {
            BY_REGISTRY_NAME.put(fluid.registryName, fluid);
        }
    }

    FluidData(String registryName, float predicateValue, int barColor) {
        this.registryName = registryName;
        this.predicateValue = predicateValue;
        this.barColor = barColor;
    }

    public float getPredicateValue() {
        return predicateValue;
    }

    public int getBarColor() {
        return barColor;
    }

    /**
     * Get fluid enum by registry name
     * @param registryName The fluid's registry name (e.g., "mekanism:brine")
     * @return The corresponding Fluids enum, or null if not found
     */
    public static FluidData getByRegistryName(String registryName) {
        return BY_REGISTRY_NAME.get(registryName);
    }

    /**
     * Get predicate value for a fluid by registry name
     * @param registryName The fluid's registry name
     * @param fallback The fallback value to return if fluid not found
     * @return The predicate value, or fallback if not found
     */
    public static float getPredicateValue(String registryName, float fallback) {
        FluidData fluid = getByRegistryName(registryName);
        return fluid != null ? fluid.getPredicateValue() : fallback;
    }

    /**
     * Get bar color for a fluid by registry name
     * @param registryName The fluid's registry name
     * @param fallback The fallback color to return if fluid not found
     * @return The bar color, or fallback if not found
     */
    public static int getBarColor(String registryName, int fallback) {
        FluidData fluid = getByRegistryName(registryName);
        return fluid != null ? fluid.getBarColor() : fallback;
    }
}
