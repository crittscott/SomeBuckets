package com.github.crittscott.somebuckets.gametest;

import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.common.Mod;

/**
 * Entry point for the {@code somebuckets_gametest} dev-mod declared in this source set's
 * {@code mods.toml}. FML's javafml loader requires a matching {@code @Mod} class to construct the
 * mod; the {@code @GameTestHolder}-annotated test classes alone do not satisfy that. The
 * constructor enables Forge's milk fluid, as a milk-piping mod would.
 */
@Mod("somebuckets_gametest")
public final class SomeBucketsGameTestMod {
    public SomeBucketsGameTestMod() {
        ForgeMod.enableMilkFluid();
    }
}
