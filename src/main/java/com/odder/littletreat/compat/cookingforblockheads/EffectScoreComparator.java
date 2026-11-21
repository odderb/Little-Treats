package com.odder.littletreat.compat.cookingforblockheads;

import com.odder.littletreat.codec.AttributeModificationDefinition;
import com.odder.littletreat.processing.FoodDispatcher;
import net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus;
import net.blay09.mods.cookingforblockheads.menu.comparator.ComparatorName;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Highest scoring recipes first, scored from the effects eating the dish would give. The result shown is the
 * recipe's plain output, so this is what the dish gives from its usual ingredients, not what the ingredients at hand
 * would give.
 */
public class EffectScoreComparator implements Comparator<RecipeWithStatus> {
    private final Comparator<RecipeWithStatus> fallback = new ComparatorName();
    private final ToLongFunction<List<AttributeModificationDefinition>> score;

    // The menu keeps this comparator while open and re-sorts as the kitchen changes, the score per recipe doesn't
    private final Map<ResourceLocation, Long> scores = new HashMap<>();

    public EffectScoreComparator(ToLongFunction<List<AttributeModificationDefinition>> score) {
        this.score = score;
    }

    public static EffectScoreComparator byEffectCount() {
        return new EffectScoreComparator(List::size);
    }

    /**
     * Every effect has a duration once collected, defaulting to one based on the dish's nutrition
     */
    public static EffectScoreComparator byTotalDuration() {
        return new EffectScoreComparator(effects -> effects.stream().mapToLong(effect -> effect.duration().orElse(0)).sum());
    }

    @Override
    public int compare(RecipeWithStatus first, RecipeWithStatus second) {
        int result = Long.compare(scoreFor(second), scoreFor(first));
        return result != 0 ? result : fallback.compare(first, second);
    }

    private long scoreFor(RecipeWithStatus recipe) {
        return scores.computeIfAbsent(recipe.recipeId(), id -> score.applyAsLong(FoodDispatcher.collectModifications(recipe.resultItem())));
    }
}
