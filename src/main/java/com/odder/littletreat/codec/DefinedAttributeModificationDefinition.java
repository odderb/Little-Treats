package com.odder.littletreat.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.odder.littletreat.processing.FoodDispatcher;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record DefinedAttributeModificationDefinition(
        Holder<Attribute> attribute,
        AttributeModifier.Operation op,
        Optional<Double> amount,
        Optional<Integer> duration
) {
    public static final Codec<DefinedAttributeModificationDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(DefinedAttributeModificationDefinition::attribute),
            AttributeModifier.Operation.CODEC.fieldOf("op").forGetter(DefinedAttributeModificationDefinition::op),
            Codec.DOUBLE.optionalFieldOf("amount").forGetter(DefinedAttributeModificationDefinition::amount),
            Codec.INT.optionalFieldOf("duration").forGetter(DefinedAttributeModificationDefinition::duration)
    ).apply(inst, DefinedAttributeModificationDefinition::new));

    public AttributeModificationDefinition fromFood(ItemStack item) {
        if (!FoodDispatcher.isFood(item)) return null;

        var food = item.get(DataComponents.FOOD);

        int time = duration.orElse(Math.min(food.nutrition()*1200, 9600));
        double appliedAmount = amount.orElse(Math.max(Math.round(food.saturation()*0.5), 1.0));

        return new AttributeModificationDefinition(attribute, appliedAmount, op, time);
    }
}
