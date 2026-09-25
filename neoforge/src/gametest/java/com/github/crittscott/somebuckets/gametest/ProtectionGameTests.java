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

    /** See {@link ProtectionScenarios#unowned_automation_is_permitted_without_providers}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void unowned_automation_is_permitted_without_providers(GameTestHelper helper) {
        ProtectionScenarios.unowned_automation_is_permitted_without_providers(helper);
    }

    /** See {@link ProtectionScenarios#player_fluid_context_preserves_main_and_offhand}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void player_fluid_context_preserves_main_and_offhand(GameTestHelper helper) {
        ProtectionScenarios.player_fluid_context_preserves_main_and_offhand(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_fluid_edit_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_fluid_edit_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_fluid_edit_without_mutation(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_mob_capture_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_mob_capture_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_mob_capture_without_mutation(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_storage_absorption_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_storage_absorption_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_storage_absorption_without_mutation(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_automated_feeding_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_automated_feeding_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_automated_feeding_without_mutation(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_cauldron_interaction_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_cauldron_interaction_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_cauldron_interaction_without_mutation(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_entity_release_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_entity_release_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_entity_release_without_mutation(helper);
    }

    /** See {@link ProtectionScenarios#aquatic_release_requires_entity_and_fluid_permissions}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void aquatic_release_requires_entity_and_fluid_permissions(GameTestHelper helper) {
        ProtectionScenarios.aquatic_release_requires_entity_and_fluid_permissions(helper);
    }

    /** See {@link ProtectionScenarios#blockedit_denial_stops_replaceable_fluid_destruction}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void blockedit_denial_stops_replaceable_fluid_destruction(GameTestHelper helper) {
        ProtectionScenarios.blockedit_denial_stops_replaceable_fluid_destruction(helper);
    }

    /** See {@link ProtectionScenarios#blockedit_denial_stops_arbitrary_fluid_placement}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void blockedit_denial_stops_arbitrary_fluid_placement(GameTestHelper helper) {
        ProtectionScenarios.blockedit_denial_stops_arbitrary_fluid_placement(helper);
    }

    /** See {@link ProtectionScenarios#dispenser_acts_as_stable_automation_player}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void dispenser_acts_as_stable_automation_player(GameTestHelper helper) {
        ProtectionScenarios.dispenser_acts_as_stable_automation_player(helper);
    }

    /** See {@link ProtectionScenarios#provider_denial_stops_dispenser_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.WORLD_TIMEOUT)
    public static void provider_denial_stops_dispenser_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.provider_denial_stops_dispenser_without_mutation(helper);
    }

    /** See {@link ProtectionScenarios#adventure_player_without_placement_permission_cannot_collect}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void adventure_player_without_placement_permission_cannot_collect(GameTestHelper helper) {
        ProtectionScenarios.adventure_player_without_placement_permission_cannot_collect(helper);
    }

    /** See {@link ProtectionScenarios#fallthrough_neighbor_requires_its_own_permission}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void fallthrough_neighbor_requires_its_own_permission(GameTestHelper helper) {
        ProtectionScenarios.fallthrough_neighbor_requires_its_own_permission(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_player_storage_absorption_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_player_storage_absorption_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_player_storage_absorption_without_mutation(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_player_trash_absorption_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_player_trash_absorption_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_player_trash_absorption_without_mutation(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_player_ejection_at_drop_pos}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_player_ejection_at_drop_pos(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_player_ejection_at_drop_pos(helper);
    }

    /** See {@link ProtectionScenarios#registered_provider_denies_player_feeding_without_mutation}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = GameTestSupport.SHORT_TIMEOUT)
    public static void registered_provider_denies_player_feeding_without_mutation(GameTestHelper helper) {
        ProtectionScenarios.registered_provider_denies_player_feeding_without_mutation(helper);
    }

}
