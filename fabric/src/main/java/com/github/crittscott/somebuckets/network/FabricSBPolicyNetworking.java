package com.github.crittscott.somebuckets.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Server-side delivery of Fabric Source Bucket policy snapshots. */
public final class FabricSBPolicyNetworking {
    private FabricSBPolicyNetworking() {}

    /** Sends the resolved policy to each joining player. */
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
    }

    /** Sends the current policy to one connected player. */
    public static void send(ServerPlayer player) {
        ServerPlayNetworking.send(player, FabricSBPolicyPayload.CHANNEL, encode(FabricSBPolicyPayload.current()));
    }

    /** Broadcasts one current-policy snapshot to every connected player. */
    public static void broadcast(MinecraftServer server) {
        FabricSBPolicyPayload payload = FabricSBPolicyPayload.current();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, FabricSBPolicyPayload.CHANNEL, encode(payload));
        }
    }

    private static FriendlyByteBuf encode(FabricSBPolicyPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        return buf;
    }
}
