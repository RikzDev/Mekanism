package mekanism.common.capabilities.adapter;

import mekanism.api.Action;
import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter that wraps a NeoForge 26.1 {@link ResourceHandler<ItemResource>} (from an external block like a vanilla chest)
 * into an interface compatible with Mekanism's internal item handling (IItemHandler-like methods).
 * <p>
 * This is used by Mekanism's transmitters (LogisticalTransporter, etc.) to interact with
 * external mod inventories that now expose ResourceHandler instead of IItemHandler.
 */
@NothingNullByDefault
public class ResourceHandlerItemAdapter {

    private final ResourceHandler<ItemResource> handler;

    public ResourceHandlerItemAdapter(ResourceHandler<ItemResource> handler) {
        this.handler = handler;
    }

    public ResourceHandler<ItemResource> getWrapped() {
        return handler;
    }

    public int getSlots() {
        return handler.size();
    }

    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= handler.size()) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int amount = handler.getAmountAsInt(slot);
        return resource.toStack(amount);
    }

    public ItemStack insertItem(int slot, ItemStack stack, Action action) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = ItemResource.of(stack);
        if (resource.isEmpty()) {
            return stack;
        }
        int toInsert = stack.getCount();

        if (action == Action.SIMULATE) {
            // Open a transaction, insert, then abort to simulate
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(slot, resource, toInsert, tx);
                // Transaction is automatically aborted (not committed) when closing
                if (inserted == 0) {
                    return stack;
                } else if (inserted >= toInsert) {
                    return ItemStack.EMPTY;
                } else {
                    return stack.copyWithCount(toInsert - inserted);
                }
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(slot, resource, toInsert, tx);
                tx.commit();
                if (inserted == 0) {
                    return stack;
                } else if (inserted >= toInsert) {
                    return ItemStack.EMPTY;
                } else {
                    return stack.copyWithCount(toInsert - inserted);
                }
            }
        }
    }

    /**
     * Insert into any available slot (lets the handler decide distribution).
     */
    public ItemStack insertItem(ItemStack stack, Action action) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = ItemResource.of(stack);
        if (resource.isEmpty()) {
            return stack;
        }
        int toInsert = stack.getCount();

        if (action == Action.SIMULATE) {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(resource, toInsert, tx);
                if (inserted == 0) {
                    return stack;
                } else if (inserted >= toInsert) {
                    return ItemStack.EMPTY;
                } else {
                    return stack.copyWithCount(toInsert - inserted);
                }
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(resource, toInsert, tx);
                tx.commit();
                if (inserted == 0) {
                    return stack;
                } else if (inserted >= toInsert) {
                    return ItemStack.EMPTY;
                } else {
                    return stack.copyWithCount(toInsert - inserted);
                }
            }
        }
    }

    public ItemStack extractItem(int slot, int amount, Action action) {
        if (amount <= 0 || slot < 0 || slot >= handler.size()) {
            return ItemStack.EMPTY;
        }
        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (action == Action.SIMULATE) {
            try (Transaction tx = Transaction.openRoot()) {
                int extracted = handler.extract(slot, resource, amount, tx);
                // Aborted on close
                if (extracted == 0) {
                    return ItemStack.EMPTY;
                }
                return resource.toStack(extracted);
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int extracted = handler.extract(slot, resource, amount, tx);
                tx.commit();
                if (extracted == 0) {
                    return ItemStack.EMPTY;
                }
                return resource.toStack(extracted);
            }
        }
    }

    public int getSlotLimit(int slot) {
        if (slot < 0 || slot >= handler.size()) {
            return 0;
        }
        ItemResource resource = handler.getResource(slot);
        // If slot is empty, get general capacity with empty resource
        return handler.getCapacityAsInt(slot, resource.isEmpty() ? ItemResource.EMPTY : resource);
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < 0 || slot >= handler.size()) {
            return false;
        }
        ItemResource resource = ItemResource.of(stack);
        return !resource.isEmpty() && handler.isValid(slot, resource);
    }

    /**
     * Check if this handler has any capacity at all (used for connection detection).
     */
    public boolean hasSlots() {
        return handler.size() > 0;
    }

    /**
     * Wraps a nullable ResourceHandler for convenience.
     *
     * @return The adapter, or null if the handler is null.
     */
    @Nullable
    public static ResourceHandlerItemAdapter wrap(@Nullable ResourceHandler<ItemResource> handler) {
        return handler == null ? null : new ResourceHandlerItemAdapter(handler);
    }
}
