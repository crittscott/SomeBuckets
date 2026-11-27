package com.github.crittscott.somebuckets.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class PresentationGameTests {
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void creative_catalog_has_shared_order_and_full_variants(GameTestHelper helper) {
        PresentationScenarios.creative_catalog_has_shared_order_and_full_variants(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void dynamic_bucket_names_match_registered_identity_and_language(GameTestHelper helper) {
        PresentationScenarios.dynamic_bucket_names_match_registered_identity_and_language(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void every_sound_event_has_a_translated_subtitle(GameTestHelper helper) {
        PresentationScenarios.every_sound_event_has_a_translated_subtitle(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void model_predicates_match_java_protocol(GameTestHelper helper) {
        PresentationScenarios.model_predicates_match_java_protocol(helper);
    }
}
