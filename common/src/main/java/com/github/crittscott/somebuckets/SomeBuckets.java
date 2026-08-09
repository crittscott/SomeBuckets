package com.github.crittscott.somebuckets;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/** Shared mod identity used by both loader entrypoints and by common code. */
public final class SomeBuckets {
    public static final String MODID = "somebuckets";
    public static final Logger LOGGER = LogUtils.getLogger();

    private SomeBuckets() {}
}
