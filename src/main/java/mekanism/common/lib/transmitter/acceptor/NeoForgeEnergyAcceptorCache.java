package mekanism.common.lib.transmitter.acceptor;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.adapter.NeoForgeCapabilityAdapters;
import mekanism.common.capabilities.adapter.ResourceHandlerEnergyAdapter;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.integration.energy.IEnergyCompat;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.api.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An EnergyAcceptorCache that also queries the new NeoForge 26.1 {@link EnergyHandler} capability
 * in addition to the legacy energy compat system (IEnergyStorage, etc.).
 * <p>
 * This allows Mekanism's UniversalCables to connect to blocks that only expose the new EnergyHandler.
 */
@NothingNullByDefault
public class NeoForgeEnergyAcceptorCache extends AbstractAcceptorCache<IStrictEnergyHandler, NeoForgeEnergyAcceptorCache.NeoForgeEnergyAcceptorInfo> {

    public NeoForgeEnergyAcceptorCache(TileEntityTransmitter transmitterTile) {
        super(transmitterTile);
    }

    @Override
    protected NeoForgeEnergyAcceptorInfo initializeCache(ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
        NeoForgeEnergyAcceptorInfo acceptorInfo = new NeoForgeEnergyAcceptorInfo();
        // Add all legacy energy compats (IEnergyStorage, StrictEnergy, FluxNetworks, GrandPower, etc.)
        for (IEnergyCompat energyCompat : EnergyCompatUtils.getCompats()) {
            if (energyCompat.capabilityExists()) {
                acceptorInfo.addLegacyCapability(energyCompat, level, pos, opposite, refreshListener);
            }
        }
        // Add the new NeoForge 26.1 EnergyHandler capability
        acceptorInfo.addNeoForgeCapability(level, pos, opposite, refreshListener);
        return acceptorInfo;
    }

    public static class NeoForgeEnergyAcceptorInfo implements AcceptorInfo<IStrictEnergyHandler> {

        private record LegacyCacheInfo(IEnergyCompat energyCompat, BlockCapabilityCache<?, @Nullable Direction> cache) {
        }

        private final List<LegacyCacheInfo> legacyCapabilities = new ArrayList<>();
        @Nullable
        private BlockCapabilityCache<EnergyHandler, @Nullable Direction> neoCache;

        NeoForgeEnergyAcceptorInfo() {
        }

        void addLegacyCapability(IEnergyCompat energyCompat, ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
            legacyCapabilities.add(new LegacyCacheInfo(energyCompat, energyCompat.getCapability().createCache(level, pos, opposite, refreshListener, refreshListener)));
        }

        void addNeoForgeCapability(ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
            neoCache = BlockCapabilityCache.create(NeoForgeCapabilityAdapters.neoEnergyCapability(), level, pos, opposite, refreshListener, refreshListener);
        }

        @Nullable
        @Override
        public IStrictEnergyHandler acceptor() {
            // First try legacy capabilities (existing energy compats)
            for (LegacyCacheInfo cacheInfo : legacyCapabilities) {
                IEnergyCompat energyCompat = cacheInfo.energyCompat();
                if (energyCompat.isUsable()) {
                    Object capability = cacheInfo.cache().getCapability();
                    if (capability != null) {
                        IStrictEnergyHandler wrapped = energyCompat.wrapAsStrictEnergyHandler(capability);
                        if (wrapped != null) {
                            return wrapped;
                        }
                    }
                }
            }
            // Fall back to NeoForge 26.1 EnergyHandler
            if (neoCache != null) {
                EnergyHandler neoHandler = neoCache.getCapability();
                if (neoHandler != null) {
                    return new NeoForgeEnergyHandlerWrapper(neoHandler);
                }
            }
            return null;
        }
    }

    /**
     * Wraps a NeoForge 26.1 EnergyHandler as a Mekanism IStrictEnergyHandler.
     * Uses the FE conversion to translate between the systems.
     */
    private static class NeoForgeEnergyHandlerWrapper implements IStrictEnergyHandler {
        private final ResourceHandlerEnergyAdapter adapter;

        NeoForgeEnergyHandlerWrapper(EnergyHandler handler) {
            this.adapter = new ResourceHandlerEnergyAdapter(handler);
        }

        @Override
        public int getEnergyContainerCount() {
            return 1; // EnergyHandler is a single-buffer system
        }

        @Override
        public long getEnergy(int container) {
            // Convert FE to Joules
            return EnergyUnit.FORGE_ENERGY.convertFrom(adapter.getEnergyStored());
        }

        @Override
        public void setEnergy(int container, long energy) {
            // Not supported by the external handler
        }

        @Override
        public long getMaxEnergy(int container) {
            return EnergyUnit.FORGE_ENERGY.convertFrom(adapter.getMaxEnergyStored());
        }

        @Override
        public long getNeededEnergy(int container) {
            long stored = getEnergy(container);
            long max = getMaxEnergy(container);
            return Math.max(0, max - stored);
        }

        @Override
        public long insertEnergy(int container, long amount, Action action) {
            if (amount <= 0) {
                return 0;
            }
            int fe = EnergyUnit.FORGE_ENERGY.convertToAsInt(amount);
            if (fe <= 0) {
                return 0;
            }
            int accepted = adapter.insertEnergy(fe, action == Action.SIMULATE);
            long joulesAccepted = EnergyUnit.FORGE_ENERGY.convertFrom(accepted);
            return amount - joulesAccepted; // Return remainder
        }

        @Override
        public long extractEnergy(int container, long amount, Action action) {
            if (amount <= 0) {
                return 0;
            }
            int fe = EnergyUnit.FORGE_ENERGY.convertToAsInt(amount);
            if (fe <= 0) {
                return 0;
            }
            int extracted = adapter.extractEnergy(fe, action == Action.SIMULATE);
            return EnergyUnit.FORGE_ENERGY.convertFrom(extracted);
        }
    }
}
