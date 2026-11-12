package com.odder.littletreat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class TreatUI {
    public static class Anchors {
        public static int hungerBarLeft()   { return guiWidth() / 2 + 10; }
        public static int hungerBarTop()    { return guiHeight() - 49; }
        public static int hungerBarWidth()  { return 81; }
        public static int hungerBarHeight() { return 10; }

        public static int healthBarLeft()   { return guiWidth() / 2 - 91; }
        public static int healthBarWidth()  { return 81; }
        public static int healthBarBottom() { return guiHeight() - 29; }

        /**
         * Mirrors the row stacking in vanilla's Gui.renderPlayerHealth, rows of hearts squish together as they grow
         */
        public static int healthBarTop(Player player) {
            float maxHealth = Math.max(player.getMaxHealth(), player.getHealth());
            int absorption = Mth.ceil(player.getAbsorptionAmount());
            int rows = Mth.ceil((maxHealth + absorption) / 2.0f / 10.0f);
            int rowHeight = Math.max(10 - (rows - 2), 3);
            return guiHeight() - 39 - (rows - 1) * rowHeight;
        }

        public static int hotbarLeft()      { return guiWidth() / 2 - 91; }
        public static int hotbarTop()       { return guiHeight() - 22; }
        public static int hotbarWidth()     { return 182; }
        public static int hotbarHeight()    { return 22; }
        public static int hotbarCenterX()   { return guiWidth() / 2; }

        private static int guiWidth()  {
            return Minecraft.getInstance().getWindow().getGuiScaledWidth();
        }

        private static int guiHeight() {
            return Minecraft.getInstance().getWindow().getGuiScaledHeight();
        }
    }

    public static int withFullAlpha(ChatFormatting clr) {
        return 0xFF000000 | clr.getColor();
    }
}
