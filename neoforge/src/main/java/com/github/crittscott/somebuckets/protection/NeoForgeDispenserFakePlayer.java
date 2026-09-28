package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Stable NeoForge fake player used by dispenser-owned actions. */
public final class NeoForgeDispenserFakePlayer {
    private NeoForgeDispenserFakePlayer() {}

    /** Returns this mod's cached fake player for the supplied server level. */
    public static ServerPlayer get(ServerLevel level) {
        return FakePlayerFactory.get(level, BucketOperations.DISPENSER_PROFILE);
    }
}
