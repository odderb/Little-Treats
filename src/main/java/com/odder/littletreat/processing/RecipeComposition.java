package com.odder.littletreat.processing;

import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.codec.AttributeModificationDefinition;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;
import java.util.function.Function;

/**
 * Works out what a food would have inherited from its recipe, for food whose craft wasn't seen
 * (loot, creative, machines we don't hook into). Crafted food carries its real ingredients instead.
 */
public class RecipeComposition {
    public record Composition(List<AttributeModificationDefinition> modifications, int nutrition) {}

    private final Map<Item, List<Recipe<?>>> recipesByOutput = new HashMap<>();
    private final Map<Item, Composition> resolved = new HashMap<>();
    private Function<Item, List<AttributeModificationDefinition>> groupModifications = item -> List.of();

    public Optional<Composition> find(Item item) {
        return Optional.ofNullable(resolved.get(item));
    }

    public void rebuild(RecipeManager recipeManager, RegistryAccess registryAccess, Function<Item, List<AttributeModificationDefinition>> groupModifications) {
        this.groupModifications = groupModifications;
        recipesByOutput.clear();
        resolved.clear();

        // Sorted so the same recipe is chosen every time
        recipeManager.getRecipes().stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .map(RecipeHolder::value)
                .forEach(recipe -> {
                    ItemStack output = recipe.getResultItem(registryAccess);
                    if (FoodDispatcher.isFood(output)) {
                        recipesByOutput.computeIfAbsent(output.getItem(), item -> new ArrayList<>()).add(recipe);
                    }
                });

        // Recipe loops are cut wherever they're first entered, so the order has to be fixed too
        recipesByOutput.keySet().stream()
                .sorted(Comparator.comparing(BuiltInRegistries.ITEM::getKey))
                .forEach(item -> resolve(item, new HashSet<>()));

        long withStats = resolved.values().stream().filter(c -> !c.modifications().isEmpty()).count();
        LittleTreat.LOGGER.debug("Composed {} foods from recipes, {} with stats", resolved.size(), withStats);
    }

    /**
     * Uses the recipe giving the most stats when there are several
     */
    private Composition resolve(Item item, Set<Item> visiting) {
        Composition known = resolved.get(item);
        if (known != null) return known;

        // Not craftable, or a recipe loop back to something already being worked out
        if (!recipesByOutput.containsKey(item) || !visiting.add(item)) return null;

        Composition best = null;
        for (Recipe<?> recipe : recipesByOutput.get(item)) {
            Composition candidate = compose(recipe, visiting);
            if (best == null || candidate.modifications().size() > best.modifications().size()) {
                best = candidate;
            }
        }

        visiting.remove(item);
        resolved.put(item, best);
        return best;
    }

    /**
     * For tag ingredients, uses the first option that gives stats
     */
    private Composition compose(Recipe<?> recipe, Set<Item> visiting) {
        List<AttributeModificationDefinition> modifications = new ArrayList<>();
        int nutrition = 0;

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;

            ItemStack chosen = null;
            List<AttributeModificationDefinition> chosenStats = List.of();
            for (ItemStack option : ingredient.getItems()) {
                List<AttributeModificationDefinition> stats = modificationsFor(option.getItem(), visiting);
                if (chosen == null || !stats.isEmpty()) {
                    chosen = option;
                    chosenStats = stats;
                }
                if (!stats.isEmpty()) break;
            }

            if (chosen == null) continue;
            modifications.addAll(chosenStats);
            nutrition += FoodDispatcher.getNutrition(chosen);
        }

        return new Composition(LittleTreat.recipeProcessor.getFlattenModifiers(modifications), nutrition);
    }

    /**
     * Matches what a crafted ingredient carries: its own group plus what its recipe gives it
     */
    private List<AttributeModificationDefinition> modificationsFor(Item item, Set<Item> visiting) {
        List<AttributeModificationDefinition> modifications = new ArrayList<>(groupModifications.apply(item));

        Composition composition = resolve(item, visiting);
        if (composition != null) modifications.addAll(composition.modifications());

        return AttributeModificationDefinition.keepEffective(modifications);
    }
}
