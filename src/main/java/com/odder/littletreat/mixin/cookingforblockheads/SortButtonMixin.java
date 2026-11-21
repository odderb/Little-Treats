package com.odder.littletreat.mixin.cookingforblockheads;

import com.odder.littletreat.compat.cookingforblockheads.ToggleSortButton;
import net.blay09.mods.cookingforblockheads.api.ISortButton;
import net.blay09.mods.cookingforblockheads.client.gui.SortButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Optional;

/**
 * Marks the sort that's showing with an arrow in its bottom right corner, pointing up when ascending, and names
 * the direction in its tooltip
 */
@Mixin(SortButton.class)
public abstract class SortButtonMixin {
    @Unique private static final int ARROW_COLOUR = 0xFFFFFFFF;
    @Unique private static final int SHADOW_COLOUR = 0xFF202020;

    @Shadow @Final private ISortButton button;

    // Blockheads sets the tooltip once, it starts out with no direction
    @Unique private Boolean littletreat$tooltipAscending = null;

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void littletreat$showDirection(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!(button instanceof ToggleSortButton toggle)) return;

        SortButton self = (SortButton) (Object) this;
        var player = Minecraft.getInstance().player;
        Optional<Boolean> ascending = self.active && player != null
                ? toggle.ascendingIfShowing(player.containerMenu)
                : Optional.empty();

        littletreat$updateTooltip(self, toggle, ascending.orElse(null));

        ascending.ifPresent(up -> {
            // Inside the button's border and bevel, which take the outer two pixels
            int x = self.getX() + 13;
            int y = self.getY() + 15;
            littletreat$drawArrow(graphics, x + 1, y + 1, up, SHADOW_COLOUR);
            littletreat$drawArrow(graphics, x, y, up, ARROW_COLOUR);
        });
    }

    /**
     * Only rebuilt when the direction changes, this runs every frame
     */
    @Unique
    private void littletreat$updateTooltip(SortButton self, ToggleSortButton toggle, Boolean ascending) {
        if (Objects.equals(ascending, littletreat$tooltipAscending)) return;
        littletreat$tooltipAscending = ascending;

        Component text = toggle.getTooltip();
        if (ascending != null) {
            text = text.copy().append(" ").append(Component.translatable(ascending
                    ? "littletreat.kitchen_sort.ascending"
                    : "littletreat.kitchen_sort.descending"));
        }
        self.setTooltip(Tooltip.create(text));
    }

    /**
     * A 5x3 triangle with its top left corner at x, y
     */
    @Unique
    private static void littletreat$drawArrow(GuiGraphics graphics, int x, int y, boolean up, int colour) {
        for (int row = 0; row < 3; row++) {
            int halfWidth = up ? row : 2 - row;
            graphics.fill(x + 2 - halfWidth, y + row, x + 3 + halfWidth, y + row + 1, colour);
        }
    }
}
