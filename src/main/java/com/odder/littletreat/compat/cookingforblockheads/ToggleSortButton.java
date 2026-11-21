package com.odder.littletreat.compat.cookingforblockheads;

import com.odder.littletreat.mixin.cookingforblockheads.KitchenMenuAccessor;
import net.blay09.mods.cookingforblockheads.api.ISortButton;
import net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Comparator;
import java.util.Optional;

/**
 * Wraps any kitchen sort button so clicking it again while its sort is showing flips the order. Clicking it while
 * another sort is showing, or in a freshly opened kitchen, starts over in the button's usual order.
 *
 * <p>The kitchen only asks a button for its comparator when it's clicked, so that's when the direction is decided.
 */
public class ToggleSortButton implements ISortButton {
    private final ISortButton delegate;
    private final boolean ascendingByDefault;

    private Comparator<RecipeWithStatus> lastApplied;
    private boolean reversed;

    /**
     * @param ascendingByDefault whether the button's usual order runs low to high (A to Z), only used to show the
     *                           direction with an arrow
     */
    public ToggleSortButton(ISortButton delegate, boolean ascendingByDefault) {
        this.delegate = delegate;
        this.ascendingByDefault = ascendingByDefault;
    }

    @Override
    public Comparator<RecipeWithStatus> getComparator(Player player) {
        reversed = isShowing(player.containerMenu) && !reversed;

        Comparator<RecipeWithStatus> comparator = delegate.getComparator(player);
        lastApplied = reversed ? comparator.reversed() : comparator;
        return lastApplied;
    }

    /**
     * @return whether the kitchen is sorted ascending by this button, empty when another sort is showing
     */
    public Optional<Boolean> ascendingIfShowing(AbstractContainerMenu menu) {
        return isShowing(menu) ? Optional.of(ascendingByDefault != reversed) : Optional.empty();
    }

    private boolean isShowing(AbstractContainerMenu menu) {
        return lastApplied != null
                && menu instanceof KitchenMenuAccessor kitchen
                && kitchen.littletreat$getCurrentSorting() == lastApplied;
    }

    @Override
    public ResourceLocation getIcon() {
        return delegate.getIcon();
    }

    @Override
    public Component getTooltip() {
        return delegate.getTooltip();
    }

    @Override
    public int getIconTextureX() {
        return delegate.getIconTextureX();
    }

    @Override
    public int getIconTextureY() {
        return delegate.getIconTextureY();
    }
}
