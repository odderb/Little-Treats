package com.odder.littletreat.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.compat.farmersdelight.FarmersDelightMealContainers;
import com.odder.littletreat.compat.letsdo.LetsDoMealContainers;
import com.odder.littletreat.processing.FoodDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hands out recipe ingredients for testing stations and inheritance without hunting for them. Every target gets the
 * same items.
 * <ul>
 *     <li>{@code /littletreat give random <count> [targets]}: everything needed for some random meals</li>
 *     <li>{@code /littletreat give dish <item> [targets]}: the direct ingredients of one recipe for that item</li>
 * </ul>
 */
public final class GiveIngredientsCommand {
    private static final int MAX_MEALS = 64;

    private GiveIngredientsCommand() {}

    public static ArgumentBuilder<CommandSourceStack, ?> build(CommandBuildContext context) {
        return Commands.literal("give")
                .then(Commands.literal("random")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_MEALS))
                                .executes(ctx -> giveRandom(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"),
                                        List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> giveRandom(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"),
                                                EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("dish")
                        .then(Commands.argument("item", ResourceArgument.resource(context, Registries.ITEM))
                                .executes(ctx -> giveDish(ctx.getSource(), ResourceArgument.getResource(ctx, "item", Registries.ITEM),
                                        List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> giveDish(ctx.getSource(), ResourceArgument.getResource(ctx, "item", Registries.ITEM),
                                                EntityArgument.getPlayers(ctx, "targets"))))));
    }

    private static int giveRandom(CommandSourceStack source, int count, Collection<ServerPlayer> players) {
        RegistryAccess access = source.getServer().registryAccess();
        List<RecipeHolder<?>> meals = findMeals(source.getServer().getRecipeManager().getRecipes(), access);

        if (meals.isEmpty()) {
            source.sendFailure(Component.literal("No meal recipes found"));
            return 0;
        }

        List<RecipeHolder<?>> chosen = pick(meals, count, access, source.getLevel().getRandom());
        giveAll(players, chosen.stream().flatMap(holder -> itemsFor(holder.value()).stream()).toList());

        MutableComponent message = Component.literal("Gave ingredients for " + chosen.size() + " meal(s) to " + players.size() + " player(s):");
        for (RecipeHolder<?> holder : chosen) {
            message.append(Component.literal("\n - ")).append(holder.value().getResultItem(access).getHoverName())
                    .append(describeType(holder.value()));
        }
        source.sendSuccess(() -> message, true);

        return chosen.size();
    }

    /**
     * Only the recipe's direct ingredients, an ingredient that's itself crafted is given as is
     */
    private static int giveDish(CommandSourceStack source, Holder.Reference<Item> item, Collection<ServerPlayer> players) {
        RegistryAccess access = source.getServer().registryAccess();
        List<RecipeHolder<?>> recipes = source.getServer().getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value().getResultItem(access).is(item))
                .filter(holder -> !nonEmpty(holder.value()).isEmpty())
                // The recipe that can be supplied most fully, then by id so the same one is chosen every time
                .sorted(Comparator.<RecipeHolder<?>>comparingLong(holder -> unsupplied(holder.value()))
                        .thenComparing(holder -> holder.id().toString()))
                .toList();

        Component name = new ItemStack(item).getHoverName();
        if (recipes.isEmpty()) {
            source.sendFailure(Component.literal("No recipe with fixed ingredients makes ").append(name));
            return 0;
        }

        RecipeHolder<?> chosen = recipes.getFirst();
        giveAll(players, itemsFor(chosen.value()));

        MutableComponent message = Component.literal("Gave ingredients for ").append(name)
                .append(Component.literal(" to " + players.size() + " player(s), using " + chosen.id()))
                .append(describeType(chosen.value()));
        if (recipes.size() > 1) {
            message.append(Component.literal(" (1 of " + recipes.size() + " recipes)").withStyle(ChatFormatting.GRAY));
        }

        long missing = unsupplied(chosen.value());
        if (missing > 0) {
            message.append(Component.literal("\n" + missing + " ingredient(s) match no items and were left out").withStyle(ChatFormatting.YELLOW));
        }
        source.sendSuccess(() -> message, true);

        return 1;
    }

    /**
     * A meal is food made by combining things, at least one of which is food or has stats itself. This leaves out
     * single item conversions like smelting raw meat, and anything an ingredient can't actually be given for.
     *
     * <p>Special recipes aren't skipped: Let's Do marks all its machine recipes special to keep them out of the
     * recipe book. Truly dynamic ones like suspicious stew list no ingredients, so they're left out regardless.
     */
    private static List<RecipeHolder<?>> findMeals(Collection<RecipeHolder<?>> recipes, RegistryAccess access) {
        return recipes.stream()
                .filter(holder -> {
                    Recipe<?> recipe = holder.value();
                    if (!FoodDispatcher.isFood(recipe.getResultItem(access))) return false;

                    List<Ingredient> ingredients = nonEmpty(recipe);
                    return ingredients.size() >= 2
                            && unsupplied(recipe) == 0
                            && ingredients.stream().anyMatch(ingredient -> Arrays.stream(ingredient.getItems()).anyMatch(GiveIngredientsCommand::isEdibleOrHasStats));
                })
                // Recipe order isn't stable between loads, sorting keeps the pick down to the random alone
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    /**
     * Picks recipes for different meals where possible, a dish with several recipes would otherwise show up twice
     */
    private static List<RecipeHolder<?>> pick(List<RecipeHolder<?>> meals, int count, RegistryAccess access, RandomSource random) {
        List<RecipeHolder<?>> shuffled = new ArrayList<>(meals);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            shuffled.set(i, shuffled.set(j, shuffled.get(i)));
        }

        List<RecipeHolder<?>> chosen = new ArrayList<>();
        Set<Item> outputs = new HashSet<>();
        for (RecipeHolder<?> holder : shuffled) {
            if (chosen.size() >= count) break;
            if (outputs.add(holder.value().getResultItem(access).getItem())) chosen.add(holder);
        }

        return chosen;
    }

    /**
     * Prefers an option with stats for tag ingredients, so the result has something to inherit. Ingredients
     * matching no items, like an empty tag, are skipped.
     */
    private static List<ItemStack> itemsFor(Recipe<?> recipe) {
        List<ItemStack> items = new ArrayList<>();

        for (Ingredient ingredient : nonEmpty(recipe)) {
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) continue;

            ItemStack choice = Arrays.stream(options)
                    .filter(option -> !FoodDispatcher.getGroupModifications(option.getItem()).isEmpty())
                    .findFirst()
                    .orElse(options[0]);
            items.add(choice.copy());
        }

        ItemStack container = containerFor(recipe);
        if (!container.isEmpty()) items.add(container);

        return items;
    }

    private static long unsupplied(Recipe<?> recipe) {
        return nonEmpty(recipe).stream().filter(ingredient -> ingredient.getItems().length == 0).count();
    }

    /**
     * Some stations take a serving container that isn't listed among the recipe's ingredients
     */
    private static ItemStack containerFor(Recipe<?> recipe) {
        if (LittleTreat.FARMERS_DELIGHT_PRESENT) {
            ItemStack container = FarmersDelightMealContainers.containerFor(recipe);
            if (!container.isEmpty()) return container;
        }
        if (ModList.get().isLoaded(LetsDoMealContainers.MOD_ID)) {
            ItemStack container = LetsDoMealContainers.containerFor(recipe);
            if (!container.isEmpty()) return container;
        }
        return ItemStack.EMPTY;
    }

    private static List<Ingredient> nonEmpty(Recipe<?> recipe) {
        return recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
    }

    private static boolean isEdibleOrHasStats(ItemStack stack) {
        return FoodDispatcher.isFood(stack) || !FoodDispatcher.getGroupModifications(stack.getItem()).isEmpty();
    }

    private static Component describeType(Recipe<?> recipe) {
        return Component.literal(" [" + BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()) + "]").withStyle(ChatFormatting.GRAY);
    }

    private static void giveAll(Collection<ServerPlayer> players, List<ItemStack> stacks) {
        List<ItemStack> items = merge(stacks);
        for (ServerPlayer player : players) {
            for (ItemStack stack : items) {
                giveSplit(player, stack.copy());
            }
        }
    }

    /**
     * Combines repeats, so ten meals needing milk give one pile instead of ten. Tools that come back out of the
     * grid, like Croptopia's frying pan, are only given once.
     */
    private static List<ItemStack> merge(List<ItemStack> stacks) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : stacks) {
            merged.stream()
                    .filter(existing -> ItemStack.isSameItemSameComponents(existing, stack))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> { if (!isReusable(existing)) existing.grow(stack.getCount()); },
                            () -> merged.add(stack.copy()));
        }
        return merged;
    }

    private static boolean isReusable(ItemStack stack) {
        return stack.hasCraftingRemainingItem() && stack.getCraftingRemainingItem().is(stack.getItem());
    }

    /**
     * Merged piles can pass the stack limit, and whatever doesn't fit lands at the player's feet
     */
    private static void giveSplit(ServerPlayer player, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(stack.getMaxStackSize());
            if (!player.getInventory().add(part)) {
                player.drop(part, false);
            }
        }
    }
}
