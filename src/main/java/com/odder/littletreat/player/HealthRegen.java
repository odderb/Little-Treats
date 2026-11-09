package com.odder.littletreat.player;

import com.odder.littletreat.Config;
import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.codec.ActiveModificationDefinition;
import com.odder.littletreat.init.Attachments;
import com.odder.littletreat.payload.SyncRegenTimerPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import vectorwing.farmersdelight.common.registry.ModEffects;

import java.util.Collection;
import java.util.HashMap;

public class HealthRegen {
    private final float naturalRegenAmount = 1.0f;
    private HashMap<ServerPlayer, Integer> ticksSinceDamage = new HashMap<>();
    private final HashMap<ServerPlayer, SyncRegenTimerPayload> lastRegenTimers = new HashMap<>();

    public float getMaxNaturalRegenForPlayer(ServerPlayer player) {
        Collection<ActiveModificationDefinition> mods = player.getData(Attachments.ACTIVE_MODIFICATIONS);
        float maxHealth = player.getMaxHealth();
        float activeModPercent = (float)mods.size() / Config.ALLOWED_EFFECT_COUNT.get();

        return Mth.floor(maxHealth * activeModPercent);
    }

    /**
     * Regen lands on multiples of ticksPerRegen, strictly after the required wait has passed
     * @return the tick count since damage at which the first heal happens
     */
    private int getFirstRegenTick(ServerPlayer player, int ticksPerRegen) {
        int baseTicksRequiredToRegen = Config.TICKS_TO_REGEN.get();

        if (LittleTreat.FARMERS_DELIGHT_PRESENT) {
            if (player.getEffect(ModEffects.NOURISHMENT) != null) {
                baseTicksRequiredToRegen = Math.round(baseTicksRequiredToRegen*0.6f);
            }
        }

        int requiredTicksToStartRegen = (baseTicksRequiredToRegen + ticksPerRegen);

        return (requiredTicksToStartRegen / ticksPerRegen + 1) * ticksPerRegen;
    }

    private void syncRegenTimer(ServerPlayer player, SyncRegenTimerPayload timer, boolean forceResync) {
        SyncRegenTimerPayload previous = lastRegenTimers.put(player, timer);

        if (!timer.continues(previous) || (forceResync && timer.active())) {
            PacketDistributor.sendToPlayer(player, timer);
        }
    }

    private void forgetPlayer(Player player) {
        ticksSinceDamage.remove(player);
        lastRegenTimers.remove(player);
    }

    @SubscribeEvent
    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forgetPlayer(event.getEntity());
    }

    @SubscribeEvent
    private void onPlayerClone(PlayerEvent.Clone event) {
        forgetPlayer(event.getOriginal());
    }

    @SubscribeEvent
    private void onPlayerHurt(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ticksSinceDamage.put(serverPlayer, 0);
            LittleTreat.LOGGER.debug("Reset player regen timer {}", serverPlayer.getUUID());
        }
    }

    @SubscribeEvent
    private void onServerTick(ServerTickEvent.Post event) {
        for(ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            int ticks = ticksSinceDamage.getOrDefault(player, 0) + 1;

            ticksSinceDamage.put(player, ticks);

            int ticksPerRegen = Config.TICKS_PER_REGEN.get();
            int firstRegenTick = getFirstRegenTick(player, ticksPerRegen);

            boolean regenEnabled =
                    event.getServer().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)
                    && Config.DISABLE_VANILLA_HUNGER.get();

            if (regenEnabled && ticks >= firstRegenTick && ticks % ticksPerRegen == 0) {
                float maxHealableAmount = getMaxNaturalRegenForPlayer(player);

                if (player.getHealth() < maxHealableAmount) {
                    player.heal(Math.min(naturalRegenAmount, maxHealableAmount-player.getHealth()));
                }
            }

            // The wait after damage is one long interval, after that each heal is its own interval
            boolean waitingForFirstRegen = ticks < firstRegenTick;

            syncRegenTimer(
                    player,
                    new SyncRegenTimerPayload(
                            regenEnabled && player.getHealth() < getMaxNaturalRegenForPlayer(player),
                            waitingForFirstRegen ? ticks : ticks % ticksPerRegen,
                            waitingForFirstRegen ? firstRegenTick : ticksPerRegen
                    ),
                    event.getServer().getTickCount() % 20 == 0
            );
        }
    }
}
