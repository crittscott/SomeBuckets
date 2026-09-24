package com.github.crittscott.somebuckets.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-side registration and delivery for Fabric Source Bucket policy snapshots. */
public final class FabricSBPolicyNetworking {
    private FabricSBPolicyNetworking() {}

    /** Registers the payload codec and sends the resolved policy to each joining player. */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(
                FabricSBPolicyPayload.TYPE, FabricSBPolicyPayload.STREAM_CODEC);
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> send(handler.player));
    }

    /** Sends the current policy to one connected player. */
    public static void send(ServerPlayer player) {
        ServerPlayNetworking.send(player, FabricSBPolicyPayload.current());
    }

    /** Broadcasts one current-policy snapshot to every connected player. */
    public static void broadcast(MinecraftServer server) {
        FabricSBPolicyPayload payload = FabricSBPolicyPayload.current();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
