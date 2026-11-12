package com.github.crittscott.somebuckets.gametest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Registers GameTest functions and enables loader-specific test fixtures. */
@Mod("somebuckets_gametest")
public final class SomeBucketsGameTestMod {
    public SomeBucketsGameTestMod(IEventBus modEventBus) {
        GameTestFunctions.register(modEventBus);
        modEventBus.addListener(GameTestSupport::registerTestCapabilities);
        NeoForgeMod.enableMilkFluid();
    }
}
