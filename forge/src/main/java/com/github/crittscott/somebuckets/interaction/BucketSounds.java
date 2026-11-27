package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Forge bucket-sound resolution and broadcast. The registered-sound, lava-fallback, and direction
 * precedence contract lives in the loader-neutral {@link FluidPlacement}; this class supplies the
 * Forge per-fluid sound lookup and the server-authoritative broadcast that also reaches the acting
 * player.
 */
public final class BucketSounds {
    private BucketSounds() {}

    /**
     * The bucket fill sound for {@code fluid}, from the fluid type's stack-aware lookup, then the
     * lava fallback.
     */
    public static SoundEvent resolveFillSound(FluidStack fluid) {
        return resolveBucketSound(
                fluid.getFluid().getFluidType().getSound(fluid, SoundActions.BUCKET_FILL),
                fluid.getFluid().defaultFluidState().is(FluidTags.LAVA), true);
    }

    /**
     * The bucket empty sound for {@code fluid}, from the fluid type's stack-aware lookup, then the
     * lava fallback.
     */
    public static SoundEvent resolveEmptySound(FluidStack fluid) {
        return resolveBucketSound(
                fluid.getFluid().getFluidType().getSound(fluid, SoundActions.BUCKET_EMPTY),
                fluid.getFluid().defaultFluidState().is(FluidTags.LAVA), false);
    }

    /** Broadcasts one server-authoritative bucket sound, including the acting player. */
    public static void playBucketSound(Level level, ProtectionContext context, BlockPos pos,
                                       SoundEvent sound) {
        playBucketSound(level, context.player(), pos, sound);
    }

    /** Broadcasts one server-authoritative bucket sound for a nullable player identity. */
    public static void playBucketSound(Level level, @Nullable Player player, BlockPos pos,
                                       SoundEvent sound) {
        if (level.isClientSide) return;
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        notifyActor(player, sound);
    }

    /** Sends the actor the sound excluded from a normal server broadcast. */
    public static void notifyActor(@Nullable Player player, SoundEvent sound) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            serverPlayer.playNotifySound(sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * Selects a bucket sound using the common registered-sound, lava-fallback, and direction
     * precedence contract.
     */
    public static SoundEvent resolveBucketSound(@Nullable SoundEvent registeredSound,
                                                boolean lava, boolean filling) {
        return FluidPlacement.resolveBucketSound(registeredSound, lava, filling);
    }

    /** The sound a dispenser plays when it milks a cow with a Some Buckets bucket. */
    public static SoundEvent automatedMilkingSound() {
        return SoundEvents.COW_MILK;
    }
}
