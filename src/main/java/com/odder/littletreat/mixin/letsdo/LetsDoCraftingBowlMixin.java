package com.odder.littletreat.mixin.letsdo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.odder.littletreat.event.RecipeAssembledEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.farm_and_charm.core.block.entity.CraftingBowlBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.CraftingBowlRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CraftingBowlBlockEntity.class)
public abstract class LetsDoCraftingBowlMixin {
    private static final String TICK = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/farm_and_charm/core/block/entity/CraftingBowlBlockEntity;)V";

    @Unique
    private List<ItemStack> littletreat$inputs = List.of();

    // The bowl's contents are used up before the result is created
    @Inject(method = TICK, at = @At(value = "INVOKE", target = "Lnet/satisfy/farm_and_charm/core/block/entity/CraftingBowlBlockEntity;findRecipe(Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private void littletreat$captureInputs(Level level, BlockPos pos, BlockState state, CraftingBowlBlockEntity bowl, CallbackInfo ci) {
        littletreat$inputs = bowl.getItems().stream().map(ItemStack::copy).toList();
    }

    @WrapOperation(method = TICK, at = @At(value = "INVOKE", target = "Lnet/satisfy/farm_and_charm/core/recipe/CraftingBowlRecipe;getResultItem(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack littletreat$getResultItem(CraftingBowlRecipe recipe, HolderLookup.Provider provider, Operation<ItemStack> original) {
        ItemStack output = original.call(recipe, provider);
        RecipeAssembledEvent.fromMatching(recipe, littletreat$inputs, output).post();
        return output;
    }
}
