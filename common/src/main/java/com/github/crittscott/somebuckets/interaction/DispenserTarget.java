package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Positions and action context derived from one dispenser activation. */
public record DispenserTarget(ServerLevel level, Direction outward, BlockPos front, Direction face,
                              BlockHitResult hit, ProtectionContext context) {
    /**
     * Derives the target geometry for a dispenser activation: the block directly in front along the
     * dispenser's facing, the face pointing back at the dispenser, a centered {@link BlockHitResult}
     * on that face, and a {@link ProtectionContext} for the dispenser at {@code source.pos()}.
     *
     * @param source the activating dispenser
     * @return the derived target geometry and action context
     */
    public static DispenserTarget from(BlockSource source) {
        ServerLevel level = source.level();
        BlockPos sourcePos = source.pos();
        Direction outward = source.state().getValue(DispenserBlock.FACING);
        BlockPos front = sourcePos.relative(outward);
        Direction face = outward.getOpposite();
        return new DispenserTarget(level, outward, front, face,
                new BlockHitResult(Vec3.atCenterOf(front), face, front, false),
                ProtectionContext.dispenser(sourcePos));
    }

    /** The one-block bounding box of the space directly in front of the dispenser. */
    public AABB frontBounds() {
        return new AABB(front);
    }
}
