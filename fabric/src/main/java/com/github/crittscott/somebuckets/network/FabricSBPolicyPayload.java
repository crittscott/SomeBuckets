package com.github.crittscott.somebuckets.network;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * Compact Fabric server-to-client snapshot of the resolved Source Bucket policy, plus its
 * registration and server-side delivery. The client receiver lives in client code.
 */
public record FabricSBPolicyPayload(List<ResourceLocation> fluidIds, boolean milkAllowed)
        implements CustomPacketPayload {
    private static final int MAX_FLUID_IDS = 4_096;

    /** Bounded wire codec for the resolved fluid ids and synthetic milk permission. */
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricSBPolicyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FLUID_IDS)),
                    FabricSBPolicyPayload::fluidIds,
                    ByteBufCodecs.BOOL, FabricSBPolicyPayload::milkAllowed,
                    FabricSBPolicyPayload::new);
    /** Network payload id. */
    public static final Type<FabricSBPolicyPayload> TYPE = new Type<>(SomeBuckets.id("source_bucket_policy"));

    public FabricSBPolicyPayload {
        fluidIds = List.copyOf(fluidIds);
    }

    /** Replaces the receiving client's policy without consulting its local config directory. */
    public void applyToClient() {
        SBPolicy.replaceFromServer(fluidIds, milkAllowed);
    }

    /**
     * Sends one current-policy snapshot, encoded once, to every connected player. Every connected
     * player has the mod, since registry sync rejects a client without its items.
     */
    public static void broadcast(MinecraftServer server) {
        server.getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(current()));
    }

    /** Captures the current authoritative policy for one send or broadcast. */
    public static FabricSBPolicyPayload current() {
        return new FabricSBPolicyPayload(SBPolicy.resolvedFluidIds(), SBPolicy.allowsMilk());
    }

    /** Registers the payload codec and sends the resolved policy to each joining player. */
    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(TYPE, STREAM_CODEC);
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> ServerPlayNetworking.send(handler.player, current()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
