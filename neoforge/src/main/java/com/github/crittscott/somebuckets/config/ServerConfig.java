package com.github.crittscott.somebuckets.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Defines the server-side config spec backing the Source Bucket allowlist. {@link SBPolicy} reads
 * and caches {@link #SOURCE_BUCKET_ALLOWED_CONTENTS} on load and reload.
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
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null
                );
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}
}
