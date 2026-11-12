package com.odder.littletreat.processing;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Stamps loot food with what its recipe would give. Eating works without this, but the stamp is what
 * lets clients on a dedicated server show the stats in tooltips.
 */
public class ComposedFoodLootModifier extends LootModifier {
    public static final MapCodec<ComposedFoodLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            LootModifier.codecStart(inst).apply(inst, ComposedFoodLootModifier::new));

    protected ComposedFoodLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> items, LootContext lootContext) {
        for (ItemStack item : items) {
            if (FoodDispatcher.isFood(item)) {
                FoodDispatcher.applyRecipeComposition(item);
            }
        }

        return items;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
