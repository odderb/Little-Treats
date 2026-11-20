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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class RecipeProcessor {

    /**
     * Merges modifiers sharing an attribute and operation. The result has to be identical for the same set of
     * inputs regardless of their order, since stacks only merge when their components are equal.
     */
    public List<AttributeModificationDefinition> getFlattenModifiers(Collection<AttributeModificationDefinition> mods) {
        Map<Holder<Attribute>, Map<AttributeModifier.Operation, List<AttributeModificationDefinition>>> grouped = new LinkedHashMap<>();

        for (AttributeModificationDefinition mod : mods) {
            grouped.computeIfAbsent(mod.attribute(), attr -> new EnumMap<>(AttributeModifier.Operation.class))
                    .computeIfAbsent(mod.op(), op -> new ArrayList<>())
                    .add(mod);
        }

        List<AttributeModificationDefinition> flattened = new ArrayList<>();

        grouped.forEach((attr, byOp) -> byOp.forEach((op, group) -> {
            // Summed in a fixed order, floating point addition depends on it
            double amount = group.stream()
                    .mapToDouble(AttributeModificationDefinition::amount)
                    .sorted()
                    .sum();

            Optional<Integer> duration = group.stream()
                    .reduce(AttributeModificationDefinition::withLongestDuration)
                    .flatMap(AttributeModificationDefinition::duration);

            flattened.add(new AttributeModificationDefinition(attr, roundAmount(amount), op, duration));
        }));

        flattened.sort(MODIFIER_ORDER);
        return flattened;
    }

    private static final Comparator<AttributeModificationDefinition> MODIFIER_ORDER = Comparator
            .<AttributeModificationDefinition, String>comparing(mod -> mod.attribute().getRegisteredName())
            .thenComparing(AttributeModificationDefinition::op);

    /**
     * Different ingredient combinations reaching the same total shouldn't differ by rounding error
     */
    private static double roundAmount(double amount) {
        return Math.round(amount * 1_000_000.0) / 1_000_000.0;
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

        // An empty list isn't equal to no component, and would stop this stacking with uncrafted copies
        if (modifiers.isEmpty()) {
            output.remove(DataComponents.INHERITED_MODIFICATIONS);
            output.remove(DataComponents.INHERITED_NUTRITION);
            return;
        }

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
