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
        registrar.playToServer(
                ServerboundPermissionCheckPayload.TYPE,
                ServerboundPermissionCheckPayload.STREAM_CODEC,
                ServerboundPermissionCheckPayload::handle
        );
        registrar.playToClient(
                ClientboundPermissionResponsePayload.TYPE,
                ClientboundPermissionResponsePayload.STREAM_CODEC,
                ClientboundPermissionResponsePayload::handle
        );
        registrar.playToClient(
                ClientboundPublicChannelListPayload.TYPE,
                ClientboundPublicChannelListPayload.STREAM_CODEC,
                ClientboundPublicChannelListPayload::handle
        );
        registrar.playToServer(
                ServerboundConfigUpdatePayload.TYPE,
                ServerboundConfigUpdatePayload.STREAM_CODEC,
                ServerboundConfigUpdatePayload::handle
        );
    }
}
