package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraftforge.event.level.LevelEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stable Forge automation player used by dispenser-owned actions. Forge has no fake-player type on
 * this version, so this is a plain {@link ServerPlayer} that is never added to the player list: it
 * has no connection, so messages sent to it are discarded, and it records no statistics. One
 * instance is cached per loaded server level and dropped when that level unloads.
 */
public final class ForgeDispenserFakePlayer extends ServerPlayer {
    /** Stable display name of the dispenser automation player. */
    public static final String NAME = "[SomeBuckets]";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes((SomeBuckets.MODID + ":dispenser").getBytes(StandardCharsets.UTF_8)),
            NAME);
    private static final Map<ServerLevel, ServerPlayer> PLAYERS = new HashMap<>();

    private ForgeDispenserFakePlayer(ServerLevel level) {
        super(level.getServer(), level, PROFILE, ClientInformation.createDefault());
    }

    /** Returns this mod's cached automation player for the supplied server level. */
    public static ServerPlayer get(ServerLevel level) {
        return PLAYERS.computeIfAbsent(level, ForgeDispenserFakePlayer::new);
    }

    /** Drops the unloading level's cached player, which would otherwise keep that level reachable. */
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PLAYERS.remove(level);
    }

    @Override
    public void sendSystemMessage(Component message, boolean overlay) {
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
    }
}
