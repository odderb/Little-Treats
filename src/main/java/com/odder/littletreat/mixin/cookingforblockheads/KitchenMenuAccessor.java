package com.odder.littletreat.mixin.cookingforblockheads;

import net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus;
import net.blay09.mods.cookingforblockheads.menu.KitchenMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Comparator;

@Mixin(KitchenMenu.class)
public interface KitchenMenuAccessor {
    @Accessor("currentSorting")
    Comparator<RecipeWithStatus> littletreat$getCurrentSorting();
}
