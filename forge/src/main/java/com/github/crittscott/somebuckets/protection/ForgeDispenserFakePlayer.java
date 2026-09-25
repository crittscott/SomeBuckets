package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Stable Forge automation player used by dispenser-owned actions. It is never added to the player
 * list, so it has no connection and is not visible to other players; one instance is cached per
 * server level.
 */
public final class ForgeDispenserFakePlayer {
    /** Stable display name of the dispenser automation player. */
    public static final String NAME = "[SomeBuckets]";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes((SomeBuckets.MODID + ":dispenser").getBytes(StandardCharsets.UTF_8)),
            NAME);
    private static final Map<ServerLevel, ServerPlayer> PLAYERS = new WeakHashMap<>();

    private ForgeDispenserFakePlayer() {}

    /** Returns this mod's cached automation player for the supplied server level. */
    public static ServerPlayer get(ServerLevel level) {
        return PLAYERS.computeIfAbsent(level, key ->
                new ServerPlayer(key.getServer(), key, PROFILE, ClientInformation.createDefault()));
    }
}
