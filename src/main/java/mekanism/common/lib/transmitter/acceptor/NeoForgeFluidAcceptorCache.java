package mekanism.common.lib.transmitter.acceptor;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.fluid.IExtendedFluidHandler;
import mekanism.common.capabilities.adapter.NeoForgeCapabilityAdapters;
import mekanism.common.capabilities.adapter.ResourceHandlerFluidAdapter;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

/**
 * An AcceptorCache for MechanicalPipe that queries both the legacy IFluidHandler capability
 * AND the new NeoForge 26.1 ResourceHandler&lt;FluidResource&gt; capability.
 * <p>
 * This allows Mekanism's fluid pipes to connect to:
 * - Other Mekanism machines (which still expose legacy IFluidHandler)
 * - Vanilla/external mod tanks (which now only expose ResourceHandler&lt;FluidResource&gt;)
 */
@NothingNullByDefault
public class NeoForgeFluidAcceptorCache extends AbstractAcceptorCache<IFluidHandler, NeoForgeFluidAcceptorCache.DualFluidCacheInfo> {

    public NeoForgeFluidAcceptorCache(TileEntityTransmitter transmitterTile) {
        super(transmitterTile);
    }

    @Override
    protected DualFluidCacheInfo initializeCache(ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
        BlockCapabilityCache<IFluidHandler, @Nullable Direction> legacyCache =
                BlockCapabilityCache.create(mekanism.common.capabilities.Capabilities.FLUID.block(), level, pos, opposite, refreshListener, refreshListener);
        BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> neoCache =
                BlockCapabilityCache.create(NeoForgeCapabilityAdapters.neoFluidCapability(), level, pos, opposite, refreshListener, refreshListener);
        return new DualFluidCacheInfo(legacyCache, neoCache);
    }

    public record DualFluidCacheInfo(
            BlockCapabilityCache<IFluidHandler, @Nullable Direction> legacyCache,
            BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> neoCache
    ) implements AcceptorInfo<IFluidHandler> {

        @Nullable
        @Override
        public IFluidHandler acceptor() {
            // First try legacy capability
            IFluidHandler legacy = legacyCache.getCapability();
            if (legacy != null) {
                return legacy;
            }
            // Fall back to new NeoForge 26.1 capability
            ResourceHandler<FluidResource> neo = neoCache.getCapability();
            if (neo != null) {
                return new ResourceHandlerFluidHandlerWrapper(neo);
            }
            return null;
        }
    }

    /**
     * Minimal IFluidHandler wrapper around ResourceHandlerFluidAdapter for use in the Mekanism pipe system.
     */
    private static class ResourceHandlerFluidHandlerWrapper implements IFluidHandler {
        private final ResourceHandlerFluidAdapter adapter;

        ResourceHandlerFluidHandlerWrapper(ResourceHandler<FluidResource> handler) {
            this.adapter = new ResourceHandlerFluidAdapter(handler);
        }

        @Override
        public int getTanks() {
            return adapter.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return adapter.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return adapter.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return adapter.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            mekanism.api.Action mekAction = action == FluidAction.EXECUTE ? mekanism.api.Action.EXECUTE : mekanism.api.Action.SIMULATE;
            FluidStack remainder = adapter.insertFluid(resource, mekAction);
            return resource.getAmount() - remainder.getAmount();
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            // Find a tank with matching fluid and extract from it
            mekanism.api.Action mekAction = action == FluidAction.EXECUTE ? mekanism.api.Action.EXECUTE : mekanism.api.Action.SIMULATE;
            for (int i = 0; i < adapter.getTanks(); i++) {
                FluidStack inTank = adapter.getFluidInTank(i);
                if (!inTank.isEmpty() && FluidStack.isSameFluidSameComponents(inTank, resource)) {
                    return adapter.extractFluid(i, resource.getAmount(), mekAction);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            mekanism.api.Action mekAction = action == FluidAction.EXECUTE ? mekanism.api.Action.EXECUTE : mekanism.api.Action.SIMULATE;
            // Extract from the first non-empty tank
            for (int i = 0; i < adapter.getTanks(); i++) {
                FluidStack inTank = adapter.getFluidInTank(i);
                if (!inTank.isEmpty()) {
                    return adapter.extractFluid(i, maxDrain, mekAction);
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
