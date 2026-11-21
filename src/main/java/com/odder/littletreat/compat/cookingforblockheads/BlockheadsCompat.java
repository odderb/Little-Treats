package com.odder.littletreat.compat.cookingforblockheads;

import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.Warnings;
import net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI;
import net.blay09.mods.cookingforblockheads.api.ISortButton;
import net.blay09.mods.cookingforblockheads.client.gui.HungerSortButton;
import net.blay09.mods.cookingforblockheads.client.gui.SaturationSortButton;
import net.blay09.mods.cookingforblockheads.registry.CookingForBlockheadsRegistry;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Only touched once Cooking for Blockheads is known to be loaded, so its classes are never needed otherwise.
 *
 * <p>This reaches into Blockheads' internals, which a Blockheads update can move. Rather than crash, the whole
 * integration switches off if anything doesn't line up, and the host or an operator is told about it.
 */
public final class BlockheadsCompat {
    public static final String MOD_ID = "cookingforblockheads";

    // Stays off until everything has been checked, the mixins do nothing while it's off
    private static volatile boolean active = false;

    private BlockheadsCompat() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(BlockheadsCompat::onLoadComplete);
    }

    public static boolean isActive() {
        return active;
    }

    // Load complete runs after every mod's setup, so Blockheads has registered its own buttons by then
    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            List<String> failedMixins = BlockheadsMixinStatus.findFailed();
            if (!failedMixins.isEmpty()) {
                disable("these mixins didn't apply: " + String.join(", ", failedMixins));
                return;
            }

            if (setUpSortButtons()) {
                active = true;
                LittleTreat.LOGGER.info("Cooking for Blockheads integration enabled");
            }
        });
    }

    /**
     * Puts the buttons back as they were if anything goes wrong part way
     *
     * @return whether the buttons were set up
     */
    private static boolean setUpSortButtons() {
        List<ISortButton> before;
        try {
            before = new ArrayList<>(CookingForBlockheadsRegistry.getSortButtons());
        } catch (LinkageError | RuntimeException e) {
            disable("its sort buttons couldn't be read: " + e);
            return false;
        }

        try {
            hideVanillaFoodSorting();
            LittleTreatSortButton.ALL.forEach(CookingForBlockheadsAPI::addSortButton);
            makeSortingToggle();
            return true;
        } catch (LinkageError | RuntimeException e) {
            try {
                Collection<ISortButton> buttons = CookingForBlockheadsRegistry.getSortButtons();
                buttons.clear();
                buttons.addAll(before);
            } catch (LinkageError | RuntimeException ignored) {
                // Already failing, the warning below covers it
            }
            disable("its sort buttons couldn't be changed: " + e);
            return false;
        }
    }

    private static void disable(String reason) {
        active = false;
        Warnings.addStartupWarning(
                "Disabled the Cooking for Blockheads integration, " + reason,
                Component.translatable("littletreat.warning.blockheads_disabled"));
    }

    /**
     * Wraps every button, Blockheads' own name sort included, so they all flip direction the same way
     */
    private static void makeSortingToggle() {
        Collection<ISortButton> buttons = CookingForBlockheadsRegistry.getSortButtons();
        List<ISortButton> original = new ArrayList<>(buttons);

        buttons.clear();
        // Our sorts put the highest score first, Blockheads' name sort runs A to Z
        original.forEach(button -> buttons.add(new ToggleSortButton(button, !(button instanceof LittleTreatSortButton))));
    }

    /**
     * Nutrition and saturation don't reflect what food is worth here, so those buttons would only mislead.
     * The API has no way to remove a button, but the registry hands back its live list.
     */
    private static void hideVanillaFoodSorting() {
        boolean removed = CookingForBlockheadsRegistry.getSortButtons()
                .removeIf(button -> button instanceof HungerSortButton || button instanceof SaturationSortButton);

        if (!removed) {
            LittleTreat.LOGGER.warn("Couldn't find Cooking for Blockheads' hunger and saturation sort buttons to hide them");
        }
    }
}
