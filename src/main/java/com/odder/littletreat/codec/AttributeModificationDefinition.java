package com.odder.littletreat.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.odder.littletreat.LittleTreat;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record AttributeModificationDefinition(
        Holder<Attribute> attribute,
        double amount,
        AttributeModifier.Operation op,
        Optional<Integer> duration
) {
    private static final DecimalFormat FORMAT = Util.make(
            new DecimalFormat("#.##"),
            f -> f.setDecimalSeparatorAlwaysShown(false));

    public static final Codec<AttributeModificationDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(AttributeModificationDefinition::attribute),
            Codec.DOUBLE.fieldOf("amount").forGetter(AttributeModificationDefinition::amount),
            AttributeModifier.Operation.CODEC.fieldOf("op").forGetter(AttributeModificationDefinition::op),
            Codec.INT.optionalFieldOf("duration").forGetter(AttributeModificationDefinition::duration)
    ).apply(inst, AttributeModificationDefinition::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttributeModificationDefinition> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.holderRegistry(Registries.ATTRIBUTE), AttributeModificationDefinition::attribute,
                    ByteBufCodecs.DOUBLE, AttributeModificationDefinition::amount,
                    AttributeModifier.Operation.STREAM_CODEC, AttributeModificationDefinition::op,
                    ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), AttributeModificationDefinition::duration,
                    AttributeModificationDefinition::new);

    public static ResourceLocation createIdFor(Holder<Item> src, AttributeModificationDefinition def) {
        var path = String.format(
                "treat/%s/%s/%s",
                def.attribute().getRegisteredName().replace(":", "_"),
                def.op().getSerializedName(),
                src.getRegisteredName().replace(":", "_")
        );

        return ResourceLocation.fromNamespaceAndPath(LittleTreat.MODID, path);
    }

    public Component format() {
        double display;
        boolean percent;

        switch (op) {
            case ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL -> {
                display = amount * 100.0;
                percent = true;
            }
            default -> { display = amount; percent = false; }
        }

        boolean positive = display >= 0;
        String number = FORMAT.format(display);
        String text = (positive ? "+" : "") + number + (percent ? "%" : "");

        return Component.literal(text)
                .withStyle(positive ? ChatFormatting.BLUE : ChatFormatting.RED);
    }

    /**
     * Only one modifier applies per attribute and operation, later entries replace earlier ones
     */
    public static List<AttributeModificationDefinition> keepEffective(Collection<AttributeModificationDefinition> defs) {
        Map<List<Object>, AttributeModificationDefinition> effective = new LinkedHashMap<>();
        defs.forEach(def -> effective.put(List.of(def.attribute(), def.op()), def));

        return new ArrayList<>(effective.values());
    }

    public static int getMaxDuration(Collection<AttributeModificationDefinition> defs) {
        return defs.stream().map(def -> def.duration().orElse(0)).max(Integer::compareTo).orElse(0);
    }

    /**
     * @return The longest duration set by either, empty if neither sets one
     */
    public static Optional<Integer> longestDuration(AttributeModificationDefinition a, AttributeModificationDefinition b) {
        if (a.duration().isEmpty()) return b.duration();
        if (b.duration().isEmpty()) return a.duration();

        return Optional.of(Math.max(a.duration().get(), b.duration().get()));
    }

    public AttributeModificationDefinition withDefaultDuration(int defaultDuration) {
        return duration.isPresent() ? this : new AttributeModificationDefinition(attribute, amount, op, Optional.of(defaultDuration));
    }

    public AttributeModifier toModifier(Holder<Item> srcItem) {
        return new AttributeModifier(createIdFor(srcItem, this), amount, op);
    }
}
