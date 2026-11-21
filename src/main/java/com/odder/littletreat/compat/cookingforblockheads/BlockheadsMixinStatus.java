package com.odder.littletreat.compat.cookingforblockheads;

import com.odder.littletreat.mixin.cookingforblockheads.BlockheadsMixinPlugin;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks every Blockheads mixin took effect, see {@link BlockheadsMixinPlugin}
 */
final class BlockheadsMixinStatus {
    private record Expected(String mixin, String target, boolean clientOnly) {}

    private static final List<Expected> EXPECTED = List.of(
            new Expected("BlockheadsOvenMixin", "net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity", false),
            new Expected("BlockheadsToasterRecipeMixin", "net.blay09.mods.cookingforblockheads.recipe.ToasterRecipe", false),
            new Expected("KitchenMenuAccessor", "net.blay09.mods.cookingforblockheads.menu.KitchenMenu", false),
            new Expected("SortButtonMixin", "net.blay09.mods.cookingforblockheads.client.gui.SortButton", true)
    );

    private BlockheadsMixinStatus() {}

    /**
     * Mixins apply as their target loads, so each target is loaded first. Loading them doesn't run any of their
     * code.
     *
     * @return the mixins that didn't take effect, empty when all is well
     */
    static List<String> findFailed() {
        List<String> failed = new ArrayList<>();

        for (Expected expected : EXPECTED) {
            if (expected.clientOnly() && !FMLEnvironment.dist.isClient()) continue;

            try {
                Class.forName(expected.target(), false, BlockheadsMixinStatus.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                failed.add(expected.mixin() + " (" + expected.target() + " couldn't be loaded: " + e + ")");
                continue;
            }

            if (!Boolean.getBoolean(BlockheadsMixinPlugin.PROPERTY_PREFIX + expected.mixin())) {
                failed.add(expected.mixin());
            }
        }

        return failed;
    }
}
