package com.odder.littletreat.mixin.letsdo;

import com.odder.littletreat.event.RecipeAssembledEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RoasterBlockEntity.class)
public abstract class LetsDoRoasterBlockEntityMixin {
    // Slots after these hold the container and finished meals
    private static final int INGREDIENT_SLOT_COUNT = 6;

    @Inject(method = "generateOutputItem", at = @At("RETURN"))
    private void littletreat$generateOutputItem(Recipe<?> recipe, RegistryAccess access, CallbackInfoReturnable<ItemStack> cir) {
        var inventory = ((RoasterBlockEntity) (Object) this).getItems();

        RecipeAssembledEvent.fromMatching(recipe, inventory.subList(0, INGREDIENT_SLOT_COUNT), cir.getReturnValue()).post();
    }
}
