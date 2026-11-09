package com.odder.littletreat.payload;

import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.client.ClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

/**
 * @param active whether a heal is actually coming, false when regen is disabled or the player is at their regen cap
 * @param elapsed ticks passed in the current wait
 * @param interval total ticks in the current wait
 */
public record SyncRegenTimerPayload(boolean active, int elapsed, int interval) implements CustomPacketPayload {
    public static final Type<SyncRegenTimerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LittleTreat.MODID, "sync_regen_timer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRegenTimerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SyncRegenTimerPayload::active,
                    ByteBufCodecs.VAR_INT, SyncRegenTimerPayload::elapsed,
                    ByteBufCodecs.VAR_INT, SyncRegenTimerPayload::interval,
                    SyncRegenTimerPayload::new
            );

    /**
     * @return true if the client would have predicted this state by counting up from the previous one
     */
    public boolean continues(@Nullable SyncRegenTimerPayload previous) {
        if (previous == null || active != previous.active) return false;

        return !active || (interval == previous.interval && elapsed == previous.elapsed + 1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncRegenTimerPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientState.INSTANCE.setRegenTimer(payload.active, payload.elapsed, payload.interval));
    }
}
