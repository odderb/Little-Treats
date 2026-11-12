package com.odder.littletreat.client;

import com.odder.littletreat.Config;
import com.odder.littletreat.codec.ActiveModificationDefinition;
import com.odder.littletreat.codec.AttributeModificationDefinition;
import com.odder.littletreat.processing.FoodDispatcher;
import com.odder.littletreat.processing.FoodItemCache;
import com.odder.littletreat.processing.FoodStats;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
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
    private final FoodStats foodStats = new FoodStats();

    private boolean regenTimerActive = false;
    private int regenTimerElapsed = 0;
    private int regenTimerInterval = 1;
    private float regenCap = 0.0f;

    public void setActive(List<ActiveModificationDefinition> active) {
        this.active = active;
    }

    public void setRegenTimer(boolean active, int elapsed, int interval, float regenCap) {
        this.regenCap = regenCap;
        regenTimerActive = active;
        regenTimerElapsed = elapsed;
        regenTimerInterval = interval;
    }

    public Collection<ActiveModificationDefinition> getActive() {
        return active;
    }

    public FoodStats getFoodStats() {
        return foodStats;
    }

    // Arrives after the definitions registry and item tags, on joining and on every /reload
    @SubscribeEvent
    private void onRecipesReceived(RecipesUpdatedEvent ev) {
        var registryAccess = Minecraft.getInstance().getConnection().registryAccess();
        cache.rebuild(ev.getRecipeManager(), registryAccess);
        foodStats.rebuild(ev.getRecipeManager(), registryAccess);
    }

    @SubscribeEvent
    private void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (Config.DISABLE_VANILLA_HUNGER.get()) {
            if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) {
                event.setCanceled(true);
            }

            Minecraft mc = Minecraft.getInstance();
            boolean healthVisible = isHealthVisible(mc);

            boolean missingHealth = mc.player != null && mc.player.getHealth() < mc.player.getMaxHealth();

            if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH) && missingHealth && healthVisible) {
                GuiDraw.drawNaturalRegenProgress(
                        event.getGuiGraphics(),
                        regenCap,
                        regenTimerActive ? (float) regenTimerElapsed / regenTimerInterval : -1.0f
                );
            }
        }
    }

    @SubscribeEvent
    private void onInventoryRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof EffectRenderingInventoryScreen<?> inventoryScreen) {
            GuiGraphics gfx = event.getGuiGraphics();
            GuiDraw.drawInventoryActiveModifications(inventoryScreen, getActive(), gfx, event.getMouseX(), event.getMouseY());
        }

        // The cursor is only free while a screen is open, e.g. chat, so this is the only place hovering the hearts works
        renderHealthTooltip(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    private void renderHealthTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || !Config.DISABLE_VANILLA_HUNGER.get() || !isHealthVisible(mc)) return;

        int left = TreatUI.Anchors.healthBarLeft();
        boolean hovering = mouseX >= left
                && mouseX < left + TreatUI.Anchors.healthBarWidth()
                && mouseY >= TreatUI.Anchors.healthBarTop(player)
                && mouseY < TreatUI.Anchors.healthBarBottom();

        if (!hovering) return;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("littletreat.tooltip.natural_regen"));

        if (regenCap > 0) {
            lines.add(Component.translatable("littletreat.tooltip.natural_regen_cap", formatHearts(regenCap))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("littletreat.tooltip.natural_regen_none").withStyle(ChatFormatting.GRAY));
        }

        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(mc.font.split(line, 200));
        }

        gfx.renderTooltip(mc.font, wrapped, mouseX, mouseY);
    }

    // gameMode is null outside a world, e.g. on the title screen
    private static boolean isHealthVisible(Minecraft mc) {
        return !mc.options.hideGui && mc.gameMode != null && mc.gameMode.canHurtPlayer();
    }

    private static String formatHearts(float health) {
        float hearts = health / 2.0f;
        return hearts == Math.floor(hearts) ? String.valueOf((int) hearts) : String.format("%.1f", hearts);
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

        if (stack.is(FoodDispatcher.CLEARS_FOOD_BUFFS)) {
            event.getToolTip().add(Component.translatable("littletreat.tooltip.clears_food_buffs").withStyle(ChatFormatting.RED));
            return;
        }

        List<AttributeModificationDefinition> mods = FoodDispatcher.collectModifications(stack);

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
