package com.github.crittscott.somebuckets.network;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.config.SBPolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Compact Fabric server-to-client snapshot of the resolved Source Bucket policy. */
public record FabricSBPolicyPayload(List<ResourceLocation> fluidIds, boolean milkAllowed)
        implements CustomPacketPayload {
    private static final int MAX_FLUID_IDS = 4_096;

    /** Network payload id. */
    public static final Type<FabricSBPolicyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, "source_bucket_policy"));
    /** Bounded wire codec for the resolved fluid ids and synthetic milk permission. */
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricSBPolicyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FLUID_IDS)),
                    FabricSBPolicyPayload::fluidIds,
                    ByteBufCodecs.BOOL, FabricSBPolicyPayload::milkAllowed,
                    FabricSBPolicyPayload::new);

    public FabricSBPolicyPayload {
        fluidIds = List.copyOf(fluidIds);
    }

    /** Captures the current authoritative policy for one send or broadcast. */
    public static FabricSBPolicyPayload current() {
        return new FabricSBPolicyPayload(SBPolicy.resolvedFluidIds(), SBPolicy.allowsMilk());
    }

    /** Replaces the receiving client's policy without consulting its local config directory. */
    public void applyToClient() {
        SBPolicy.replaceFromServer(fluidIds, milkAllowed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
