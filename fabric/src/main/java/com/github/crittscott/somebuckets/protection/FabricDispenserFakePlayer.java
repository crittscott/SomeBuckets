package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** The stable Fabric fake player Some Buckets' dispensers act as. */
public final class FabricDispenserFakePlayer {
    private FabricDispenserFakePlayer() {}

    /** Returns Fabric's cached fake player for the dispenser profile in {@code level}. */
    public static ServerPlayer get(ServerLevel level) {
        return FakePlayer.get(level, BucketOperations.DISPENSER_PROFILE);
    }
}
