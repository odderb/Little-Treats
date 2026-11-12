package com.odder.littletreat.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * A stat group: every item in the tag gives these modifications.
 */
public record FoodDefinition(
        TagKey<Item> tag,
        List<AttributeModificationDefinition> modifications
) {
    public static final Codec<FoodDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(FoodDefinition::tag),
            AttributeModificationDefinition.CODEC.listOf().fieldOf("modifications").forGetter(FoodDefinition::modifications)
    ).apply(inst, FoodDefinition::new));

    public HolderSet<Item> getItems(RegistryAccess access) {
        return access.registryOrThrow(Registries.ITEM).getTag(tag)
                .<HolderSet<Item>>map(items -> items)
                .orElseGet(() -> HolderSet.direct(List.of()));
    }
}
