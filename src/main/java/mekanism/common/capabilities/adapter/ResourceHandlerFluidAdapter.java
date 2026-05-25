package mekanism.common.capabilities.adapter;

import mekanism.api.Action;
import mekanism.api.annotations.NothingNullByDefault;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter that wraps a NeoForge 26.1 {@link ResourceHandler<FluidResource>} (from an external block)
 * into an interface compatible with Mekanism's internal fluid handling.
 * <p>
 * Used by Mekanism's MechanicalPipe transmitters to interact with external mod tanks.
 */
@NothingNullByDefault
public class ResourceHandlerFluidAdapter {

    private final ResourceHandler<FluidResource> handler;

    public ResourceHandlerFluidAdapter(ResourceHandler<FluidResource> handler) {
        this.handler = handler;
    }

    public ResourceHandler<FluidResource> getWrapped() {
        return handler;
    }

    public int getTanks() {
        return handler.size();
    }

    public FluidStack getFluidInTank(int tank) {
        if (tank < 0 || tank >= handler.size()) {
            return FluidStack.EMPTY;
        }
        FluidResource resource = handler.getResource(tank);
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        int amount = handler.getAmountAsInt(tank);
        return resource.toStack(amount);
    }

    public int getTankCapacity(int tank) {
        if (tank < 0 || tank >= handler.size()) {
            return 0;
        }
        FluidResource resource = handler.getResource(tank);
        return handler.getCapacityAsInt(tank, resource.isEmpty() ? FluidResource.EMPTY : resource);
    }

    public boolean isFluidValid(int tank, FluidStack stack) {
        if (stack.isEmpty() || tank < 0 || tank >= handler.size()) {
            return false;
        }
        FluidResource resource = FluidResource.of(stack);
        return !resource.isEmpty() && handler.isValid(tank, resource);
    }

    public FluidStack insertFluid(int tank, FluidStack stack, Action action) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        FluidResource resource = FluidResource.of(stack);
        if (resource.isEmpty()) {
            return stack;
        }
        int toInsert = stack.getAmount();

        if (action == Action.SIMULATE) {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(tank, resource, toInsert, tx);
                if (inserted == 0) {
                    return stack;
                } else if (inserted >= toInsert) {
                    return FluidStack.EMPTY;
                } else {
                    return stack.copyWithAmount(toInsert - inserted);
                }
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(tank, resource, toInsert, tx);
                tx.commit();
                if (inserted == 0) {
                    return stack;
                } else if (inserted >= toInsert) {
                    return FluidStack.EMPTY;
                } else {
                    return stack.copyWithAmount(toInsert - inserted);
                }
            }
        }
    }

    /**
     * Insert into any available tank (lets the handler decide distribution).
     */
    public FluidStack insertFluid(FluidStack stack, Action action) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        FluidResource resource = FluidResource.of(stack);
        if (resource.isEmpty()) {
            return stack;
        }
        int toInsert = stack.getAmount();

        if (action == Action.SIMULATE) {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(resource, toInsert, tx);
                if (inserted == 0) {
                    return stack;
                } else if (inserted >= toInsert) {
                    return FluidStack.EMPTY;
                } else {
                    return stack.copyWithAmount(toInsert - inserted);
                }
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(resource, toInsert, tx);
                tx.commit();
                if (inserted == 0) {
                    return stack;
                } else if (inserted >= toInsert) {
                    return FluidStack.EMPTY;
                } else {
                    return stack.copyWithAmount(toInsert - inserted);
                }
            }
        }
    }

    public FluidStack extractFluid(int tank, int amount, Action action) {
        if (amount <= 0 || tank < 0 || tank >= handler.size()) {
            return FluidStack.EMPTY;
        }
        FluidResource resource = handler.getResource(tank);
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }

        if (action == Action.SIMULATE) {
            try (Transaction tx = Transaction.openRoot()) {
                int extracted = handler.extract(tank, resource, amount, tx);
                if (extracted == 0) {
                    return FluidStack.EMPTY;
                }
                return resource.toStack(extracted);
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int extracted = handler.extract(tank, resource, amount, tx);
                tx.commit();
                if (extracted == 0) {
                    return FluidStack.EMPTY;
                }
                return resource.toStack(extracted);
            }
        }
    }

    public boolean hasTanks() {
        return handler.size() > 0;
    }

    @Nullable
    public static ResourceHandlerFluidAdapter wrap(@Nullable ResourceHandler<FluidResource> handler) {
        return handler == null ? null : new ResourceHandlerFluidAdapter(handler);
    }
}
