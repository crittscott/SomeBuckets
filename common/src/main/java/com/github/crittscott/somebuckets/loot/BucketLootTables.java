package com.github.crittscott.somebuckets.loot;

import com.github.crittscott.somebuckets.SomeBuckets;
import com.github.crittscott.somebuckets.item.BucketDefinitions;
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
        BIG_BUCKET("big_bucket", BucketDefinitions.BIG_BUCKET_ID, 0.05F, 0),
        JUNK_BUCKET("junk_bucket", BucketDefinitions.JUNK_BUCKET_ID, 0.02F, 0),
        SOURCE_BUCKET_OCEAN("source_bucket_ocean", BucketDefinitions.SOURCE_BUCKET_ID, 0.05F, 0),
        SOURCE_BUCKET_BASTION("source_bucket_bastion", BucketDefinitions.SOURCE_BUCKET_ID, 0.10F, 0),
        TRASH_BUCKET("trash_bucket", BucketDefinitions.TRASH_BUCKET_ID, 0.05F, 0),
        MOB_BUCKET("mob_bucket", BucketDefinitions.MOB_BUCKET_ID, 0.05F, 0),
        HUGE_POWDER_SNOW_BUCKET("huge_powder_snow_bucket", BucketDefinitions.HUGE_BUCKET_ID, 0.05F,
                BucketDefinitions.HUGE_BUCKET_CAPACITY_UNITS);

        private final ResourceLocation modifierId;
        private final ResourceLocation itemId;
        private final float chance;
        private final int powderUnits;

        Reward(String modifierPath, ResourceLocation itemId, float chance, int powderUnits) {
            this.modifierId = new ResourceLocation(SomeBuckets.MODID, modifierPath);
            this.itemId = itemId;
            this.chance = chance;
            this.powderUnits = powderUnits;
        }

        public ResourceLocation modifierId() {
            return modifierId;
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

        public Set<ResourceLocation> targets() {
            return switch (this) {
                case BIG_BUCKET -> BIG_BUCKET_TARGETS;
                case JUNK_BUCKET -> JUNK_BUCKET_TARGETS;
                case SOURCE_BUCKET_OCEAN -> SOURCE_BUCKET_OCEAN_TARGETS;
                case SOURCE_BUCKET_BASTION -> SOURCE_BUCKET_BASTION_TARGETS;
                case TRASH_BUCKET, MOB_BUCKET -> TRASH_AND_MOB_BUCKET_TARGETS;
                case HUGE_POWDER_SNOW_BUCKET -> HUGE_POWDER_SNOW_BUCKET_TARGETS;
            };
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

    public static final Set<ResourceLocation> SOURCE_BUCKET_OCEAN_TARGETS = targets(
            "buried_treasure",
            "shipwreck_map",
            "shipwreck_supply",
            "shipwreck_treasure",
            "underwater_ruin_big",
            "underwater_ruin_small");

    public static final Set<ResourceLocation> SOURCE_BUCKET_BASTION_TARGETS = targets(
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
        for (Reward reward : Reward.values()) add(rewards, reward.targets(), reward);

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
