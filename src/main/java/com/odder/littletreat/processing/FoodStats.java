package com.odder.littletreat.processing;

import com.odder.littletreat.codec.AttributeModificationDefinition;
import com.odder.littletreat.codec.FoodDefinition;
import com.odder.littletreat.init.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;

/**
 * Which stats each item gives. The server and client each build their own from the same data: the definitions
 * registry, item tags and recipes are all synced to clients, so tooltips match without extra packets.
 */
public class FoodStats {
    private final Map<ResourceLocation, List<FoodDefinition>> definitions = new HashMap<>();
    private final RecipeComposition composition = new RecipeComposition();

    public void rebuild(RecipeManager recipeManager, RegistryAccess registryAccess) {
        definitions.clear();

        registryAccess.registry(Registries.FOOD_DEFINITIONS).ifPresent(registry -> {
            for (FoodDefinition definition : registry) {
                definition.getItems(registryAccess).forEach(item ->
                        definitions.computeIfAbsent(item.getKey().location(), loc -> new ArrayList<>()).add(definition));
            }
        });

        composition.rebuild(recipeManager, registryAccess, this::getGroupModifications);
    }

    public boolean hasGroup(ResourceLocation item) {
        return definitions.containsKey(item);
    }

    public int size() {
        return definitions.size();
    }

    public List<AttributeModificationDefinition> getGroupModifications(Item item) {
        return definitions.getOrDefault(BuiltInRegistries.ITEM.getKey(item), List.of()).stream()
                .flatMap(definition -> definition.modifications().stream())
                .toList();
    }

    public Optional<RecipeComposition.Composition> findComposition(Item item) {
        return composition.find(item);
    }
}
