package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The stable fake player Some Buckets' dispensers act as, shared by every claim-provider check. */
public final class DispenserFakePlayer {
    public static final String NAME = "[SomeBuckets]";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes((SomeBuckets.MODID + ":dispenser").getBytes(StandardCharsets.UTF_8)),
            NAME);

    private DispenserFakePlayer() {}

    public static ServerPlayer get(ServerLevel level) {
        return FakePlayerFactory.get(level, PROFILE);
    }
}
