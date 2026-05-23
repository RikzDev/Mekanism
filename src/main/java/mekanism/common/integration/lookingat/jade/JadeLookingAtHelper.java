package mekanism.common.integration.lookingat.jade;

import com.mojang.serialization.DataResult;
import mekanism.api.SerializationConstants;
import mekanism.common.Mekanism;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import mekanism.common.integration.lookingat.ChemicalElement;
import mekanism.common.integration.lookingat.EnergyElement;
import mekanism.common.integration.lookingat.FluidElement;
import mekanism.common.integration.lookingat.ILookingAtElement;
import mekanism.common.integration.lookingat.TextElement;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;
import mekanism.common.integration.lookingat.SimpleLookingAtHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

public class JadeLookingAtHelper extends SimpleLookingAtHelper {

    private static <B, L extends B, R extends B> MapCodec<B> alternativeElement(MapCodec<L> leftBase, MapCodec<R> rightBase,
          final Function<? super B, ? extends DataResult<? extends Either<L, R>>> from) {
        MapCodec<Either<L, R>> base = Codec.mapEither(leftBase, rightBase);
        return Codec.of(base.flatComap(from), base.map(Either::unwrap), () -> base + "[flatComapMapped]");
    }

    private static final MapCodec<ILookingAtElement> FLUID_OR_CHEMICAL_CODEC = alternativeElement(
          FluidElement.CODEC, ChemicalElement.CODEC,
          (ILookingAtElement element) -> switch (element) {
              case FluidElement e -> DataResult.success(Either.left(e));
              case ChemicalElement e -> DataResult.success(Either.right(e));
              default -> DataResult.error(() -> "Unknown Element Type");
          });
    private static final MapCodec<ILookingAtElement> ENERGY_OR_TEXT_CODEC = alternativeElement(
          EnergyElement.CODEC, TextElement.CODEC,
          (ILookingAtElement element) -> switch (element) {
              case EnergyElement e -> DataResult.success(Either.left(e));
              case TextElement e -> DataResult.success(Either.right(e));
              default -> DataResult.error(() -> "Unknown Element Type");
          });
    static final Codec<ILookingAtElement> ELEMENT_CODEC = NeoForgeExtraCodecs.withAlternative(FLUID_OR_CHEMICAL_CODEC, ENERGY_OR_TEXT_CODEC).codec();

    private final HolderLookup.Provider provider;

    public JadeLookingAtHelper(HolderLookup.Provider provider) {
        this.provider = provider;
    }

    public void finalizeData(CompoundTag data) {
        if (!elements.isEmpty()) {
            RegistryOps<Tag> registryOps = provider.createSerializationContext(NbtOps.INSTANCE);
            ListTag list = new ListTag(elements.size());
            for (ILookingAtElement element : elements) {
                DataResult<Tag> encoded = ELEMENT_CODEC.encodeStart(registryOps, element);
                encoded.ifSuccess(list::add);
                encoded.ifError(error -> Mekanism.logger.warn("Failed to serialize jade looking at data: {}", error.message()));
            }
            data.put(SerializationConstants.MEK_DATA, list);
        }
    }
}