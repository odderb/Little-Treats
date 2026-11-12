package com.odder.littletreat.mixin.letsdo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.odder.littletreat.event.RecipeAssembledEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.satisfy.farm_and_charm.core.block.entity.SiloBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SiloBlockEntity.class)
public abstract class LetsDoSiloMixin {
    // The second stack in scope is the one just taken out of the silo to be dried
    @ModifyExpressionValue(method = "dry", at = @At(value = "INVOKE", target = "Lnet/satisfy/farm_and_charm/core/recipe/SiloRecipe;getResultItem(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack littletreat$getResultItem(ItemStack output, @Local(ordinal = 1) ItemStack input) {
        RecipeAssembledEvent.fromIngredient(input, output, RecipeType.CRAFTING).post();
        return output;
    }
}
