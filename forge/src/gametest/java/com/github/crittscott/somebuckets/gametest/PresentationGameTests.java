package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.register.ForgeItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class PresentationGameTests {
    private PresentationGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void dynamic_bucket_names_match_registered_identity_and_language(GameTestHelper helper) {
        PresentationScenarios.dynamic_bucket_names_match_registered_identity_and_language(
                helper, ForgeItems.BIG_BUCKET_8.get(), ForgeItems.BIG_BUCKET_64.get(),
                ForgeItems.SOURCE_BUCKET.get());
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void model_predicates_match_java_protocol(GameTestHelper helper) {
        PresentationScenarios.model_predicates_match_java_protocol(
                helper, ForgeItems.BIG_BUCKET_8.get(), ForgeItems.MOB_BUCKET.get(), false);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void creative_catalog_has_shared_order_and_full_variants(GameTestHelper helper) {
        PresentationScenarios.creative_catalog_has_shared_order_and_full_variants(
                helper, ForgeItems.BIG_BUCKET_8.get(), ForgeItems.BIG_BUCKET_64.get(),
                ForgeItems.SOURCE_BUCKET.get(), ForgeItems.JUNK_BUCKET.get(),
                ForgeItems.MOB_BUCKET.get(), ForgeItems.TRASH_BUCKET.get());
    }
}
