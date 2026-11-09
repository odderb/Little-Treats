package com.odder.littletreat.payload;

import com.odder.littletreat.payload.client.RequestModificationSyncPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class Registrar {
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar("1");

        reg.playToClient(
                SyncModificationsPayload.TYPE,
                SyncModificationsPayload.STREAM_CODEC,
                SyncModificationsPayload::handle
            );

        reg.playToClient(
                SyncRegenTimerPayload.TYPE,
                SyncRegenTimerPayload.STREAM_CODEC,
                SyncRegenTimerPayload::handle
        );

        reg.playToServer(
                RequestModificationSyncPayload.TYPE,
                RequestModificationSyncPayload.STREAM_CODEC,
                RequestModificationSyncPayload::handle
        );
    }
}
