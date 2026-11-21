package com.odder.littletreat.mixin.cookingforblockheads;

import com.odder.littletreat.compat.cookingforblockheads.BlockheadsCompat;
import com.odder.littletreat.event.RecipeAssembledEvent;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The oven never assembles its recipes, it copies their result item instead, so the vanilla cooking hook doesn't
 * see it. This covers both oven recipes and smelting recipes, including ones queued from the kitchen.
 *
 * <p>A redirect rather than MixinExtras' WrapOperation: those are applied after {@link BlockheadsMixinPlugin} gets
 * to check the class, so it couldn't confirm this one took effect.
 */
@Mixin(OvenBlockEntity.class)
public abstract class BlockheadsOvenMixin {
    private static final String SERVER_TICK = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V";

    @Redirect(method = SERVER_TICK, at = @At(value = "INVOKE", target = "Lnet/blay09/mods/cookingforblockheads/block/entity/OvenBlockEntity;getSmeltingResult(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack littletreat$getSmeltingResult(OvenBlockEntity oven, ItemStack input) {
        ItemStack result = oven.getSmeltingResult(input);
        if (result.isEmpty() || !BlockheadsCompat.isActive()) return result;

        // The result is the recipe's own stack, writing components onto it would leak into every later smelt
        ItemStack output = result.copy();
        RecipeAssembledEvent.fromIngredient(input, output, RecipeType.SMELTING).post();
        return output;
    }
}
