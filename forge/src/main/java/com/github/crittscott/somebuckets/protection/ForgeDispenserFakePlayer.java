package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.LevelEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stable Forge automation player used by dispenser-owned actions. It is never added to the player
 * list, so it has no connection and is not visible to other players; one instance is cached per
 * loaded server level and dropped when that level unloads.
 */
public final class ForgeDispenserFakePlayer {
    /** Stable display name of the dispenser automation player. */
    public static final String NAME = "[SomeBuckets]";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes((SomeBuckets.MODID + ":dispenser").getBytes(StandardCharsets.UTF_8)),
            NAME);
    private static final Map<ServerLevel, ServerPlayer> PLAYERS = new HashMap<>();

    private ForgeDispenserFakePlayer() {}

    /** Returns this mod's cached automation player for the supplied server level. */
    public static ServerPlayer get(ServerLevel level) {
        return PLAYERS.computeIfAbsent(level, key ->
                new ServerPlayer(key.getServer(), key, PROFILE, ClientInformation.createDefault()));
    }

    /** Drops the unloading level's cached player, which would otherwise keep that level reachable. */
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PLAYERS.remove(level);
    }
}
