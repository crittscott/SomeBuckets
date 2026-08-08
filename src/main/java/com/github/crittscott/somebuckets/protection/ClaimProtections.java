package com.github.crittscott.somebuckets.protection;

import com.github.crittscott.somebuckets.compat.ftbchunks.FtbChunksProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Registry and unanimous-composition point for claim-protection providers.
 *
 * <p>Registration occurs during mod setup, and interaction-time reads occur on the server thread.
 * The registry is intentionally not safe for concurrent registration and lookup.
 */
public final class ClaimProtections {
    private static final List<ClaimProtectionProvider> PROVIDERS = new ArrayList<>();

    private ClaimProtections() {}

    public static void initialize() {
        if (ModList.get().isLoaded("ftbchunks")) {
            FtbChunksProtection.register();
        }
    }

    /**
     * Registers {@code provider} until the returned token is closed.
     *
     * <p>The caller must register and close the token on the same single-threaded lifecycle assumed
     * by interaction handling; closing is idempotent with respect to provider presence.
     *
     * @param provider provider to append to the authorization chain
     * @return lifetime token whose {@link Registration#close()} method unregisters the provider
     */
    public static Registration register(ClaimProtectionProvider provider) {
        PROVIDERS.add(provider);
        return () -> PROVIDERS.remove(provider);
    }

    /**
     * Asks each registered provider to authorize one action, stopping at the first denial.
     *
     * @param level server level in which the action applies
     * @param context player or automation identity for the action
     * @param action mutation or interaction category
     * @param target exact block position associated with the action
     * @param face non-null face or direction associated with the action
     * @param stack bucket stack driving the action
     * @param targetEntity entity being interacted with or released, or {@code null}
     * @return {@code true} when every registered provider allows the action
     */
    public static boolean mayAct(ServerLevel level, ProtectionContext context, ProtectionAction action,
                                 BlockPos target, Direction face, ItemStack stack,
                                 @Nullable Entity targetEntity) {
        for (ClaimProtectionProvider provider : PROVIDERS) {
            if (!provider.mayAct(level, context, action, target, face, stack, targetEntity)) return false;
        }
        return true;
    }

    /** Registration lifetime for one provider; closing it removes that provider from the chain. */
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        /** Unregisters the associated provider. */
        @Override
        void close();
    }
}
