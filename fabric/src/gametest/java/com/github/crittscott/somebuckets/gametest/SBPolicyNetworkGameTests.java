package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.config.SBPolicy;
import com.github.crittscott.somebuckets.network.FabricSBPolicyPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/** Fabric-specific coverage for Source Bucket policy payload encoding and client replacement. */
public final class SBPolicyNetworkGameTests {
    public SBPolicyNetworkGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void source_policy_payload_round_trip_and_client_replacement(GameTestHelper helper) {
        FabricSBPolicyPayload expected = new FabricSBPolicyPayload(
                List.of(ResourceLocation.parse("minecraft:lava")), false);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            FabricSBPolicyPayload.STREAM_CODEC.encode(buffer, expected);
            FabricSBPolicyPayload decoded = FabricSBPolicyPayload.STREAM_CODEC.decode(buffer);
            GameTestSupport.check(decoded.equals(expected),
                    "Source Bucket policy payload did not round-trip exactly");

            decoded.applyToClient();
            GameTestSupport.check(SBPolicy.allows(Fluids.LAVA),
                    "Client policy did not accept the synchronized fluid");
            GameTestSupport.check(!SBPolicy.allows(Fluids.WATER),
                    "Client policy retained a fluid omitted by the server");
            GameTestSupport.check(!SBPolicy.allowsMilk(),
                    "Client policy retained milk after the server removed it");

            new FabricSBPolicyPayload(List.of(), true).applyToClient();
            GameTestSupport.check(!SBPolicy.allows(Fluids.LAVA) && SBPolicy.allowsMilk(),
                    "Client policy did not replace the prior snapshot atomically");

            SBPolicy.resetToDefaults();
            GameTestSupport.check(SBPolicy.allows(Fluids.WATER)
                            && SBPolicy.allows(Fluids.LAVA) && SBPolicy.allowsMilk(),
                    "Disconnect reset did not restore the shipped Source Bucket policy");
            helper.succeed();
        } finally {
            SBPolicy.resetToDefaults();
            buffer.release();
        }
    }
}
