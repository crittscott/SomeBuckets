package com.github.crittscott.somebuckets.gametest;

import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Registers GameTest functions and enables loader-specific test fixtures. */
@Mod("somebuckets_gametest")
public final class SomeBucketsGameTestMod {
    public SomeBucketsGameTestMod(FMLJavaModLoadingContext context) {
        GameTestFunctions.register(context.getModEventBus());
        ForgeMod.enableMilkFluid();
    }
}
