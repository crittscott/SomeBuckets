package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.fluid.FluidPlacement;
import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;

import javax.annotation.Nullable;

/**
 * NeoForge bucket-sound resolution and broadcast. The registered-sound, lava-fallback, and direction
 * precedence contract lives in the loader-neutral {@link FluidPlacement}; this class supplies the
 * NeoForge per-fluid sound lookup and the server-authoritative broadcast that also reaches the
 * acting player.
 */
public final class BucketSounds {
    private BucketSounds() {}

    /** The bucket fill sound for {@code fluid}, via the registered-sound then lava-fallback contract. */
    public static SoundEvent resolveFillSound(Fluid fluid) {
        return resolveBucketSound(fluid.getFluidType().getSound(SoundActions.BUCKET_FILL),
                fluid.defaultFluidState().is(FluidTags.LAVA), true);
    }

    /** The bucket empty sound for {@code fluid}, via the registered-sound then lava-fallback contract. */
    public static SoundEvent resolveEmptySound(Fluid fluid) {
        return resolveBucketSound(fluid.getFluidType().getSound(SoundActions.BUCKET_EMPTY),
                fluid.defaultFluidState().is(FluidTags.LAVA), false);
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

    /** Shared "raspy hiss" pitch for the vanilla evaporation sound and its Trash Bucket reuse. */
    public static float hissPitch(RandomSource random) {
        return FluidPlacement.hissPitch(random);
    }
}
