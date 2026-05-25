package mekanism.common.capabilities.adapter;

import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.fluid.ISidedFluidHandler;
import mekanism.api.inventory.ISidedItemHandler;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister.BlockEntityTypeBuilder;
import mekanism.common.tile.base.CapabilityTileEntity;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class that registers the NeoForge 26.1 capabilities (ResourceHandler, EnergyHandler)
 * on Mekanism's block entities by wrapping the internal handlers through our adapter classes.
 * <p>
 * This is the bridge between Mekanism's old IItemHandler/IFluidHandler/IEnergyStorage-based system
 * and the new NeoForge 26.1 Transfer API.
 * <p>
 * Capabilities registered here:
 * <ul>
 *   <li>{@code net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK} → ResourceHandler&lt;ItemResource&gt;</li>
 *   <li>{@code net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK} → ResourceHandler&lt;FluidResource&gt;</li>
 *   <li>{@code net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK} → EnergyHandler</li>
 * </ul>
 */
public final class NeoForgeCapabilityAdapters {

    private NeoForgeCapabilityAdapters() {
    }

    // Reference to NeoForge 26.1 capabilities
    private static final BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> NEOFORGE_ITEM_BLOCK =
            net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK;

    private static final BlockCapability<ResourceHandler<FluidResource>, @Nullable Direction> NEOFORGE_FLUID_BLOCK =
            net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK;

    private static final BlockCapability<EnergyHandler, @Nullable Direction> NEOFORGE_ENERGY_BLOCK =
            net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK;

    /**
     * Provider that exposes Mekanism machines as ResourceHandler&lt;ItemResource&gt; to external mods.
     * <p>
     * Resolves the Mekanism-internal IItemHandler capability, and wraps it with MekanismItemResourceHandler.
     * Uses the same disabled/resolver checks as ITEM_HANDLER_PROVIDER.
     */
    @SuppressWarnings("unchecked")
    public static final ICapabilityProvider<CapabilityTileEntity, @Nullable Direction, ResourceHandler<ItemResource>> ITEM_RESOURCE_PROVIDER =
            (tile, context) -> {
                // Resolve using Mekanism's internal item capability (same logic as ITEM_HANDLER_PROVIDER)
                Object resolved = CapabilityTileEntity.basicCapabilityProvider(mekanism.common.capabilities.Capabilities.ITEM.block())
                        .getCapability(tile, context);
                if (resolved == null) {
                    return null;
                }
                // The resolved object implements ISidedItemHandler (it's a ProxyItemHandler)
                if (resolved instanceof ISidedItemHandler sidedHandler) {
                    return new MekanismItemResourceHandler(sidedHandler, context);
                }
                // Fallback: wrap as a basic handler with null side (read-only/internal)
                // This shouldn't normally happen but handles edge cases
                return null;
            };

    /**
     * Provider that exposes Mekanism machines as ResourceHandler&lt;FluidResource&gt; to external mods.
     */
    @SuppressWarnings("unchecked")
    public static final ICapabilityProvider<CapabilityTileEntity, @Nullable Direction, ResourceHandler<FluidResource>> FLUID_RESOURCE_PROVIDER =
            (tile, context) -> {
                Object resolved = CapabilityTileEntity.basicCapabilityProvider(mekanism.common.capabilities.Capabilities.FLUID.block())
                        .getCapability(tile, context);
                if (resolved == null) {
                    return null;
                }
                if (resolved instanceof ISidedFluidHandler sidedHandler) {
                    return new MekanismFluidResourceHandler(sidedHandler, context);
                }
                return null;
            };

    /**
     * Provider that exposes Mekanism machines as EnergyHandler to external mods.
     */
    @SuppressWarnings("unchecked")
    public static final ICapabilityProvider<CapabilityTileEntity, @Nullable Direction, EnergyHandler> ENERGY_HANDLER_PROVIDER =
            (tile, context) -> {
                Object resolved = CapabilityTileEntity.basicCapabilityProvider(mekanism.common.capabilities.Capabilities.STRICT_ENERGY.block())
                        .getCapability(tile, context);
                if (resolved == null) {
                    return null;
                }
                if (resolved instanceof IStrictEnergyHandler energyHandler) {
                    return new MekanismEnergyHandler(energyHandler);
                }
                return null;
            };

    /**
     * Adds the NeoForge 26.1 capability providers to a block entity builder.
     * Call this in addition to the existing Mekanism capability registration.
     *
     * @param builder The block entity builder to add capabilities to.
     * @param <BE>    The block entity type.
     */
    public static <BE extends CapabilityTileEntity> void addNeoForgeCapabilities(BlockEntityTypeBuilder<BE> builder) {
        builder.with(NEOFORGE_ITEM_BLOCK, ITEM_RESOURCE_PROVIDER);
        builder.with(NEOFORGE_FLUID_BLOCK, FLUID_RESOURCE_PROVIDER);
        builder.with(NEOFORGE_ENERGY_BLOCK, ENERGY_HANDLER_PROVIDER);
    }

    /**
     * @return The NeoForge 26.1 item capability for use in AcceptorCache and transmitters.
     */
    public static BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> neoItemCapability() {
        return NEOFORGE_ITEM_BLOCK;
    }

    /**
     * @return The NeoForge 26.1 fluid capability for use in transmitters.
     */
    public static BlockCapability<ResourceHandler<FluidResource>, @Nullable Direction> neoFluidCapability() {
        return NEOFORGE_FLUID_BLOCK;
    }

    /**
     * @return The NeoForge 26.1 energy capability for use in transmitters.
     */
    public static BlockCapability<EnergyHandler, @Nullable Direction> neoEnergyCapability() {
        return NEOFORGE_ENERGY_BLOCK;
    }

    // --- Utility methods for querying NeoForge 26.1 capabilities from the world ---

    /**
     * Queries the NeoForge 26.1 ResourceHandler&lt;ItemResource&gt; capability from a block in the world.
     *
     * @return The ResourceHandler if the block is loaded and exposes the capability, or null otherwise.
     */
    @Nullable
    public static ResourceHandler<ItemResource> getItemResourceIfLoaded(@Nullable net.minecraft.world.level.Level level,
                                                                         net.minecraft.core.BlockPos pos,
                                                                         @Nullable net.minecraft.world.level.block.entity.BlockEntity tile,
                                                                         @Nullable Direction side) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getCapability(NEOFORGE_ITEM_BLOCK, pos, null, tile, side);
    }

    /**
     * Queries the NeoForge 26.1 ResourceHandler&lt;FluidResource&gt; capability from a block in the world.
     */
    @Nullable
    public static ResourceHandler<FluidResource> getFluidResourceIfLoaded(@Nullable net.minecraft.world.level.Level level,
                                                                           net.minecraft.core.BlockPos pos,
                                                                           @Nullable net.minecraft.world.level.block.entity.BlockEntity tile,
                                                                           @Nullable Direction side) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getCapability(NEOFORGE_FLUID_BLOCK, pos, null, tile, side);
    }

    /**
     * Queries the NeoForge 26.1 EnergyHandler capability from a block in the world.
     */
    @Nullable
    public static EnergyHandler getEnergyHandlerIfLoaded(@Nullable net.minecraft.world.level.Level level,
                                                          net.minecraft.core.BlockPos pos,
                                                          @Nullable net.minecraft.world.level.block.entity.BlockEntity tile,
                                                          @Nullable Direction side) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getCapability(NEOFORGE_ENERGY_BLOCK, pos, null, tile, side);
    }
}
