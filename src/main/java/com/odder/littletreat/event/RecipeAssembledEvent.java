package com.odder.littletreat.event;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class RecipeAssembledEvent extends Event {
    private final Collection<ItemStack> inputs;
    private final ItemStack output;
    private final RecipeType<?> recipeType;

    public static RecipeAssembledEvent fromIngredients(Collection<Ingredient> ingredients, ItemStack output, RecipeType<?> recipeType) {
        return new RecipeAssembledEvent(
                ingredients.stream().map(Ingredient::getItems).flatMap(Arrays::stream).toList(),
                output,
                recipeType
        );
    }

    public static RecipeAssembledEvent fromIngredients(RecipeInput recipeInput, ItemStack output, RecipeType<?> recipeType) {
        List<ItemStack> items = new ArrayList<>();
        for(int i = 0; i < recipeInput.size(); i++) {
            items.add(recipeInput.getItem(i));
        }

        return new RecipeAssembledEvent(items, output, recipeType);
    }

    public static RecipeAssembledEvent fromIngredients(SingleRecipeInput recipeInput, ItemStack output, RecipeType<?> recipeType) {
        return new RecipeAssembledEvent(List.of(recipeInput.item()),  output, recipeType);
    }

    public static RecipeAssembledEvent fromIngredient(ItemStack input, ItemStack output, RecipeType<?> recipeType) {
        return new RecipeAssembledEvent(List.of(input),  output, recipeType);
    }

    /**
     * For machines with spare slots, uses only the items the recipe actually calls for
     */
    public static RecipeAssembledEvent fromMatching(Recipe<?> recipe, Collection<ItemStack> items, ItemStack output) {
        return new RecipeAssembledEvent(
                items.stream().filter(item -> !item.isEmpty() && recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(item))).toList(),
                output,
                recipe.getType()
        );
    }

    public RecipeAssembledEvent(Collection<ItemStack> inputs, ItemStack output, RecipeType<?> recipeType) {
        this.inputs = inputs;
        this.recipeType = recipeType;
        this.output = output;
    }

    public void post() {
        NeoForge.EVENT_BUS.post(this);
    }

    public Collection<ItemStack> getInputs() {
        return inputs;
    }

    public ItemStack getOutput() {
        return output;
    }

    public RecipeType<?> getRecipeType() {
        return recipeType;
    }
}
