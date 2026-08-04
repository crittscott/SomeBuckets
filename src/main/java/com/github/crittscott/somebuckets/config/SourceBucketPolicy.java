package com.github.crittscott.somebuckets.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class SourceBucketPolicy {
    private SourceBucketPolicy() {}

    public static boolean allows(FluidStack stack) {
        return !stack.isEmpty() && allows(stack.getFluid());
    }

    public static boolean allows(Fluid fluid) {
        for (String configuredId : ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(configuredId);
            if (id == null || id.equals(ServerConfig.MILK_ID)) continue;

            Fluid allowed = ForgeRegistries.FLUIDS.getValue(id);
            if (allowed != null && fluid.isSame(allowed)) return true;
        }
        return false;
    }

    public static boolean allowsMilk() {
        return ServerConfig.SOURCE_BUCKET_ALLOWED_CONTENTS.get().contains(ServerConfig.MILK_ID.toString());
    }
}
