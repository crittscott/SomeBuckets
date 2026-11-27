package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class PresentationGameTests {
    private PresentationGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void creative_catalog_has_shared_order_and_full_variants(GameTestHelper helper) {
        PresentationScenarios.creative_catalog_has_shared_order_and_full_variants(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void dynamic_bucket_names_match_registered_identity_and_language(GameTestHelper helper) {
        PresentationScenarios.dynamic_bucket_names_match_registered_identity_and_language(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void every_sound_event_has_a_translated_subtitle(GameTestHelper helper) {
        PresentationScenarios.every_sound_event_has_a_translated_subtitle(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void model_predicates_match_java_protocol(GameTestHelper helper) {
        PresentationScenarios.model_predicates_match_java_protocol(helper);
    }
}
