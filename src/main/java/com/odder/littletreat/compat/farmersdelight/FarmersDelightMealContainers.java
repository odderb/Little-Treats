package com.odder.littletreat.compat.farmersdelight;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

/**
 * Only called once Farmer's Delight is known to be loaded
 */
public final class FarmersDelightMealContainers {
    private FarmersDelightMealContainers() {}

    /**
     * The cooking pot holds finished meals until they're scooped out with this, it isn't one of the ingredients
     */
    public static ItemStack containerFor(Recipe<?> recipe) {
        return recipe instanceof CookingPotRecipe pot ? pot.getOutputContainer().copy() : ItemStack.EMPTY;
    }
}
