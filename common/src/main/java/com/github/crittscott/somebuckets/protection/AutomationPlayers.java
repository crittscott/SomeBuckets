package com.github.crittscott.somebuckets.protection;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.Function;

/** Loader-installed access to the stable fake player used for dispenser-owned actions. */
public final class AutomationPlayers {
    private static volatile Function<ServerLevel, ServerPlayer> provider;

    private AutomationPlayers() {}

    /**
     * Installs the loader's fake-player provider. Called once during mod setup, before any dispenser
     * interaction can run.
     *
     * @param newProvider maps a server level to its stable automation player
     */
    public static synchronized void install(Function<ServerLevel, ServerPlayer> newProvider) {
        provider = Objects.requireNonNull(newProvider, "newProvider");
    }

    /**
     * Returns the stable automation player for {@code level}.
     *
     * @param level server level the automation acts in
     * @return the loader's fake player for that level
     * @throws IllegalStateException if no provider has been installed
     */
    public static ServerPlayer get(ServerLevel level) {
        Function<ServerLevel, ServerPlayer> current = provider;
        if (current == null) throw new IllegalStateException("Automation player provider is not installed");
        return current.apply(level);
    }
}
