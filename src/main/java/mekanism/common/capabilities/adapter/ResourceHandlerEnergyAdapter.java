package mekanism.common.capabilities.adapter;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.math.MathUtils;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter that wraps a NeoForge 26.1 {@link EnergyHandler} (from an external block)
 * into an interface compatible with Mekanism's internal energy handling.
 * <p>
 * Used by Mekanism's UniversalCable transmitters to interact with external mod energy blocks.
 * <p>
 * Note: Mekanism internally uses long-based energy (Joules), and converts to/from FE.
 * This adapter provides methods in both int (FE-compatible) and long scales.
 */
@NothingNullByDefault
public class ResourceHandlerEnergyAdapter {

    private final EnergyHandler handler;

    public ResourceHandlerEnergyAdapter(EnergyHandler handler) {
        this.handler = handler;
    }

    public EnergyHandler getWrapped() {
        return handler;
    }

    /**
     * @return The amount of energy currently stored.
     */
    public long getEnergyStored() {
        return handler.getAmountAsLong();
    }

    /**
     * @return The maximum amount of energy that can be stored.
     */
    public long getMaxEnergyStored() {
        return handler.getCapacityAsLong();
    }

    /**
     * @return Whether this handler can accept energy (has capacity remaining).
     */
    public boolean canReceive() {
        return handler.getAmountAsLong() < handler.getCapacityAsLong();
    }

    /**
     * @return Whether this handler can provide energy (has energy stored).
     */
    public boolean canExtract() {
        return handler.getAmountAsLong() > 0;
    }

    /**
     * Inserts energy into this handler.
     *
     * @param amount   The amount to insert.
     * @param simulate If true, the operation is simulated (no actual change).
     * @return The amount of energy actually accepted.
     */
    public int insertEnergy(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (simulate) {
            try (Transaction tx = Transaction.openRoot()) {
                return handler.insert(amount, tx);
                // Transaction aborted on close (not committed)
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(amount, tx);
                tx.commit();
                return inserted;
            }
        }
    }

    /**
     * Extracts energy from this handler.
     *
     * @param amount   The amount to extract.
     * @param simulate If true, the operation is simulated (no actual change).
     * @return The amount of energy actually extracted.
     */
    public int extractEnergy(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (simulate) {
            try (Transaction tx = Transaction.openRoot()) {
                return handler.extract(amount, tx);
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int extracted = handler.extract(amount, tx);
                tx.commit();
                return extracted;
            }
        }
    }

    /**
     * Checks if there is any energy capability present.
     */
    public boolean isPresent() {
        return true;
    }

    @Nullable
    public static ResourceHandlerEnergyAdapter wrap(@Nullable EnergyHandler handler) {
        return handler == null ? null : new ResourceHandlerEnergyAdapter(handler);
    }
}
