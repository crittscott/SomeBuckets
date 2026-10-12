package com.github.crittscott.somebuckets.network;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact Fabric server-to-client snapshot of the resolved Source Bucket policy: the allowed fluid
 * ids and the synthetic milk permission. Decoding is bounded and rejects an oversized list.
 */
public record FabricSBPolicyPayload(List<ResourceLocation> fluidIds, boolean milkAllowed) {
    /** Network channel id. */
    public static final ResourceLocation CHANNEL = new ResourceLocation(SomeBuckets.MODID, "source_bucket_policy");
    private static final int MAX_FLUID_IDS = 4_096;

    public FabricSBPolicyPayload {
        fluidIds = List.copyOf(fluidIds);
    }

    /** Captures the current authoritative policy for one send or broadcast. */
    public static FabricSBPolicyPayload current() {
        return new FabricSBPolicyPayload(SBPolicy.resolvedFluidIds(), SBPolicy.allowsMilk());
    }

    /** Writes this snapshot to {@code buf}. */
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(fluidIds.size());
        fluidIds.forEach(buf::writeResourceLocation);
        buf.writeBoolean(milkAllowed);
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

    /** Replaces the receiving client's policy without consulting its local config directory. */
    public void applyToClient() {
        SBPolicy.replaceFromServer(fluidIds, milkAllowed);
    }
}
