package com.github.crittscott.somebuckets.network;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact Fabric server-to-client snapshot of the resolved Source Bucket policy: the allowed fluid
 * ids and the synthetic milk permission, plus its server-side delivery. Decoding is bounded and
 * rejects an oversized list. The client receiver lives in client code.
 */
public record FabricSBPolicyPayload(List<ResourceLocation> fluidIds, boolean milkAllowed) implements FabricPacket {
    /** Network channel id. */
    public static final ResourceLocation CHANNEL = new ResourceLocation(SomeBuckets.MODID, "source_bucket_policy");
    /** Typed packet registration, decoding through {@link #read}. */
    public static final PacketType<FabricSBPolicyPayload> TYPE =
            PacketType.create(CHANNEL, FabricSBPolicyPayload::read);
    private static final int MAX_FLUID_IDS = 4_096;

    public FabricSBPolicyPayload {
        fluidIds = List.copyOf(fluidIds);
    }

    /** Replaces the receiving client's policy without consulting its local config directory. */
    public void applyToClient() {
        SBPolicy.replaceFromServer(fluidIds, milkAllowed);
    }

    /**
     * Broadcasts one current-policy snapshot, encoded once, to every connected player. Every
     * connected player has the mod, since registry sync rejects a client without its items.
     */
    public static void broadcast(MinecraftServer server) {
        // createS2CPacket takes a channel and buffer, not a FabricPacket, so one shared packet is
        // built from an encoded buffer.
        FriendlyByteBuf buf = PacketByteBufs.create();
        current().write(buf);
        Packet<?> packet = ServerPlayNetworking.createS2CPacket(CHANNEL, buf);
        server.getPlayerList().broadcastAll(packet);
    }

    /** Captures the current authoritative policy for one send or broadcast. */
    public static FabricSBPolicyPayload current() {
        return new FabricSBPolicyPayload(SBPolicy.resolvedFluidIds(), SBPolicy.allowsMilk());
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    /**
     * Reads a snapshot from {@code buf}.
     *
     * @throws IllegalArgumentException if the fluid list is negative or longer than the bound
     */
    public static FabricSBPolicyPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_FLUID_IDS) {
            throw new IllegalArgumentException("Source Bucket policy lists " + count + " fluids");
        }
        List<ResourceLocation> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) ids.add(buf.readResourceLocation());
        return new FabricSBPolicyPayload(ids, buf.readBoolean());
    }

    /** Sends the current policy to each joining player. */
    public static void registerServer() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerPlayNetworking.send(handler.player, current()));
    }

    /** Writes this snapshot to {@code buf}. */
    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(fluidIds.size());
        fluidIds.forEach(buf::writeResourceLocation);
        buf.writeBoolean(milkAllowed);
    }
}
