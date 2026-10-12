package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/** The stable Forge fake player Some Buckets' dispensers act as. */
public final class ForgeAutomationPlayer {
    private ForgeAutomationPlayer() {}

    /** Returns Forge's cached fake player for the dispenser profile in {@code level}. */
    public static ServerPlayer get(ServerLevel level) {
        return FakePlayerFactory.get(level, BucketOperations.DISPENSER_PROFILE);
    }
}
