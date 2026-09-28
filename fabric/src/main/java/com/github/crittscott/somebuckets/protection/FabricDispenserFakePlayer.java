package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Stable Fabric fake player used by dispenser-owned actions. */
public final class FabricDispenserFakePlayer {
    private FabricDispenserFakePlayer() {}

    /** Returns this mod's cached fake player for the supplied server level. */
    public static ServerPlayer get(ServerLevel level) {
        return FakePlayer.get(level, BucketOperations.DISPENSER_PROFILE);
    }
}
