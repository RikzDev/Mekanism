package mekanism.common.capabilities.adapter;

import com.google.common.primitives.Ints;
import mekanism.api.Action;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Adapter that exposes Mekanism's internal {@link IStrictEnergyHandler} as a NeoForge 26.1
 * {@link EnergyHandler} so that external mods' cables/conduits can interact with
 * Mekanism machines' energy storage.
 * <p>
 * Performs conversion between Mekanism's Joules and NeoForge's FE (Forge Energy)
 * using the same conversion logic as ForgeEnergyIntegration.
 */
@NothingNullByDefault
public class MekanismEnergyHandler implements EnergyHandler {

    private final IStrictEnergyHandler handler;
    private final IEnergyConversion converter;

    public MekanismEnergyHandler(IStrictEnergyHandler handler) {
        this(handler, EnergyUnit.FORGE_ENERGY);
    }

    public MekanismEnergyHandler(IStrictEnergyHandler handler, IEnergyConversion converter) {
        this.handler = handler;
        this.converter = converter;
    }

    @Override
    public long getAmountAsLong() {
        long energy = 0;
        int containers = handler.getEnergyContainerCount();
        for (int i = 0; i < containers; i++) {
            long fe = converter.convertToAsInt(handler.getEnergy(i));
            if (fe > Long.MAX_VALUE - energy) {
                return Long.MAX_VALUE;
            }
            energy += fe;
        }
        return energy;
    }

    @Override
    public long getCapacityAsLong() {
        long maxEnergy = 0;
        int containers = handler.getEnergyContainerCount();
        for (int i = 0; i < containers; i++) {
            long max = converter.convertToAsInt(handler.getMaxEnergy(i));
            if (max > Long.MAX_VALUE - maxEnergy) {
                return Long.MAX_VALUE;
            }
            maxEnergy += max;
        }
        return maxEnergy;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }

        long toInsert = converter.convertFrom(amount);
        if (toInsert == 0) {
            return 0;
        }

        if (!converter.isOneToOne()) {
            // Simulate first to properly handle rounding, matching ForgeEnergyIntegration logic
            long simulatedRemainder = handler.insertEnergy(toInsert, Action.SIMULATE);
            if (simulatedRemainder == toInsert) {
                return 0;
            }
            long simulatedInserted = toInsert - simulatedRemainder;
            int fe = converter.convertToAsInt(simulatedInserted);
            toInsert = converter.convertFrom(fe);
            if (toInsert == 0L) {
                return 0;
            }
        }

        long remainder = handler.insertEnergy(toInsert, Action.EXECUTE);
        if (remainder == toInsert) {
            return 0;
        }
        long inserted = toInsert - remainder;
        return converter.convertToAsInt(inserted);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        if (amount <= 0) {
            return 0;
        }

        long toExtract = converter.convertFrom(amount);
        if (toExtract == 0) {
            return 0;
        }

        if (!converter.isOneToOne()) {
            long simulatedExtracted = handler.extractEnergy(toExtract, Action.SIMULATE);
            int fe = converter.convertToAsInt(simulatedExtracted);
            toExtract = converter.convertFrom(fe);
            if (toExtract == 0L) {
                return 0;
            }
        }

        long extracted = handler.extractEnergy(toExtract, Action.EXECUTE);
        return converter.convertToAsInt(extracted);
    }
}
