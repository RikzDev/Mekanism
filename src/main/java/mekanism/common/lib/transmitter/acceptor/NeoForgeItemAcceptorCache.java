package mekanism.common.lib.transmitter.acceptor;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.common.capabilities.adapter.NeoForgeCapabilityAdapters;
import mekanism.common.capabilities.adapter.ResourceHandlerItemAdapter;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * An AcceptorCache for LogisticalTransporter that queries both the legacy IItemHandler capability
 * AND the new NeoForge 26.1 ResourceHandler&lt;ItemResource&gt; capability.
 * <p>
 * This allows Mekanism's pipes to connect to:
 * - Other Mekanism machines (which still expose legacy IItemHandler)
 * - Vanilla chests and external mod blocks (which now only expose ResourceHandler&lt;ItemResource&gt;)
 * <p>
 * The cache first checks the legacy capability. If it returns null, it falls back to the new
 * ResourceHandler capability and wraps it with ResourceHandlerItemAdapter to provide an IItemHandler-compatible interface.
 */
@NothingNullByDefault
public class NeoForgeItemAcceptorCache extends AbstractAcceptorCache<IItemHandler, NeoForgeItemAcceptorCache.DualCacheInfo> {

    public NeoForgeItemAcceptorCache(TileEntityTransmitter transmitterTile) {
        super(transmitterTile);
    }

    @Override
    protected DualCacheInfo initializeCache(ServerLevel level, BlockPos pos, Direction opposite, RefreshListener refreshListener) {
        // Create cache for both the legacy capability and the new NeoForge 26.1 capability
        BlockCapabilityCache<IItemHandler, @Nullable Direction> legacyCache =
                BlockCapabilityCache.create(mekanism.common.capabilities.Capabilities.ITEM.block(), level, pos, opposite, refreshListener, refreshListener);
        BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> neoCache =
                BlockCapabilityCache.create(NeoForgeCapabilityAdapters.neoItemCapability(), level, pos, opposite, refreshListener, refreshListener);
        return new DualCacheInfo(legacyCache, neoCache);
    }

    /**
     * Holds both a legacy IItemHandler cache and a new ResourceHandler cache.
     * Returns the legacy handler if available, otherwise wraps the ResourceHandler.
     */
    public record DualCacheInfo(
            BlockCapabilityCache<IItemHandler, @Nullable Direction> legacyCache,
            BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> neoCache
    ) implements AcceptorInfo<IItemHandler> {

        @Nullable
        @Override
        public IItemHandler acceptor() {
            // First try legacy capability (for Mekanism machines and old mods)
            IItemHandler legacy = legacyCache.getCapability();
            if (legacy != null) {
                return legacy;
            }
            // Fall back to new NeoForge 26.1 capability
            ResourceHandler<ItemResource> neo = neoCache.getCapability();
            if (neo != null) {
                // Wrap as IItemHandler-compatible adapter
                return new ResourceHandlerItemHandlerWrapper(neo);
            }
            return null;
        }
    }

    /**
     * A minimal IItemHandler wrapper around ResourceHandlerItemAdapter for use in the Mekanism transporter system.
     * This allows the existing Mekanism code to continue using IItemHandler interfaces
     * while actually delegating to a ResourceHandler&lt;ItemResource&gt;.
     */
    private static class ResourceHandlerItemHandlerWrapper implements IItemHandler {
        private final ResourceHandlerItemAdapter adapter;

        ResourceHandlerItemHandlerWrapper(ResourceHandler<ItemResource> handler) {
            this.adapter = new ResourceHandlerItemAdapter(handler);
        }

        @Override
        public int getSlots() {
            return adapter.getSlots();
        }

        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            return adapter.getStackInSlot(slot);
        }

        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            return adapter.insertItem(slot, stack, simulate ? mekanism.api.Action.SIMULATE : mekanism.api.Action.EXECUTE);
        }

        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return adapter.extractItem(slot, amount, simulate ? mekanism.api.Action.SIMULATE : mekanism.api.Action.EXECUTE);
        }

        @Override
        public int getSlotLimit(int slot) {
            return adapter.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return adapter.isItemValid(slot, stack);
        }
    }
}
