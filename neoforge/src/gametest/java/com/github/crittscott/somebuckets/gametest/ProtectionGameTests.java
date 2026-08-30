package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(SomeBuckets.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    private ProtectionGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void unowned_automation_is_permitted_without_providers(GameTestHelper helper) {
        ProtectionScenarios.unowned_automation_is_permitted_without_providers(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_fluid_context_preserves_main_and_offhand(GameTestHelper helper) {
        ProtectionScenarios.player_fluid_context_preserves_main_and_offhand(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_fluid_edit_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_fluid_edit_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_mob_capture_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_mob_capture_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_storage_absorption_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_storage_absorption_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_automated_feeding_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_automated_feeding_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_cauldron_interaction_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_cauldron_interaction_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_entity_release_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_entity_release_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void aquatic_release_requires_entity_and_fluid_permissions(GameTestHelper helper) {
        ProtectionScenarios.aquatic_release_requires_entity_and_fluid_permissions(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void blockedit_denial_stops_replaceable_fluid_destruction(GameTestHelper helper) {
        ProtectionScenarios.blockedit_denial_stops_replaceable_fluid_destruction(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void automation_player_provider_is_installed(GameTestHelper helper) {
        ProtectionScenarios.automation_player_provider_is_installed(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_without_placement_permission_cannot_collect(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void fallthrough_neighbor_requires_its_own_permission(GameTestHelper helper) {
        ProtectionScenarios.fallthrough_neighbor_requires_its_own_permission(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_player_storage_absorption_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_player_storage_absorption_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_player_trash_absorption_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_player_trash_absorption_without_mutation(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_player_ejection_at_drop_pos(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_player_ejection_at_drop_pos(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_player_feeding_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_player_feeding_without_mutation(helper);
    }

}
