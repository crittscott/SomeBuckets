package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.compat.ftbchunks.FtbChunksProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClaimProtections {
    private static final List<ClaimProtectionProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private ClaimProtections() {}

    public static void initialize() {
        if (ModList.get().isLoaded("ftbchunks")) {
            FtbChunksProtection.register();
        }
    }

    public static Registration register(ClaimProtectionProvider provider) {
        PROVIDERS.add(provider);
        return () -> PROVIDERS.remove(provider);
    }

    public static boolean mayAct(ServerLevel level, ProtectionContext context, ProtectionAction action,
                                 BlockPos target, Direction face, ItemStack stack,
                                 @Nullable Entity targetEntity) {
        for (ClaimProtectionProvider provider : PROVIDERS) {
            if (!provider.mayAct(level, context, action, target, face, stack, targetEntity)) return false;
        }
        return true;
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
