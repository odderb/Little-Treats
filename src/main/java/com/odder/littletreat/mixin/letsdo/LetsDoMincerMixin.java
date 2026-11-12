package com.odder.littletreat.mixin.letsdo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.odder.littletreat.event.RecipeAssembledEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.farm_and_charm.core.block.entity.MincerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MincerBlockEntity.class)
public abstract class LetsDoMincerMixin {
    private static final String TICK = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/farm_and_charm/core/block/entity/MincerBlockEntity;)V";

    @Unique
    private ItemStack littletreat$input = ItemStack.EMPTY;

    // The input is used up before the result is created
    @Inject(method = TICK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void littletreat$captureInput(Level level, BlockPos pos, BlockState state, MincerBlockEntity mincer, CallbackInfo ci) {
        littletreat$input = mincer.getItem(mincer.INPUT_SLOT).copy();
    }

    @ModifyExpressionValue(method = TICK, at = @At(value = "INVOKE", target = "Lnet/satisfy/farm_and_charm/core/recipe/MincerRecipe;getResultItem(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack littletreat$getResultItem(ItemStack output) {
        RecipeAssembledEvent.fromIngredient(littletreat$input, output, RecipeType.CRAFTING).post();
        return output;
    }
}
