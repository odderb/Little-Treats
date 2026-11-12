package com.odder.littletreat.processing;

import com.odder.littletreat.Config;
import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.client.ClientState;
import com.odder.littletreat.codec.ActiveModificationDefinition;
import com.odder.littletreat.codec.AttributeModificationDefinition;
import com.odder.littletreat.codec.FoodDefinition;
import com.odder.littletreat.init.Attachments;
import com.odder.littletreat.init.DataComponents;
import com.odder.littletreat.init.Registries;
import com.odder.littletreat.payload.SyncModificationsPayload;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

public class FoodDispatcher {
    public static final FoodDispatcher INSTANCE =  new FoodDispatcher();

    /** Eating anything in this tag removes every active food buff */
    public static final TagKey<Item> CLEARS_FOOD_BUFFS = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(LittleTreat.MODID, "clears_food_buffs"));

    private final Queue<Tuple<ActiveModificationDefinition, ServerPlayer>> removals = new ArrayDeque<>();

    private final FoodItemCache itemCache = new FoodItemCache();
    private final FoodStats stats = new FoodStats();

    public FoodDispatcher() {}

    /**
     * In singleplayer the client and server share this class, so each side reads its own copy
     */
    private static FoodStats currentStats() {
        return EffectiveSide.get().isClient() ? ClientState.INSTANCE.getFoodStats() : INSTANCE.stats;
    }

    public static boolean isFood(ItemStack stack) {
        return stack.get(net.minecraft.core.component.DataComponents.FOOD) != null;
    }

    public static boolean isFood(Holder<Item> holder) {
        return holder.is(Tags.Items.FOODS);
    }

    public static List<AttributeModificationDefinition> collectModifications(ItemStack eaten) {
        List<AttributeModificationDefinition> modifications = new ArrayList<>();

        if (!isFood(eaten)) {
            return modifications;
        }

        FoodStats stats = currentStats();
        modifications.addAll(stats.getGroupModifications(eaten.getItem()));

        // Crafted food carries its real ingredients, anything else falls back to what its recipe would give
        int sourceNutrition;
        if (eaten.has(DataComponents.INHERITED_MODIFICATIONS)) {
            modifications.addAll(eaten.getOrDefault(DataComponents.INHERITED_MODIFICATIONS, Collections.emptyList()));
            sourceNutrition = eaten.getOrDefault(DataComponents.INHERITED_NUTRITION, 0);
        } else {
            var composition = stats.findComposition(eaten.getItem());
            composition.ifPresent(c -> modifications.addAll(c.modifications()));
            sourceNutrition = composition.map(RecipeComposition.Composition::nutrition).orElse(0);
        }

        // Falls back to the food's own nutrition when made from nothing edible, like bread from wheat
        int nutrition = sourceNutrition > 0 ? sourceNutrition : getNutrition(eaten);

        return AttributeModificationDefinition.keepEffective(modifications).stream()
                .map(mod -> mod.withDefaultDuration(nutrition * Config.TICKS_PER_NUTRITION.get()))
                .toList();
    }

    /**
     * Stores what an uncrafted food's recipe would give onto the item itself, so clients can show it
     */
    public static void applyRecipeComposition(ItemStack stack) {
        if (stack.has(DataComponents.INHERITED_MODIFICATIONS)) return;

        INSTANCE.stats.findComposition(stack.getItem()).ifPresent(composition -> {
            stack.set(DataComponents.INHERITED_MODIFICATIONS, composition.modifications());
            stack.set(DataComponents.INHERITED_NUTRITION, composition.nutrition());
        });
    }

    public static List<AttributeModificationDefinition> getGroupModifications(Item item) {
        return currentStats().getGroupModifications(item);
    }

    public static int getNutrition(ItemStack stack) {
        var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
    }

    /**
     * Applies the relevant modifications and attaches the active definition to the player
     * @param src
     * @param modification
     * @param player
     * @return
     */
    public ActiveModificationDefinition applyModifications(Holder<Item> src, Collection<AttributeModificationDefinition> modification, ServerPlayer player) {
        List<AttributeModificationDefinition> appliedModifiers = new ArrayList<>();

        for (AttributeModificationDefinition def : modification) {
            if (tryApplyModification(src, def, player)) {
                appliedModifiers.add(def);
            }
        }

        ActiveModificationDefinition modDef = new ActiveModificationDefinition(src, appliedModifiers);
        ActiveModificationDefinition.addActiveModification(modDef, player);

        return modDef;
    }

    public void clearAllBuffs(ServerPlayer player) {
        progressPlayerTicks(player, Integer.MAX_VALUE);
    }

    private boolean tryApplyModification(Holder<Item> src, AttributeModificationDefinition def, ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(def.attribute());

        if (attr == null) return false;

        attr.addOrUpdateTransientModifier(def.toModifier(src));

        return true;
    }

    private void progressPlayerTicks(ServerPlayer player, int ticks) {
        List<ActiveModificationDefinition> active = player.getData(Attachments.ACTIVE_MODIFICATIONS);

        for(ActiveModificationDefinition modification : active) {
            modification.progressTicks(ticks);

            if (modification.isFinished()) {
                removals.add(new Tuple<>(modification, player));
            }
        }
    }

    private ActiveModificationDefinition getActiveModificationForFood(Holder<Item> holder, Player player) {
        Collection<ActiveModificationDefinition> mods = EffectiveSide.get().isClient()
                ? ClientState.INSTANCE.getActive()
                : player.getData(Attachments.ACTIVE_MODIFICATIONS);

        return mods.stream().filter(mod -> mod.source.equals(holder)).findFirst().orElse(null);
    }

    // Machines in loaded chunks can cook before anyone joins, so the lookup has to exist from the start
    @SubscribeEvent
    private void onServerStarted(ServerStartedEvent event) {
        rebuildCaches(event.getServer());
    }

    @SubscribeEvent
    private void onDatapackSync(OnDatapackSyncEvent event) {
        rebuildCaches(event.getPlayerList().getServer());
    }

    private void rebuildCaches(MinecraftServer server) {
        RegistryAccess access = server.registryAccess();

        itemCache.rebuild(server.getRecipeManager(), access);
        stats.rebuild(server.getRecipeManager(), access);

        warnUnassigned("ingredients", itemCache.getBasicIngredients());
        warnUnassigned("edible items with no recipe", itemCache.getUncraftedFoods(access));

        LittleTreat.LOGGER.debug("Rebuilt Little Treat item cache, {} items counted", stats.size());
    }

    private void warnUnassigned(String description, Collection<Holder<Item>> items) {
        List<String> unassigned = items.stream()
                .map(item -> item.getKey().location())
                .filter(loc -> !stats.hasGroup(loc))
                .map(ResourceLocation::toString)
                .sorted()
                .toList();

        if (!unassigned.isEmpty()) {
            LittleTreat.LOGGER.warn("Found {} unassigned {}..\n{}", unassigned.size(), description, String.join(", ", unassigned));
        }
    }

    @SubscribeEvent
    private void onStart(PlayerInteractEvent.RightClickItem event) {
        if (!isFood(event.getItemStack())) return;

        // Doesn't take up a slot, and needs to work when they're all full
        if (event.getItemStack().is(CLEARS_FOOD_BUFFS)) return;

        Collection<ActiveModificationDefinition> mods = EffectiveSide.get().isClient()
                ? ClientState.INSTANCE.getActive()
                : event.getEntity().getData(Attachments.ACTIVE_MODIFICATIONS);

        // Allow eating if we already have this food in a slot
        if (getActiveModificationForFood(event.getItemStack().getItemHolder(), event.getEntity()) != null) {
            return;
        }

        int count = mods.size();
        if (count >= Config.ALLOWED_EFFECT_COUNT.get()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    private void onFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (!isFood(event.getItem())) return;

        if (event.getItem().is(CLEARS_FOOD_BUFFS)) {
            clearAllBuffs(serverPlayer);
            return;
        }

        ActiveModificationDefinition activeMod = getActiveModificationForFood(event.getItem().getItemHolder(), serverPlayer);

        // refreshes the duration, keys on food not attributes
        if (activeMod != null) {
            Collection<ActiveModificationDefinition> mods = serverPlayer.getData(Attachments.ACTIVE_MODIFICATIONS);

            for (ActiveModificationDefinition mod : mods) {
                if (mod == activeMod) {
                    mod.resetTicks();
                    break;
                }
            }

            ActiveModificationDefinition.updateActiveModifications(mods, serverPlayer);
            return;
        }

        List<AttributeModificationDefinition> modifications = collectModifications(event.getItem());

        if (modifications.isEmpty()) return;

        applyModifications(
                event.getItem().getItemHolder(),
                modifications,
                serverPlayer
        );

        SyncModificationsPayload.syncToClient(serverPlayer);
    }

    @SubscribeEvent
    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        progressPlayerTicks(serverPlayer, 1);
    }

    @SubscribeEvent
    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        for (ActiveModificationDefinition mod : serverPlayer.getData(Attachments.ACTIVE_MODIFICATIONS.get())) {
            for (AttributeModificationDefinition attrDef : mod.defs()) {
                AttributeInstance attr = serverPlayer.getAttribute(attrDef.attribute());
                if (attr != null) attr.addOrUpdateTransientModifier(attrDef.toModifier(mod.source));
            }
        }

        SyncModificationsPayload.syncToClient(serverPlayer);
    }

    @SubscribeEvent
    private void onPlayerDie(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        ActiveModificationDefinition.clearActiveModifications(serverPlayer);
    }

    @SubscribeEvent
    private void onServerTick(ServerTickEvent.Post event) {
        var next = removals.poll();

        if (next != null){
            ActiveModificationDefinition.removeActiveModification(next.getA(), next.getB());
        }

        var server = event.getServer();
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                SyncModificationsPayload.syncToClient(player);
            }
        }
    }
}
