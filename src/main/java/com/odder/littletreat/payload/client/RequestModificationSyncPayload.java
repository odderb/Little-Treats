package com.odder.littletreat.payload.client;

import com.odder.littletreat.LittleTreat;
import com.odder.littletreat.payload.SyncModificationsPayload;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestModificationSyncPayload() implements CustomPacketPayload {
    public static final Type<RequestModificationSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LittleTreat.MODID, "request_modification_sync_payload"));

    public static final StreamCodec<ByteBuf, RequestModificationSyncPayload> STREAM_CODEC = StreamCodec.unit(new RequestModificationSyncPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestModificationSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                SyncModificationsPayload.syncToClient(serverPlayer);
            } else {
                LittleTreat.LOGGER.warn("RequestModificationSyncPayload is being misused, the player should always be a server player.");
            }
        });
    }
}
