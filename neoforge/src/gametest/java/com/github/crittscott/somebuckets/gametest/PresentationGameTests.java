package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.register.NeoForgeItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** NeoForge GameTest wrappers for {@link PresentationScenarios}. */
@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class PresentationGameTests {
    private PresentationGameTests() {}

    /** See {@link PresentationScenarios#dynamic_bucket_names_match_registered_identity_and_language}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void dynamic_bucket_names_match_registered_identity_and_language(GameTestHelper helper) {
        PresentationScenarios.dynamic_bucket_names_match_registered_identity_and_language(
                helper, NeoForgeItems.BIG_BUCKET_8.get(), NeoForgeItems.BIG_BUCKET_64.get(),
                NeoForgeItems.SOURCE_BUCKET.get());
    }

    /** See {@link PresentationScenarios#item_definitions_match_bucket_state}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void item_definitions_match_bucket_state(GameTestHelper helper) {
        PresentationScenarios.item_definitions_match_bucket_state(
                helper, NeoForgeItems.BIG_BUCKET_8.get(), NeoForgeItems.MOB_BUCKET.get());
    }

    /** See {@link PresentationScenarios#creative_catalog_has_shared_order_and_full_variants}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void creative_catalog_has_shared_order_and_full_variants(GameTestHelper helper) {
        PresentationScenarios.creative_catalog_has_shared_order_and_full_variants(
                helper, NeoForgeItems.BIG_BUCKET_8.get(), NeoForgeItems.BIG_BUCKET_64.get(),
                NeoForgeItems.SOURCE_BUCKET.get(), NeoForgeItems.JUNK_BUCKET.get(),
                NeoForgeItems.MOB_BUCKET.get(), NeoForgeItems.TRASH_BUCKET.get());
    }
}
