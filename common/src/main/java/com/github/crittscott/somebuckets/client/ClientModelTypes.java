package com.github.crittscott.somebuckets.client;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

/**
 * Ids of the item-definition types Some Buckets adds: the {@link FluidBucketModel} item model, the
 * {@link JunkContentsRenderer} special renderer, and the {@link MobEggColors.Tint} tint source. Each
 * loader registers them with their codecs before the first client resource load.
 */
@Environment(EnvType.CLIENT)
public final class ClientModelTypes {
    public static final ResourceLocation FLUID_BUCKET = id("fluid_bucket");
    public static final ResourceLocation JUNK_CONTENTS = id("junk_contents");
    public static final ResourceLocation MOB_EGG = id("mob_egg");

    private ClientModelTypes() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SomeBuckets.MODID, path);
    }
}
