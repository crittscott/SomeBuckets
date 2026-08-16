package com.github.crittscott.somebuckets.interaction;

import com.github.crittscott.somebuckets.protection.ProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Positions and action context derived from one dispenser activation. */
public record DispenserTarget(ServerLevel level, Direction outward, BlockPos front, Direction face,
                              BlockHitResult hit, ProtectionContext context) {
    public static DispenserTarget from(BlockSource source) {
        ServerLevel level = source.getLevel();
        BlockPos sourcePos = source.getPos();
        Direction outward = source.getBlockState().getValue(DispenserBlock.FACING);
        BlockPos front = sourcePos.relative(outward);
        Direction face = outward.getOpposite();
        return new DispenserTarget(level, outward, front, face,
                new BlockHitResult(Vec3.atCenterOf(front), face, front, false),
                ProtectionContext.dispenser(sourcePos));
    }

    public AABB frontBounds() {
        return new AABB(front);
    }
}
