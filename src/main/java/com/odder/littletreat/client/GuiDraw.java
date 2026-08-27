package com.odder.littletreat.client;

import com.odder.littletreat.Config;
import com.odder.littletreat.codec.ActiveModificationDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class GuiDraw {
    private static final ResourceLocation EFFECT_BACKGROUND_UI = ResourceLocation.withDefaultNamespace("hud/effect_background");
    private static final ResourceLocation EFFECT_BACKGROUND_LARGE_SPRITE = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_large");
    private static final ResourceLocation EFFECT_BACKGROUND_SMALL_SPRITE = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_small");
    private static final ResourceLocation HEADER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/header_separator.png");
    private static final int BACKGROUND_SPRITE_SIZE = 24;

    /**
     * Draws a bar similar to durability, but takes in a progress amount (0.0 - 1.0) to
     * display the actual amount
     * @param gfx gfx api
     * @param x x coord to draw at
     * @param y y coord to draw at
     * @param progress 0.0 - 1.0
     */
    public static void drawDurationBar(GuiGraphics gfx, int x, int y, float progress, int width, int color) {
        progress = Math.clamp(progress, 0.0f, 1.0f);
        int usedWidth = (int) (width * progress);

        gfx.fill(x, y, x + width, y + 2, 0xFF000000);
        gfx.fill(x, y, x + usedWidth, y + 2, color);
    }

    public static void drawActiveModifications(Collection<ActiveModificationDefinition> mods, GuiGraphics gfx, int x, int y, boolean horizontal) {
        for(ActiveModificationDefinition activeModificationDefinition : mods) {
            // client state can get stale, skip drawing anything under zero (local state will be eventually correct)
            if (activeModificationDefinition.remainingTicks <= 0) continue;

            if (horizontal) {
                x += drawActiveModification(gfx, activeModificationDefinition, x, y);
            } else {
                y += drawActiveModification(gfx, activeModificationDefinition, x, y);
            }
        }
    }

    // Much of this borrows logic from 'EffectRenderingInventoryScreen.renderEffects(...)'
    public static void drawInventoryActiveModifications(EffectRenderingInventoryScreen<?> screen, Collection<ActiveModificationDefinition> mods, GuiGraphics gfx, int mouseX, int mouseY){
        int x = screen.getGuiLeft()+screen.getXSize() + 2;
        int width = screen.width-x;

        if (width < 32) return;

        boolean drawLarge = width >= 120;
        ScreenEvent.RenderInventoryMobEffects event = ClientHooks.onScreenPotionSize(screen, width, !drawLarge, x);

        if (event.isCanceled()) {
            return;
        }

        Collection<MobEffectInstance> effects = Minecraft.getInstance().player.getActiveEffects();
        int top = screen.getGuiTop();

        if (!Config.IGNORE_POTION_RENDERING.get()) {
            top += effects.size() * 33;
        }

        drawLarge = !event.isCompact();
        x = event.getHorizontalOffset();

        int k = 33;
        if (mods.size() > 5) {
            k = 132 / (mods.size()-1);
        }

        if (!mods.isEmpty() && !effects.isEmpty() && !Config.IGNORE_POTION_RENDERING.get()) {
            top += 2;
            int sepWidth = drawLarge ? 120 : 32;
            gfx.blit(HEADER_SEPARATOR, x, top, 0, 0, sepWidth, 2, 32, 2);
            top += 4;
        }

        renderBackgrounds(gfx, x, k, mods, drawLarge, top);
        renderIcons(gfx, x, k, mods, top);

        if (drawLarge) {
            renderLabels(gfx, x, k, mods, top);
        }

        int trackingY = top;
        for (ActiveModificationDefinition activeModificationDefinition : mods) {
            if (isMouseHoveringStatus(mouseX, mouseY, x, trackingY, drawLarge)) {
                List<Component> modifiers = activeModificationDefinition.defs
                        .stream()
                        .<Component>map(def -> Component.translatable(def.attribute().value().getDescriptionId()).append(": ").append(def.format()))
                        .toList();

                String time = StringUtil.formatTickDuration(activeModificationDefinition.remainingTicks(), Minecraft.getInstance().level.tickRateManager().tickrate());
                var outputs = new ArrayList<>(modifiers);
                outputs.add(Component.literal(String.format("(%s)", time)).withStyle(ChatFormatting.GRAY));
                gfx.renderTooltip(Minecraft.getInstance().font, outputs, Optional.empty(), mouseX, mouseY);
            }

            trackingY += k;
        }
    }

    private static boolean isMouseHoveringStatus(int mouseX, int mouseY, int x, int y, boolean drawLarge) {
        if (drawLarge) {
            return mouseX >= x
                && mouseX < x + 120
                && mouseY >= y
                && mouseY < y + 32;
        } else {
            return mouseX >= x
                    && mouseX < x + 32
                    && mouseY >= y
                    && mouseY < y + 32;
        }
    }

    private static int drawActiveModification(GuiGraphics gfx, ActiveModificationDefinition def, int x, int y) {
        gfx.blitSprite(EFFECT_BACKGROUND_UI, x, y, BACKGROUND_SPRITE_SIZE, BACKGROUND_SPRITE_SIZE);
        int usedY = y+4;
        int clr = TreatUI.withFullAlpha(ChatFormatting.GREEN);
        if (def.getRemainingPercent() <= Config.LOW_FOOD_WARNING_PERCENT.get()) {
            usedY += Math.sin((x + y + Minecraft.getInstance().level.getGameTime())*0.1);
            clr = TreatUI.withFullAlpha(ChatFormatting.YELLOW);
        }

        gfx.renderItem(def.getItem(), x+4,usedY);
        GuiDraw.drawDurationBar(gfx, x+2, y + 20, def.getRemainingPercent(), 20, clr);
        return BACKGROUND_SPRITE_SIZE + Config.EFFECT_MARGIN.get();
    }

    private static void renderBackgrounds(GuiGraphics guiGraphics, int renderX, int yOffset, Iterable<ActiveModificationDefinition> mods, boolean isSmall, int topPos) {
        int i = topPos;

        for (ActiveModificationDefinition mod : mods) {
            if (isSmall) {
                guiGraphics.blitSprite(EFFECT_BACKGROUND_LARGE_SPRITE, renderX, i, 120, 32);
            } else {
                guiGraphics.blitSprite(EFFECT_BACKGROUND_SMALL_SPRITE, renderX, i, 32, 32);
            }

            i += yOffset;
        }
    }

    private static void renderIcons(GuiGraphics gfx, int x, int yOffset, Iterable<ActiveModificationDefinition> mods, int topPos) {
        int i = topPos;

        for(ActiveModificationDefinition mod : mods) {
            gfx.renderItem(mod.getItem(), x+7,i+7);
            i += yOffset;
        }
    }

    private static void renderLabels(GuiGraphics guiGraphics, int renderX, int yOffset, Iterable<ActiveModificationDefinition> mods, int topPos) {
        int i = topPos;
        Font font = Minecraft.getInstance().font;

        for(ActiveModificationDefinition mod : mods) {
            Component component = mod.source.value().getDescription();
            guiGraphics.drawString(font, component, renderX + 10 + 18, i + 6, 16777215);
            String remaining = StringUtil.formatTickDuration(mod.remainingTicks(), Minecraft.getInstance().level.tickRateManager().tickrate());
            guiGraphics.drawString(font, Component.literal(remaining), renderX + 10 + 18, i + 6 + 10, 8355711);
            i += yOffset;
        }
    }
}
