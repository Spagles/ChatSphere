package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetworkSetup {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1.0");
        registrar.playToServer(
                ServerboundChannelActionPayload.TYPE,
                ServerboundChannelActionPayload.STREAM_CODEC,
                ServerboundChannelActionPayload::handle
        );
        registrar.playToClient(
                ClientboundChannelSyncPayload.TYPE,
                ClientboundChannelSyncPayload.STREAM_CODEC,
                ClientboundChannelSyncPayload::handle
        );
        registrar.playToClient(
                ClientboundMessageSyncPayload.TYPE,
                ClientboundMessageSyncPayload.STREAM_CODEC,
                ClientboundMessageSyncPayload::handle
        );
        registrar.playToClient(
                ClientboundChatPayload.TYPE,
                ClientboundChatPayload.STREAM_CODEC,
                ClientboundChatPayload::handle
        );
    }
}
