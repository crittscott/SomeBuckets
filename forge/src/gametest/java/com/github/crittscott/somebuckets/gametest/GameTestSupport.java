package com.github.crittscott.somebuckets.gametest;

import com.github.crittscott.somebuckets.register.ForgeItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class GameTestSupport extends SharedGameTestSupport {
    static final String TEMPLATE = "empty_9x6x9";

    private static boolean protectionListenersInstalled;

    private GameTestSupport() {}

    /** Dispatches a player's right-click on an entity as the server does: Forge's event, then vanilla. */
    static InteractionResult interactWithEntity(Player player, Entity entity, InteractionHand hand) {
        InteractionResult cancelled = ForgeHooks.onInteractEntity(player, entity, hand);
        return cancelled != null ? cancelled : player.interactOn(entity, hand);
    }

    /** Registers the Forge listeners behind {@link #refuseProtection}, once per game. */
    static synchronized void installProtectionListeners() {
        if (protectionListenersInstalled) return;
        protectionListenersInstalled = true;
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, event -> {
            if (refuses(ProtectionEvent.BREAK, event.getPlayer(), event.getPos())) event.setCanceled(true);
        });
        bus.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, event -> {
            if (event.getEntity() instanceof Player player
                    && refuses(ProtectionEvent.PLACE, player, event.getPos())) {
                event.setCanceled(true);
            }
        });
        bus.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.RightClickBlock.class, event -> {
            if (refuses(ProtectionEvent.BLOCK_INTERACT, event.getEntity(), event.getPos())) event.setCanceled(true);
        });
        bus.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.EntityInteract.class, event -> {
            if (refuses(ProtectionEvent.ENTITY_INTERACT, event.getEntity(), event.getTarget().blockPosition())) {
                event.setCanceled(true);
            }
        });
        bus.addListener(EventPriority.NORMAL, false, EntityItemPickupEvent.class, event -> {
            if (refuses(ProtectionEvent.PICKUP, event.getEntity(), event.getItem().blockPosition())) {
                event.setCanceled(true);
            }
        });
    }

    static ItemStack big8() {
        return new ItemStack(ForgeItems.BIG_BUCKET_8.get());
    }

    static ItemStack big64() {
        return new ItemStack(ForgeItems.BIG_BUCKET_64.get());
    }

    static ItemStack source() {
        return new ItemStack(ForgeItems.SOURCE_BUCKET.get());
    }

    static ItemStack junk() {
        return new ItemStack(ForgeItems.JUNK_BUCKET.get());
    }

    static ItemStack trash() {
        return new ItemStack(ForgeItems.TRASH_BUCKET.get());
    }

    static ItemStack mob() {
        return new ItemStack(ForgeItems.MOB_BUCKET.get());
    }

    static SidedFluidBlockEntity fluidTank(GameTestHelper helper, BlockPos relative,
                                           Direction exposedFace, int capacity, FluidStack contents) {
        helper.setBlock(relative, Blocks.STRUCTURE_BLOCK);
        BlockPos absolute = helper.absolutePos(relative);
        SidedFluidBlockEntity blockEntity = new SidedFluidBlockEntity(
                absolute, helper.getBlockState(relative), exposedFace, capacity, contents);
        helper.getLevel().setBlockEntity(blockEntity);
        check(helper.getLevel().getBlockEntity(absolute) == blockEntity,
                "Test fluid block entity was not installed");
        return blockEntity;
    }

    static final class SidedFluidBlockEntity extends BlockEntity {
        private final Direction exposedFace;
        private final FluidTank tank;
        private final LazyOptional<IFluidHandler> capability;

        private SidedFluidBlockEntity(BlockPos pos, BlockState state, Direction exposedFace,
                                      int capacity, FluidStack contents) {
            super(BlockEntityType.STRUCTURE_BLOCK, pos, state);
            this.exposedFace = exposedFace;
            this.tank = new FluidTank(capacity);
            this.tank.setFluid(contents.copy());
            this.capability = LazyOptional.of(() -> tank);
        }

        FluidStack contents() {
            return tank.getFluid().copy();
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> requested,
                                                 @Nullable Direction side) {
            if (requested == ForgeCapabilities.FLUID_HANDLER && side == exposedFace) {
                return capability.cast();
            }
            return super.getCapability(requested, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            capability.invalidate();
        }
    }
}
