package com.github.crittscott.somebuckets.loot;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Defines the vanilla structure loot tables and independent bucket rolls shared by both loaders. */
public final class BucketLootTables {
    public enum Reward {
        BIG_BUCKET("big_bucket_8", 0.05F, 0),
        JUNK_BUCKET("junk_bucket", 0.02F, 0),
        SOURCE_BUCKET_FIVE("source_bucket", 0.05F, 0),
        SOURCE_BUCKET_TEN("source_bucket", 0.10F, 0),
        TRASH_BUCKET("trash_bucket", 0.05F, 0),
        MOB_BUCKET("mob_bucket", 0.05F, 0),
        HUGE_POWDER_SNOW_BUCKET("big_bucket_64", 0.05F, 64);

        private final ResourceLocation itemId;
        private final float chance;
        private final int powderUnits;

        Reward(String itemPath, float chance, int powderUnits) {
            this.itemId = new ResourceLocation(SomeBuckets.MODID, itemPath);
            this.chance = chance;
            this.powderUnits = powderUnits;
        }

        public ResourceLocation itemId() {
            return itemId;
        }

        public float chance() {
            return chance;
        }

        public int powderUnits() {
            return powderUnits;
        }
    }

    public static final Set<ResourceLocation> BIG_BUCKET_TARGETS = targets(
            "abandoned_mineshaft",
            "ancient_city",
            "ancient_city_ice_box",
            "bastion_bridge",
            "bastion_hoglin_stable",
            "bastion_other",
            "bastion_treasure",
            "buried_treasure",
            "desert_pyramid",
            "end_city_treasure",
            "igloo_chest",
            "jungle_temple",
            "jungle_temple_dispenser",
            "nether_bridge",
            "pillager_outpost",
            "ruined_portal",
            "shipwreck_map",
            "shipwreck_supply",
            "shipwreck_treasure",
            "simple_dungeon",
            "stronghold_corridor",
            "stronghold_crossing",
            "stronghold_library",
            "underwater_ruin_big",
            "underwater_ruin_small",
            "woodland_mansion");

    public static final Set<ResourceLocation> JUNK_BUCKET_TARGETS = targets(
            "village/village_armorer",
            "village/village_butcher",
            "village/village_cartographer",
            "village/village_desert_house",
            "village/village_fisher",
            "village/village_fletcher",
            "village/village_mason",
            "village/village_plains_house",
            "village/village_savanna_house",
            "village/village_shepherd",
            "village/village_snowy_house",
            "village/village_tannery",
            "village/village_taiga_house",
            "village/village_temple",
            "village/village_toolsmith",
            "village/village_weaponsmith");

    public static final Set<ResourceLocation> SOURCE_BUCKET_FIVE_TARGETS = targets(
            "buried_treasure",
            "shipwreck_map",
            "shipwreck_supply",
            "shipwreck_treasure",
            "underwater_ruin_big",
            "underwater_ruin_small");

    public static final Set<ResourceLocation> SOURCE_BUCKET_TEN_TARGETS = targets(
            "bastion_bridge",
            "bastion_hoglin_stable",
            "bastion_other",
            "bastion_treasure");

    public static final Set<ResourceLocation> TRASH_AND_MOB_BUCKET_TARGETS = targets(
            "end_city_treasure",
            "stronghold_corridor",
            "stronghold_crossing",
            "stronghold_library");

    public static final Set<ResourceLocation> HUGE_POWDER_SNOW_BUCKET_TARGETS = targets(
            "igloo_chest",
            "ancient_city_ice_box");

    private static final Map<ResourceLocation, List<Reward>> REWARDS_BY_TABLE = buildRewardsByTable();

    private BucketLootTables() {}

    public static List<Reward> rewardsFor(ResourceLocation lootTableId) {
        return REWARDS_BY_TABLE.getOrDefault(lootTableId, List.of());
    }

    public static Map<ResourceLocation, List<Reward>> rewardsByTable() {
        return REWARDS_BY_TABLE;
    }

    private static Map<ResourceLocation, List<Reward>> buildRewardsByTable() {
        Map<ResourceLocation, List<Reward>> rewards = new LinkedHashMap<>();
        add(rewards, BIG_BUCKET_TARGETS, Reward.BIG_BUCKET);
        add(rewards, JUNK_BUCKET_TARGETS, Reward.JUNK_BUCKET);
        add(rewards, SOURCE_BUCKET_FIVE_TARGETS, Reward.SOURCE_BUCKET_FIVE);
        add(rewards, SOURCE_BUCKET_TEN_TARGETS, Reward.SOURCE_BUCKET_TEN);
        add(rewards, TRASH_AND_MOB_BUCKET_TARGETS, Reward.TRASH_BUCKET);
        add(rewards, TRASH_AND_MOB_BUCKET_TARGETS, Reward.MOB_BUCKET);
        add(rewards, HUGE_POWDER_SNOW_BUCKET_TARGETS, Reward.HUGE_POWDER_SNOW_BUCKET);

        rewards.replaceAll((id, entries) -> List.copyOf(entries));
        return Collections.unmodifiableMap(rewards);
    }

    private static void add(Map<ResourceLocation, List<Reward>> rewards,
                            Set<ResourceLocation> targets, Reward reward) {
        for (ResourceLocation target : targets) {
            rewards.computeIfAbsent(target, ignored -> new ArrayList<>()).add(reward);
        }
    }

    private static Set<ResourceLocation> targets(String... paths) {
        java.util.LinkedHashSet<ResourceLocation> targets = new java.util.LinkedHashSet<>();
        for (String path : paths) {
            targets.add(new ResourceLocation("minecraft", "chests/" + path));
        }
        return Collections.unmodifiableSet(targets);
    }
}
