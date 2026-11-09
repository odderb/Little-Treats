package com.odder.littletreat.client;

import com.odder.littletreat.Config;
import com.odder.littletreat.codec.ActiveModificationDefinition;
import com.odder.littletreat.codec.AttributeModificationDefinition;
import com.odder.littletreat.processing.FoodDispatcher;
import com.odder.littletreat.processing.FoodItemCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ClientState {
    public static final ClientState INSTANCE = new ClientState();

    private List<ActiveModificationDefinition> active = new ArrayList<>();
    private final FoodItemCache cache = new FoodItemCache();

    private boolean regenTimerActive = false;
    private int regenTimerElapsed = 0;
    private int regenTimerInterval = 1;

    public void setActive(List<ActiveModificationDefinition> active) {
        this.active = active;
    }

    public void setRegenTimer(boolean active, int elapsed, int interval) {
        regenTimerActive = active;
        regenTimerElapsed = elapsed;
        regenTimerInterval = interval;
    }

    public Collection<ActiveModificationDefinition> getActive() {
        return active;
    }

    @SubscribeEvent
    private void onRecipesReceived(RecipesUpdatedEvent ev) {
        cache.rebuild(ev.getRecipeManager(), Minecraft.getInstance().getConnection().registryAccess());
    }

    @SubscribeEvent
    private void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (Config.DISABLE_VANILLA_HUNGER.get()) {
            if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) {
                event.setCanceled(true);
            }

            Minecraft mc = Minecraft.getInstance();
            boolean healthVisible = !mc.options.hideGui && mc.gameMode.canHurtPlayer();

            if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH) && regenTimerActive && healthVisible) {
                GuiDraw.drawNaturalRegenProgress(event.getGuiGraphics(), (float) regenTimerElapsed / regenTimerInterval);
            }
        }
    }

    @SubscribeEvent
    private void onInventoryRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof EffectRenderingInventoryScreen<?> inventoryScreen) {
            GuiGraphics gfx = event.getGuiGraphics();
            GuiDraw.drawInventoryActiveModifications(inventoryScreen, getActive(), gfx, event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    private void onClientTick(ClientTickEvent.Post event) {
        for(ActiveModificationDefinition activeModificationDefinition : active) {
            activeModificationDefinition.remainingTicks--;
        }

        if (regenTimerActive && regenTimerElapsed < regenTimerInterval && !Minecraft.getInstance().isPaused()) {
            regenTimerElapsed++;
        }
    }

    @SubscribeEvent
    private void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;

        GuiGraphics gui = event.getGuiGraphics();

        int y = TreatUI.Anchors.hotbarTop() - 6;
        int x = TreatUI.Anchors.hotbarLeft() + TreatUI.Anchors.hotbarWidth() + 24;
        GuiDraw.drawActiveModifications(getActive(), gui, x, y, true);
    }

    @SubscribeEvent
    private void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<AttributeModificationDefinition> mods = FoodDispatcher.collectModifications(stack, false);

        if (mods.isEmpty()) {
            if (cache.isMeal(stack.getItemHolder())) {
                Component input = Component.translatable("littletreat.tooltip.dynamic_food");
                event.getToolTip().add(input);
            }

            return;
        }

        for (AttributeModificationDefinition mod : mods) {
            Component value = mod.format();

            event.getToolTip().add(
                    Component.translatable(mod.attribute().value().getDescriptionId()).append(": ").append(value));
        }

        int duration = ActiveModificationDefinition.calculateDuration(mods);
        String time = StringUtil.formatTickDuration(duration, Minecraft.getInstance().level.tickRateManager().tickrate());
        event.getToolTip().add(Component.literal(String.format("(%s)", time)).withStyle(ChatFormatting.GRAY));
    }
}
