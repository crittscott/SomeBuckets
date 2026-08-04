package com.github.crittscott.somebuckets.config;

import com.github.crittscott.somebuckets.SomeBuckets;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class ServerConfig {
    public static final ResourceLocation MILK_ID = new ResourceLocation(SomeBuckets.MODID, "milk");

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SOURCE_BUCKET_ALLOWED_CONTENTS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("sourceBucket");
        SOURCE_BUCKET_ALLOWED_CONTENTS = builder
                .comment(
                        "Registry names of fluids that Source Buckets may use.",
                        "Use somebuckets:milk for milk. An empty list disables every Source Bucket content."
                )
                .defineListAllowEmpty(
                        "allowedContents",
                        List.of("minecraft:water", "minecraft:lava", MILK_ID.toString()),
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null
                );
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}
}
