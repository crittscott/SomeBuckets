package com.github.crittscott.somebuckets.protection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.Function;

/** Loader-installed access to the stable fake player used for dispenser-owned actions. */
public final class AutomationPlayers {
    private static Function<ServerLevel, ServerPlayer> provider;

    private AutomationPlayers() {}

    public static synchronized void install(Function<ServerLevel, ServerPlayer> newProvider) {
        provider = Objects.requireNonNull(newProvider, "newProvider");
    }

    public static ServerPlayer get(ServerLevel level) {
        Function<ServerLevel, ServerPlayer> current = provider;
        if (current == null) throw new IllegalStateException("Automation player provider is not installed");
        return current.apply(level);
    }
}
