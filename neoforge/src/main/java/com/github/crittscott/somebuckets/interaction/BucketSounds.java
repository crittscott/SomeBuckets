package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;

/**
 * NeoForge bucket-sound resolution and broadcast. The registered-sound, lava-fallback, and direction
 * precedence contract lives in the loader-neutral {@link FluidTransactions}; this class supplies the
 * NeoForge per-fluid sound lookup and the server-authoritative broadcast that also reaches the
 * acting player.
 */
public final class BucketSounds {
    private BucketSounds() {}

    /** The bucket fill sound for {@code fluid}, via the registered-sound then lava-fallback contract. */
    public static SoundEvent resolveFillSound(Fluid fluid) {
        return FluidTransactions.resolveBucketSound(fluid.getFluidType().getSound(SoundActions.BUCKET_FILL),
                fluid.defaultFluidState().is(FluidTags.LAVA), true);
    }

    /** The bucket empty sound for {@code fluid}, via the registered-sound then lava-fallback contract. */
    public static SoundEvent resolveEmptySound(Fluid fluid) {
        return FluidTransactions.resolveBucketSound(fluid.getFluidType().getSound(SoundActions.BUCKET_EMPTY),
                fluid.defaultFluidState().is(FluidTags.LAVA), false);
    }

}
