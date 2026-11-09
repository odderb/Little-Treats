package com.odder.littletreat.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;

public record AttributeGenDefinition(Holder<Attribute> attribute, double min, double max) {
    public static final Codec<AttributeGenDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(AttributeGenDefinition::attribute),
            Codec.DOUBLE.fieldOf("min").forGetter(AttributeGenDefinition::min),
            Codec.DOUBLE.fieldOf("max").forGetter(AttributeGenDefinition::max)
    ).apply(inst, AttributeGenDefinition::new));
}
