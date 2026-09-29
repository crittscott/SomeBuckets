package com.github.crittscott.somebuckets.gametest;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Entry point for the {@code somebuckets_gametest} dev-mod declared in this source set's
 * {@code neoforge.mods.toml}. NeoForge's javafml loader requires a matching {@code @Mod} class to
 * construct the mod; the {@code @GameTestHolder}-annotated test classes alone do not satisfy that.
 * The constructor also registers the sided-tank fixture's block fluid capability and enables
 * NeoForge's milk fluid, as a milk-piping mod would.
 */
@Mod("somebuckets_gametest")
public final class SomeBucketsGameTestMod {
    public SomeBucketsGameTestMod(IEventBus modEventBus) {
        modEventBus.addListener(GameTestSupport::registerTestCapabilities);
        NeoForgeMod.enableMilkFluid();
    }
}
