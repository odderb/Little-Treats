package com.odder.littletreat.init;

import com.mojang.serialization.Codec;
import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.codec.AttributeModificationDefinition;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

public class DataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LittleTreat.MODID);

    public static final Supplier<DataComponentType<List<AttributeModificationDefinition>>> INHERITED_MODIFICATIONS =
            COMPONENTS.register("inherited_modifications", () ->
                    DataComponentType.<List<AttributeModificationDefinition>>builder()
                            .networkSynchronized(AttributeModificationDefinition.STREAM_CODEC.apply(ByteBufCodecs.list()))
                            .persistent(AttributeModificationDefinition.CODEC.listOf())
                            .build());

    /** Total nutrition of the ingredients a food was made from */
    public static final Supplier<DataComponentType<Integer>> INHERITED_NUTRITION =
            COMPONENTS.register("inherited_nutrition", () ->
                    DataComponentType.<Integer>builder()
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
                            .persistent(Codec.INT)
                            .build());
}
