package com.github.crittscott.somebuckets.diagnostic;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.nio.file.Path;

/** Forge {@link DiagnosticsSupport}: config path and modded-aware spawn-egg lookup. */
public final class ForgeDiagnosticsSupport implements DiagnosticsSupport {
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Nullable
    @Override
    public SpawnEggItem spawnEggFor(EntityType<?> type) {
        return ForgeSpawnEggItem.fromEntityType(type);
    }

    @Override
    public String loaderName() {
        return "Forge";
    }
}
