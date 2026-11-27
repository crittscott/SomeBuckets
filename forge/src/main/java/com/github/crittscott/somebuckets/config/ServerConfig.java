package com.github.crittscott.somebuckets.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Defines the server-side config spec backing the Source Bucket allowlist. {@code SomeBucketsForge}
 * passes {@link #SOURCE_BUCKET_ALLOWED_CONTENTS} to {@link SBPolicy#refresh} when the config loads
 * and reloads.
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SOURCE_BUCKET_ALLOWED_CONTENTS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push(SBPolicy.CONFIG_SECTION);
        SOURCE_BUCKET_ALLOWED_CONTENTS = builder
                .comment(
                        "Registry names of fluids that Source Buckets may use.",
                        "Use " + SBPolicy.MILK_ID
                                + " for milk. An empty list disables every Source Bucket content.",
                        "Unknown registry names are ignored and logged when this config loads or reloads."
                )
                .defineListAllowEmpty(
                        List.of(SBPolicy.ALLOWED_CONTENTS_KEY),
                        SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS,
                        value -> value instanceof String id && SBPolicy.isValidContentId(id)
                );
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}
}
