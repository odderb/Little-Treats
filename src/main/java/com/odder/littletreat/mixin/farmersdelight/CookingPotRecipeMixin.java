package com.odder.littletreat.mixin.farmersdelight;

import com.odder.littletreat.event.RecipeAssembledEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import java.util.List;
import java.util.stream.IntStream;

@Mixin(CookingPotRecipe.class)
public abstract class CookingPotRecipeMixin {
    @Shadow abstract RecipeType<?> getType();

    @Inject(method = "assemble", at = @At("TAIL"))
    private void littletreat$assemble(RecipeWrapper inv, HolderLookup.Provider provider, CallbackInfoReturnable<ItemStack> cir) {
        // The wrapper covers the pot's whole inventory, the slots after these hold finished meals
        List<ItemStack> ingredients = IntStream.range(0, CookingPotBlockEntity.MEAL_DISPLAY_SLOT).mapToObj(inv::getItem).toList();
        new RecipeAssembledEvent(ingredients, cir.getReturnValue(), getType()).post();
    }
}
