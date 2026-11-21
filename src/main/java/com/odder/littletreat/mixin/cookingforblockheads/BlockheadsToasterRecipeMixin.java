package com.odder.littletreat.mixin.cookingforblockheads;

import com.odder.littletreat.compat.cookingforblockheads.BlockheadsCompat;
import com.odder.littletreat.event.RecipeAssembledEvent;
import net.blay09.mods.cookingforblockheads.recipe.ToasterRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Plain bread is toasted by copying the stack, so it keeps its components without help. This covers the
 * datapack driven toaster recipes.
 */
@Mixin(ToasterRecipe.class)
public abstract class BlockheadsToasterRecipeMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void littletreat$assemble(SingleRecipeInput input, HolderLookup.Provider provider, CallbackInfoReturnable<ItemStack> cir) {
        if (!BlockheadsCompat.isActive()) return;

        ToasterRecipe recipe = (ToasterRecipe) (Object) this;
        RecipeAssembledEvent.fromIngredients(input, cir.getReturnValue(), recipe.getType()).post();
    }
}
