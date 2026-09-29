package com.github.crittscott.somebuckets.item;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** Defines the registry identities, tags, and capacities of the mod's bucket items. */
public final class BucketDefinitions {
    /** Registry id of the eight-unit Big Bucket. */
    public static final ResourceLocation BIG_BUCKET_ID = SomeBuckets.id("big_bucket_8");
    /** Registry id of the 64-unit Huge Bucket. */
    public static final ResourceLocation HUGE_BUCKET_ID = SomeBuckets.id("big_bucket_64");
    /** Registry id of the Junk Bucket. */
    public static final ResourceLocation JUNK_BUCKET_ID = SomeBuckets.id("junk_bucket");
    /** Registry id of the Mob Bucket. */
    public static final ResourceLocation MOB_BUCKET_ID = SomeBuckets.id("mob_bucket");
    /** Registry id of the Source Bucket. */
    public static final ResourceLocation SOURCE_BUCKET_ID = SomeBuckets.id("source_bucket");
    /** Registry id of the Trash Bucket. */
    public static final ResourceLocation TRASH_BUCKET_ID = SomeBuckets.id("trash_bucket");

    /** Registry id of the component-sensitive empty-bucket recipe ingredient. */
    public static final ResourceLocation EMPTY_BUCKET_INGREDIENT_ID = SomeBuckets.id("empty_bucket");
    /** Registry id of the spawn-egg recipe ingredient. */
    public static final ResourceLocation SPAWN_EGG_INGREDIENT_ID = SomeBuckets.id("spawn_egg");

    /** Registry id of the reversed evaporation sound used for Trash Bucket ejection. */
    public static final ResourceLocation TB_EJECT_SOUND_ID = SomeBuckets.id("tb_eject");
    /** Reversed evaporation sound used for Trash Bucket ejection; each loader registers this instance. */
    public static final SoundEvent TB_EJECT_SOUND = SoundEvent.createVariableRangeEvent(TB_EJECT_SOUND_ID);
    /** Entity types a Mob Bucket never captures. */
    public static final TagKey<EntityType<?>> MB_BLACKLIST =
            TagKey.create(Registries.ENTITY_TYPE, SomeBuckets.id("mb_blacklist"));

    /** Big Bucket capacity in bucket-volume units. */
    public static final int BIG_BUCKET_CAPACITY_UNITS = 8;
    /** Huge Bucket capacity in bucket-volume units. */
    public static final int HUGE_BUCKET_CAPACITY_UNITS = 64;
    /** Junk Bucket capacity in stored stack entries. */
    public static final int JUNK_BUCKET_CAPACITY_STACKS = 9;
    /** Trash Bucket capacity in stored stack entries. */
    public static final int TRASH_BUCKET_CAPACITY_STACKS = 1;
    /** Mob Bucket capacity in captured entity snapshots. */
    public static final int MOB_BUCKET_CAPACITY_MOBS = 8;

    private BucketDefinitions() {}
}
