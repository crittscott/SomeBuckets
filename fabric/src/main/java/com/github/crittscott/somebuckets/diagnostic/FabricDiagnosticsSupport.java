package com.github.crittscott.somebuckets.diagnostic;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;
import java.nio.file.Path;

/** Fabric {@link DiagnosticsSupport}: config path and spawn-egg lookup. */
public final class FabricDiagnosticsSupport implements DiagnosticsSupport {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Nullable
    @Override
    public SpawnEggItem spawnEggFor(EntityType<?> type) {
        return SpawnEggItem.byId(type);
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }
}
