package com.github.crittscott.somebuckets.diagnostic;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.nio.file.Path;

/** NeoForge {@link DiagnosticsSupport}: config path and spawn-egg lookup. */
public final class NeoForgeDiagnosticsSupport implements DiagnosticsSupport {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Nullable
    @Override
    public SpawnEggItem spawnEggFor(EntityType<?> type) {
        return SpawnEggItem.byId(type);
    }

    @Override
    public String loaderName() {
        return "NeoForge";
    }
}
