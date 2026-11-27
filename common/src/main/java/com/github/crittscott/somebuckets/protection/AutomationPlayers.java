package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;

/** Loader-installed access to the stable fake player used for dispenser-owned actions. */
public final class AutomationPlayers {
    /** The stable identity of the fake player dispensers act as, so claim mods can recognize it. */
    public static final GameProfile DISPENSER_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes((SomeBuckets.MODID + ":dispenser").getBytes(StandardCharsets.UTF_8)),
            "[SomeBuckets]");

    private static Function<ServerLevel, ServerPlayer> provider;

    private AutomationPlayers() {}

    /**
     * Installs the loader's fake-player provider. Called once during single-threaded mod setup,
     * before any dispenser interaction can run.
     *
     * @param newProvider maps a server level to its stable automation player
     */
    public static void install(Function<ServerLevel, ServerPlayer> newProvider) {
        provider = newProvider;
    }

    /**
     * Returns the stable automation player for {@code level}.
     *
     * @param level server level the automation acts in
     * @return the loader's fake player for that level
     */
    public static ServerPlayer get(ServerLevel level) {
        return provider.apply(level);
    }
}
