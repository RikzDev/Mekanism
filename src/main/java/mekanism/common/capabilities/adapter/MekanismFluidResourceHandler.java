package mekanism.common.capabilities.adapter;

import mekanism.api.Action;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.fluid.ISidedFluidHandler;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter that exposes Mekanism's internal {@link ISidedFluidHandler} as a NeoForge 26.1
 * {@link ResourceHandler<FluidResource>} so that external mods' pipes/conduits can interact
 * with Mekanism machines' fluid tanks.
 */
@NothingNullByDefault
public class MekanismFluidResourceHandler implements ResourceHandler<FluidResource> {

    private final ISidedFluidHandler handler;
    @Nullable
    private final Direction side;

    public MekanismFluidResourceHandler(ISidedFluidHandler handler, @Nullable Direction side) {
        this.handler = handler;
        this.side = side;
    }

    @Override
    public int size() {
        return handler.getTanks(side);
    }

    @Override
    public FluidResource getResource(int index) {
        FluidStack stack = handler.getFluidInTank(index, side);
        return stack.isEmpty() ? FluidResource.EMPTY : FluidResource.of(stack);
    }

    @Override
    public long getAmountAsLong(int index) {
        return handler.getFluidInTank(index, side).getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return handler.getTankCapacity(index, side);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        // Create a FluidStack with bucket volume for validation purposes
        FluidStack testStack = resource.toStack(1);
        return handler.isFluidValid(index, testStack, side);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) {
            return 0;
        }

        FluidStack toInsert = resource.toStack(amount);

        // Execute immediately - Mekanism doesn't support rollback
        FluidStack remainder = handler.insertFluid(index, toInsert, side, Action.EXECUTE);
        return amount - remainder.getAmount();
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) {
            return 0;
        }

        FluidStack toInsert = resource.toStack(amount);
        FluidStack remainder = handler.insertFluid(toInsert, side, Action.EXECUTE);
        return amount - remainder.getAmount();
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) {
            return 0;
        }

        // Check if the fluid at this index matches
        FluidStack current = handler.getFluidInTank(index, side);
        if (current.isEmpty() || !resource.matches(current)) {
            return 0;
        }

        FluidStack extracted = handler.extractFluid(index, amount, side, Action.EXECUTE);
        return extracted.getAmount();
    }

    @Override
    public int extract(FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) {
            return 0;
        }

        int totalExtracted = 0;
        int remaining = amount;
        int tanks = size();

        for (int i = 0; i < tanks && remaining > 0; i++) {
            FluidStack current = handler.getFluidInTank(i, side);
            if (!current.isEmpty() && resource.matches(current)) {
                FluidStack extracted = handler.extractFluid(i, remaining, side, Action.EXECUTE);
                int count = extracted.getAmount();
                totalExtracted += count;
                remaining -= count;
            }
        }

        return totalExtracted;
    }
}
