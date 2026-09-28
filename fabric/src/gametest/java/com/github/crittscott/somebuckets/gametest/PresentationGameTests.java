package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.register.FabricItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Fabric GameTest wrappers for {@link PresentationScenarios}. */
public final class PresentationGameTests {
    /** See {@link PresentationScenarios#dynamic_bucket_names_match_registered_identity_and_language}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void dynamic_bucket_names_match_registered_identity_and_language(GameTestHelper helper) {
        PresentationScenarios.dynamic_bucket_names_match_registered_identity_and_language(
                helper, FabricItems.BIG_BUCKET_8, FabricItems.BIG_BUCKET_64,
                FabricItems.SOURCE_BUCKET);
    }

    /** See {@link PresentationScenarios#item_definitions_match_bucket_state}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void item_definitions_match_bucket_state(GameTestHelper helper) {
        PresentationScenarios.item_definitions_match_bucket_state(
                helper, FabricItems.BIG_BUCKET_8, FabricItems.MOB_BUCKET);
    }

    /** See {@link PresentationScenarios#creative_catalog_has_shared_order_and_full_variants}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public void creative_catalog_has_shared_order_and_full_variants(GameTestHelper helper) {
        PresentationScenarios.creative_catalog_has_shared_order_and_full_variants(
                helper, FabricItems.BIG_BUCKET_8, FabricItems.BIG_BUCKET_64,
                FabricItems.SOURCE_BUCKET, FabricItems.JUNK_BUCKET,
                FabricItems.MOB_BUCKET, FabricItems.TRASH_BUCKET);
    }
}
