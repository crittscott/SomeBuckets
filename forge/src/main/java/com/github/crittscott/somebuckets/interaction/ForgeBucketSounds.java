package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.fluid.FluidTransactions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.SoundActions;

/**
 * Forge bucket-sound resolution and broadcast. The registered-sound, lava-fallback, and direction
 * precedence contract lives in the loader-neutral {@link FluidTransactions}; this class supplies the
 * Forge per-fluid sound lookup and the server-authoritative broadcast that also reaches the acting
 * player.
 */
public final class ForgeBucketSounds {
    private ForgeBucketSounds() {}

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
