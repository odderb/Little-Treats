package com.odder.littletreat.mixin.letsdo;

import com.odder.littletreat.event.RecipeAssembledEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.StoveRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(StoveBlockEntity.class)
public abstract class LetsDoStoveMixin {
    @Inject(method = "generateOutputItem", at = @At("RETURN"))
    private void littletreat$generateOutputItem(StoveRecipe recipe, RegistryAccess access, CallbackInfoReturnable<ItemStack> cir) {
        StoveBlockEntity stove = (StoveBlockEntity) (Object) this;
        var ingredients = Arrays.stream(stove.getIngredientSlots()).mapToObj(stove::getItem).toList();

        RecipeAssembledEvent.fromMatching(recipe, ingredients, cir.getReturnValue()).post();
    }
}
