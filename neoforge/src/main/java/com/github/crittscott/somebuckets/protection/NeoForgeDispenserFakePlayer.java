package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.platform.BucketOperations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Stable NeoForge fake player used by dispenser-owned actions. */
public final class NeoForgeDispenserFakePlayer {
    private NeoForgeDispenserFakePlayer() {}

    /** Returns this mod's cached fake player with Survival abilities for dispenser actions. */
    public static ServerPlayer get(ServerLevel level) {
        ServerPlayer player = FakePlayerFactory.get(level, BucketOperations.DISPENSER_PROFILE);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        return player;
    }
}
