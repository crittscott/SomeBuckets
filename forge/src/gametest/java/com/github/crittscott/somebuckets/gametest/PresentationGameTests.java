package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.register.ForgeItems;
import net.minecraft.gametest.framework.GameTestHelper;

/** Forge GameTest wrappers for {@link PresentationScenarios}. */
public final class PresentationGameTests {
    private PresentationGameTests() {}

    /** See {@link PresentationScenarios#dynamic_bucket_names_match_registered_identity_and_language}. */
    public static void dynamic_bucket_names_match_registered_identity_and_language(GameTestHelper helper) {
        PresentationScenarios.dynamic_bucket_names_match_registered_identity_and_language(
                helper, ForgeItems.BIG_BUCKET_8.get(), ForgeItems.BIG_BUCKET_64.get(),
                ForgeItems.SOURCE_BUCKET.get());
    }

    /** See {@link PresentationScenarios#item_definitions_match_bucket_state}. */
    public static void item_definitions_match_bucket_state(GameTestHelper helper) {
        PresentationScenarios.item_definitions_match_bucket_state(
                helper, ForgeItems.BIG_BUCKET_8.get(), ForgeItems.MOB_BUCKET.get());
    }

    /** See {@link PresentationScenarios#creative_catalog_has_shared_order_and_full_variants}. */
    public static void creative_catalog_has_shared_order_and_full_variants(GameTestHelper helper) {
        PresentationScenarios.creative_catalog_has_shared_order_and_full_variants(
                helper, ForgeItems.BIG_BUCKET_8.get(), ForgeItems.BIG_BUCKET_64.get(),
                ForgeItems.SOURCE_BUCKET.get(), ForgeItems.JUNK_BUCKET.get(),
                ForgeItems.MOB_BUCKET.get(), ForgeItems.TRASH_BUCKET.get());
    }
}
