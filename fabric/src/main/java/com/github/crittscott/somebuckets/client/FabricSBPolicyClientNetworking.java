package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.network.FabricSBPolicyPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client receiver and connection-lifetime ownership for the Fabric Source Bucket policy. */
public final class FabricSBPolicyClientNetworking {
    private FabricSBPolicyClientNetworking() {}

    /** Installs the client-thread receiver and prevents policy leakage across connections. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                FabricSBPolicyPayload.TYPE, (payload, context) -> payload.applyToClient());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> SBPolicy.resetToDefaults());
    }
}
