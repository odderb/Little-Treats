package com.odder.littletreat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Problems found while loading that whoever runs the game should know about. They're always logged, and shown in
 * chat on joining to the singleplayer host or an operator, unless {@link Config#DISABLE_WARNING_MESSAGES} is set.
 */
public final class Warnings {
    private static final List<Component> STARTUP_WARNINGS = new CopyOnWriteArrayList<>();

    private Warnings() {}

    /**
     * @param logMessage written to the log straight away, in English
     * @param chatMessage shown to the people who can act on it when they join
     */
    public static void addStartupWarning(String logMessage, Component chatMessage) {
        LittleTreat.LOGGER.warn(logMessage);
        STARTUP_WARNINGS.add(chatMessage);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (STARTUP_WARNINGS.isEmpty() || Config.DISABLE_WARNING_MESSAGES.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !canActOnWarnings(player)) return;

        for (Component warning : STARTUP_WARNINGS) {
            player.sendSystemMessage(warning.copy().withStyle(ChatFormatting.YELLOW));
        }
    }

    /**
     * The singleplayer host counts even without cheats on, since they own the world
     */
    private static boolean canActOnWarnings(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        return server.isSingleplayerOwner(player.getGameProfile())
                || server.getPlayerList().isOp(player.getGameProfile());
    }
}
