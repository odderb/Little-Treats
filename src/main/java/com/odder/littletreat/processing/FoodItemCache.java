package com.odder.littletreat.processing;

import com.odder.littletreat.LittleTreat;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

public class FoodItemCache {
    /** Ingredients that are never expected to have a food definition, such as gold nuggets for golden carrots */
    private static final TagKey<Item> IGNORED_INGREDIENTS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(LittleTreat.MODID, "ignored_ingredients"));

    private final HashSet<Holder<Item>> meals = new HashSet<>();
    private final HashSet<Holder<Item>> ingredients = new HashSet<>();
    private final HashSet<Holder<Item>> isOutput = new HashSet<>();
    private final HashSet<Holder<Item>> containers = new HashSet<>();

    public boolean isMeal(Holder<Item> holder) {
        return meals.contains(holder);
    }

    /***
     * @return All ingredients that cannot themselves be crafted, excluding containers such as bowls and anything in the ignored tag
     */
    public HashSet<Holder<Item>> getBasicIngredients() {
        return ingredients.stream()
                .filter(ingredient -> !isOutput.contains(ingredient) && !containers.contains(ingredient) && !ingredient.is(IGNORED_INGREDIENTS))
                .collect(Collectors.toCollection(HashSet::new));
    }

    public void rebuild(RecipeManager recipeManager, RegistryAccess registryAccess) {
        meals.clear();
        ingredients.clear();
        isOutput.clear();
        containers.clear();

        var recipes = recipeManager.getRecipes();

        for (var recipe : recipes) {
            ItemStack output = recipe.value().getResultItem(registryAccess);
            if (FoodDispatcher.isFood(output)) {
                for (var input : recipe.value().getIngredients()) {
                    ingredients.addAll(Arrays.stream(input.getItems()).map(ItemStack::getItemHolder).toList());
                }
                isOutput.add(output.getItemHolder());
                collectContainers(output);
            }
        }

        for (var recipe : recipes) {
            ItemStack output = recipe.value().getResultItem(registryAccess);
            if (FoodDispatcher.isFood(output)) {
                if (!ingredients.contains(output.getItemHolder())) {
                    meals.add(output.getItemHolder());
                }
            }
        }
    }

    /**
     * Stews declare their bowl through the food component, honey bottles only as a crafting remainder.
     */
    private void collectContainers(ItemStack food) {
        food.get(DataComponents.FOOD).usingConvertsTo().ifPresent(container -> containers.add(container.getItemHolder()));

        ItemStack remainder = food.getCraftingRemainingItem();
        if (!remainder.isEmpty()) {
            containers.add(remainder.getItemHolder());
        }
    }
}
