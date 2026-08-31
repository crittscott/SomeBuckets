package com.github.crittscott.somebuckets.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The permission checks a vanilla bucket applies before it changes the world, plus the registry of
 * claim-protection providers those checks consult.
 *
 * <p>The block-use packet path gates on {@link Level#mayInteract} before an item ever sees it, so
 * this exists for the operations driven from {@code Item.use}, which the server receives without a
 * target position.
 *
 * <p>Providers are composed by unanimous consent: the first denial vetoes the action and prevents
 * later providers and the mod-owned mutation from running. Registration occurs during mod setup and
 * interaction-time reads occur on the server thread; the registry is intentionally not safe for
 * concurrent registration and lookup. Loader-specific optional-integration bootstrap (deciding which
 * providers to register, such as FTB Chunks) is not this class's job; each loader entrypoint calls
 * {@link #register} directly for whatever optional integrations it finds loaded.
 */
public final class Protections {
    private static final List<ClaimProtectionProvider> PROVIDERS = new ArrayList<>();

    private Protections() {}

    /**
     * Applies vanilla player restrictions and every registered claim provider to one exact action
     * and target. The vanilla {@link Level#mayInteract} spawn-protection and world-border gate is
     * applied to every player action, including {@link ProtectionAction#ENTITY_INTERACT}, at the
     * target entity's own position. The stricter {@link Player#mayUseItemAt} block-placement gate is
     * skipped for {@code ENTITY_INTERACT}, since interacting with a mob neither places nor breaks a
     * block. Claim providers receive every action, including automation contexts and the dispenser's
     * source block.
     */
    public static boolean mayAct(Level level, ProtectionContext context, ProtectionAction action,
                                 BlockPos pos, Direction face, ItemStack stack,
                                 @Nullable Entity targetEntity) {
        Player player = context.player();
        if (player != null) {
            if (!level.mayInteract(player, pos)) return false;
            if (action != ProtectionAction.ENTITY_INTERACT && !player.mayUseItemAt(pos, face, stack)) {
                return false;
            }
        }
        return !(level instanceof ServerLevel serverLevel)
                || claimsAllow(serverLevel, context, action, pos, face, stack, targetEntity);
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

    /** Asks each registered claim provider to authorize one action, stopping at the first denial. */
    private static boolean claimsAllow(ServerLevel level, ProtectionContext context, ProtectionAction action,
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
