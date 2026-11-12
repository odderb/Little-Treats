package com.odder.littletreat.command;

import com.odder.littletreat.codec.FoodDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ClientCommands {
    private static final HashSet<TagKey<Item>> requiredFoods = new HashSet<>(List.of(
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "foods")),
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "crops")),
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "vegetables")),
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "fruits"))
    ));

    private static final HashSet<String> narrowedNamespace = new HashSet<>(List.of("croptopia", "farmersdelight"));

    private static final HashSet<TagKey<Item>> blockedTags = new HashSet<>(List.of(
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("farmersdelight", "feasts"))
    ));

    @SubscribeEvent
    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("treatutil")
                        .then(Commands.literal("audit")
                                .executes(ctx -> auditFoods(ctx.getSource()))));

    }

    private static int auditFoods(CommandSourceStack source) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return 0;

        Registry<Item> registry = level.registryAccess().registryOrThrow(Registries.ITEM);

        var res = requiredFoods
                .stream()
                .map(tag -> registry.getTag(tag).orElseThrow())
                .toList();

        Set<String> missing = new HashSet<>();

        for (var set : res) {
            missing.addAll(checkHolderSet(set, level.registryAccess(), level.getRecipeManager()));
        }

        missing.forEach(s -> source.sendSystemMessage(Component.literal("  " + s)));
        source.sendSystemMessage(Component.literal(missing.size() + " missing"));

        writeAudit(missing);

        return missing.size();
    }

    private static List<String> checkHolderSet(HolderSet<Item> items, RegistryAccess access, RecipeManager recipes) {
        Registry<FoodDefinition> definitions = access.registryOrThrow(com.odder.littletreat.init.Registries.FOOD_DEFINITIONS);
        Set<Item> coveredItems = new HashSet<>();
        for (FoodDefinition def : definitions) {
            for (Holder<Item> holder : def.getItems(access)) {
                coveredItems.add(holder.value());
            }
        }

        List<String> missing = new ArrayList<>();

        for (Holder<Item> holder : items) {
            if (holder.tags().anyMatch(blockedTags::contains)) continue;

            Item item = holder.value();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

            boolean hasStat = coveredItems.contains(item);
            boolean hasRecipe = hasAnyRecipeOutputting(item, recipes, access);

            if (!hasStat && !hasRecipe && narrowedNamespace.contains(id.getNamespace())) {
                missing.add(id.toString());
            }
        }

        return missing;
    }

    private static boolean hasAnyRecipeOutputting(Item item, RecipeManager recipes,
                                                  RegistryAccess access) {
        return recipes.getRecipes().stream()
                .anyMatch(holder -> {
                    ItemStack result = holder.value().getResultItem(access);
                    return !result.isEmpty() && result.is(item);
                });
    }

    private static void writeAudit(Collection<String> missing) {
        // Written in tag format so entries can be pasted straight into a food type's tag
        String entries = missing.stream()
                .sorted()
                .map("    { \"id\": \"%s\", \"required\": false }"::formatted)
                .collect(Collectors.joining(",\n"));

        Path filePath = Minecraft.getInstance().gameDirectory.toPath().resolve("littletreat_untyped_items.json");

        try {
            Files.writeString(filePath, "{\n  \"values\": [\n" + entries + "\n  ]\n}\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
