package com.odder.littletreat.compat.cookingforblockheads;

import com.odder.littletreat.LittleTreat;
import net.blay09.mods.cookingforblockheads.api.ISortButton;
import net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * A sort button in the kitchen's recipe book. To add one, add an entry to {@link #ALL}, draw its icon in the next
 * column of the sprite sheet, and add a {@code littletreat.kitchen_sort.<name>} tooltip to the lang file.
 *
 * <p>The sheet is 256x256. Each button takes a 20 pixel wide column, starting from the left, with its normal,
 * hovered and disabled states stacked top to bottom.
 */
public record LittleTreatSortButton(String name, int column, Function<Player, Comparator<RecipeWithStatus>> comparator) implements ISortButton {
    private static final ResourceLocation SPRITES = ResourceLocation.fromNamespaceAndPath(LittleTreat.MODID, "textures/gui/kitchen_sort.png");
    private static final int SIZE = 20;

    public static final List<LittleTreatSortButton> ALL = List.of(
            new LittleTreatSortButton("effects", 0, player -> EffectScoreComparator.byEffectCount()),
            new LittleTreatSortButton("duration", 1, player -> EffectScoreComparator.byTotalDuration())
    );

    @Override
    public ResourceLocation getIcon() {
        return SPRITES;
    }

    @Override
    public Component getTooltip() {
        return Component.translatable("littletreat.kitchen_sort." + name);
    }

    @Override
    public Comparator<RecipeWithStatus> getComparator(Player player) {
        return comparator.apply(player);
    }

    @Override
    public int getIconTextureX() {
        return column * SIZE;
    }

    @Override
    public int getIconTextureY() {
        return 0;
    }
}
