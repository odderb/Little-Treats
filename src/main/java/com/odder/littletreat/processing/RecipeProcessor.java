package com.odder.littletreat.processing;

import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.codec.AttributeModificationDefinition;
import com.odder.littletreat.event.RecipeAssembledEvent;
import com.odder.littletreat.init.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.util.thread.EffectiveSide;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class RecipeProcessor {

    public List<AttributeModificationDefinition> getFlattenModifiers(Collection<AttributeModificationDefinition> mods) {
        HashMap<Holder<Attribute>, List<AttributeModificationDefinition>> similar = new HashMap<>();
        List<AttributeModificationDefinition> flattened = new ArrayList<>();

        for(AttributeModificationDefinition mod : mods) {
            if (!similar.containsKey(mod.attribute())) {
                similar.put(mod.attribute(), new ArrayList<>());
            }

            similar.get(mod.attribute()).add(mod);
        }

        for (Holder<Attribute> attr : similar.keySet()) {
            HashMap<AttributeModifier.Operation, List<AttributeModificationDefinition>> similarOperations = new HashMap<>();
            for (AttributeModificationDefinition mod : similar.get(attr)) {
                if (!similarOperations.containsKey(mod.op())) {
                    similarOperations.put(mod.op(), new ArrayList<>());
                }

                similarOperations.get(mod.op()).add(mod);
            }

            for (AttributeModifier.Operation op :  similarOperations.keySet()) {
                var res = similarOperations.get(op).stream().reduce(
                        (a, b) -> new AttributeModificationDefinition(
                                attr,
                                a.amount()+b.amount(),
                                op,
                                AttributeModificationDefinition.longestDuration(a, b)
                        )
                );

                res.ifPresent(flattened::add);
            }
        }

        return flattened;
    }

    /**
     * Some machines (cooking pots) fire this every tick while cooking, so it should stay cheap
     */
    @SubscribeEvent
    private void onRecipeAssembledEvent(RecipeAssembledEvent event) {
        if (!FoodDispatcher.isFood(event.getOutput())) return;
        process(event.getInputs(), event.getOutput(), event.getRecipeType());
    }

    private void process(Collection<ItemStack> inputs, ItemStack output, RecipeType<?> type) {
        if (EffectiveSide.get().isClient()) return;

        var modifiers = inputs
                .stream()
                .map(this::getModificationsFor)
                .flatMap(Collection::stream)
                .toList();

        modifiers = getFlattenModifiers(modifiers);

        output.set(DataComponents.INHERITED_MODIFICATIONS, modifiers);
        output.set(DataComponents.INHERITED_NUTRITION, inputs.stream().mapToInt(FoodDispatcher::getNutrition).sum());

        if (LittleTreat.LOGGER.isDebugEnabled()) {
            LittleTreat.LOGGER.debug(
                    "Created item {} from {} with modifications: {}",
                    output.getDisplayName().getString(),
                    type,
                    modifiers.stream().map(mod -> mod.attribute().getKey().location().toString()).collect(Collectors.joining(", "))
            );
        }
    }

    /**
     * What an ingredient contributes, including anything it inherited from its own recipe
     */
    private List<AttributeModificationDefinition> getModificationsFor(ItemStack stack) {
        List<AttributeModificationDefinition> modifications = new ArrayList<>(FoodDispatcher.getGroupModifications(stack.getItem()));
        modifications.addAll(stack.getOrDefault(DataComponents.INHERITED_MODIFICATIONS, List.of()));

        return AttributeModificationDefinition.keepEffective(modifications);
    }
}
