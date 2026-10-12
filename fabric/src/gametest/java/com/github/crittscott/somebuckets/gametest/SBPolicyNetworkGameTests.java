package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.network.FabricSBPolicyPayload;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/**
 * The Fabric server sends its resolved Source Bucket allowlist to clients. In game: on a Fabric
 * server, remove water from {@code config/somebuckets-server.json} and run {@code /reload}; a
 * connected client stops predicting water Source Bucket use, and after leaving the server the client
 * returns to the default allowlist.
 */
public final class SBPolicyNetworkGameTests {
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void policy_payload_round_trips_and_applies_to_the_client(GameTestHelper helper) {
        FabricSBPolicyPayload sent = new FabricSBPolicyPayload(
                List.of(new ResourceLocation("minecraft", "lava")), false);
        FriendlyByteBuf buf = PacketByteBufs.create();
        sent.write(buf);
        FabricSBPolicyPayload received = FabricSBPolicyPayload.read(buf);

        GameTestSupport.check(sent.equals(received), "Policy payload did not round-trip: " + received);
        try {
            received.applyToClient();
            GameTestSupport.check(SBPolicy.allows(Fluids.LAVA), "Applied policy did not allow lava");
            GameTestSupport.check(!SBPolicy.allows(Fluids.WATER), "Applied policy still allowed water");
            GameTestSupport.check(!SBPolicy.allowsMilk(), "Applied policy still allowed milk");
            SBPolicy.resetToDefaults();
            GameTestSupport.check(SBPolicy.allows(Fluids.WATER) && SBPolicy.allowsMilk(),
                    "Disconnect reset did not restore the default allowlist");
        } finally {
            SBPolicy.refresh(SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS, "SBPolicyNetworkGameTests cleanup", true);
        }
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void oversized_policy_payload_is_rejected(GameTestHelper helper) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(1_000_000);

        try {
            FabricSBPolicyPayload.read(buf);
        } catch (IllegalArgumentException expected) {
            helper.succeed();
            return;
        }
        GameTestSupport.check(false, "An oversized policy payload was accepted");
    }
}
