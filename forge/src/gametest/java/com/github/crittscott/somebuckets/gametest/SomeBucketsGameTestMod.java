package com.github.crittscott.somebuckets.gametest;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Registers the Forge GameTest functions. */
@Mod("somebuckets_gametest")
public final class SomeBucketsGameTestMod {
    public SomeBucketsGameTestMod(FMLJavaModLoadingContext context) {
        GameTestFunctions.register(context.getModBusGroup());
    }
}
