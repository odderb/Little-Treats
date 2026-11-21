package com.odder.littletreat.compat.letsdo;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.satisfy.farm_and_charm.core.recipe.CookingPotRecipe;
import net.satisfy.farm_and_charm.core.recipe.RoasterRecipe;

/**
 * Only called once Let's Do Farm & Charm is known to be loaded
 */
public final class LetsDoMealContainers {
    public static final String MOD_ID = "farm_and_charm";

    private LetsDoMealContainers() {}

    /**
     * Both machines take the serving container in its own slot, apart from the ingredients
     */
    public static ItemStack containerFor(Recipe<?> recipe) {
        if (recipe instanceof CookingPotRecipe pot && pot.isContainerRequired()) return pot.getContainerItem().copy();
        if (recipe instanceof RoasterRecipe roaster) return roaster.getContainer().copy();
        return ItemStack.EMPTY;
    }
}
