package com.github.crittscott.somebuckets.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Defines the server-side config spec backing the Source Bucket allowlist. {@code SomeBucketsNeoForge}
 * passes {@link #SOURCE_BUCKET_ALLOWED_CONTENTS} to {@link SBPolicy#refresh} when the config loads
 * and reloads.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SOURCE_BUCKET_ALLOWED_CONTENTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push(SBPolicy.CONFIG_SECTION);
        SOURCE_BUCKET_ALLOWED_CONTENTS = builder
                .comment(
                        "Registry names of fluids that Source Buckets may use.",
                        "Use " + SBPolicy.MILK_ID
                                + " for milk. An empty list disables every Source Bucket content.",
                        "Unknown registry names are ignored and logged when this config loads or reloads."
                )
                .defineListAllowEmpty(
                        SBPolicy.ALLOWED_CONTENTS_KEY,
                        () -> SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS,
                        () -> SBPolicy.DEFAULT_ALLOWED_CONTENT_IDS.get(0),
                        value -> value instanceof String id && SBPolicy.isValidContentId(id)
                );
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}
}
