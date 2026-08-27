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
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

public class FoodDispatcher {
    public static final FoodDispatcher INSTANCE =  new FoodDispatcher();

    private Queue<Tuple<ActiveModificationDefinition, ServerPlayer>> removals = new ArrayDeque<>();
    private HashMap<ResourceLocation, List<FoodDefinition>> cachedDefinitions = new HashMap<>();

    public FoodDispatcher() {}

    public static boolean isFood(ItemStack stack) {
        return stack.get(net.minecraft.core.component.DataComponents.FOOD) != null;
    }

    public static boolean isFood(Holder<Item> item) {
        return item.is(Tags.Items.FOODS);
    }

    public static List<AttributeModificationDefinition> collectModifications(ItemStack eaten) {
        List<AttributeModificationDefinition> modifications = new ArrayList<>();

        ResourceLocation loc = eaten.getItemHolder().getKey().location();

        if (INSTANCE.cachedDefinitions.containsKey(loc)) {
            var definitions = INSTANCE.cachedDefinitions.get(loc);
            modifications.addAll(definitions.stream().map(FoodDefinition::modifications).flatMap(Collection::stream).toList());
        }

        modifications.addAll(eaten.getOrDefault(DataComponents.INHERITED_MODIFICATIONS, Collections.emptyList()));

        if (Config.PROVIDE_FALLBACK_MODIFIER.get() && modifications.isEmpty()) {
            var food = eaten.get(net.minecraft.core.component.DataComponents.FOOD);
            if (food != null) {
                modifications.add(new AttributeModificationDefinition(
                        Attributes.MAX_HEALTH,
                        Math.max(Math.round(food.saturation()*0.5f), 1),
                        AttributeModifier.Operation.ADD_VALUE,
                        Math.min(food.nutrition()*1200, 9600)
                ));
            }
        }

        return modifications;
    }

    public List<FoodDefinition> getDefinition(ItemStack item) {
        return cachedDefinitions.getOrDefault(item.getItemHolder().getKey().location(), new ArrayList<>());
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

    @SubscribeEvent
    private void onDatapackSync(OnDatapackSyncEvent event) {
        RegistryAccess access = event.getPlayerList().getServer().registryAccess();
        Registry<FoodDefinition> registry = access.registryOrThrow(Registries.FOOD_DEFINITIONS);

        cachedDefinitions.clear();

        for (var entry : registry.entrySet()) {
            entry.getValue().getItems(access).forEach(item -> {
                ResourceLocation loc = item.getKey().location();

                if (!cachedDefinitions.containsKey(loc)) {
                    cachedDefinitions.put(loc, new ArrayList<>());
                }

                cachedDefinitions.get(loc).add(entry.getValue());
            });
        }

        LittleTreat.LOGGER.debug("Rebuilt Little Treat item cache, {} items counted", cachedDefinitions.size());
    }

    @SubscribeEvent
    private void onStart(PlayerInteractEvent.RightClickItem event) {
        if (!isFood(event.getItemStack())) return;

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

        ActiveModificationDefinition.updateActiveModifications(new ArrayList<>(), serverPlayer);
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
