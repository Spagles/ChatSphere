package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import cn.sarskin.ChatSphere.server.ModServerChannels.ChannelEntry;
import cn.sarskin.ChatSphere.server.ModVoiceStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ServerboundVoicePacket(
        UUID voiceMessageId,
        String conversationId,
        String conversationType,
        UUID senderUuid,
        int frameCount,
        byte[] audioData
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundVoicePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "voice_c2s"));

    public static final StreamCodec<ByteBuf, ServerboundVoicePacket> STREAM_CODEC =
            StreamCodec.of(ServerboundVoicePacket::write, ServerboundVoicePacket::read);

    private static void write(ByteBuf buf, ServerboundVoicePacket p) {
        writeUuid(buf, p.voiceMessageId);
        writeUtf(buf, p.conversationId);
        writeUtf(buf, p.conversationType);
        writeUuid(buf, p.senderUuid);
        buf.writeInt(p.frameCount);
        buf.writeInt(p.audioData.length);
        buf.writeBytes(p.audioData);
    }

    private static ServerboundVoicePacket read(ByteBuf buf) {
        UUID voiceMessageId = readUuid(buf);
        String convId = readUtf(buf);
        String convType = readUtf(buf);
        UUID senderUuid = readUuid(buf);
        int frameCount = buf.readInt();
        int len = buf.readInt();
        byte[] audioData = new byte[len];
        buf.readBytes(audioData);
        return new ServerboundVoicePacket(voiceMessageId, convId, convType, senderUuid, frameCount, audioData);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            var server = player.getServer();
            if (server == null) return;

            String senderStr = senderUuid.toString();
            ClientboundVoicePacket relay = new ClientboundVoicePacket(
                    voiceMessageId, senderUuid, conversationId, conversationType, frameCount, audioData);
            ModVoiceStorage storage = ModVoiceStorage.getInstance(server);
            ModServerChannels msc = ModServerChannels.getInstance(server);

            // Store the chat message placeholder in server message history for sync on reconnect
            String senderName = player.getName().getString();
            msc.addChatMessage(senderName, senderUuid,
                    "VoiceMessage#" + voiceMessageId,
                    conversationId, conversationType, "", "", "");

            if ("CHANNEL".equals(conversationType)) {
                ChannelEntry entry = msc.getChannel(conversationId);
                if (entry == null) return;
                if (!entry.members().contains(senderStr)) return;
                if (entry.mutedPlayers().contains(senderStr)) return;

                for (String memberUuid : entry.members()) {
                    if (memberUuid.equals(senderStr)) continue;
                    ServerPlayer target = server.getPlayerList().getPlayer(UUID.fromString(memberUuid));
                    if (target != null) {
                        target.connection.send(new ClientboundCustomPayloadPacket(relay));
                    } else {
                        storage.store(voiceMessageId, senderStr, conversationId, conversationType, frameCount, audioData);
                    }
                }
            } else if ("PRIVATE".equals(conversationType) && conversationId != null && conversationId.contains(":")) {
                String[] parts = conversationId.split(":");
                if (parts.length != 2) return;
                String recipientStr = parts[0].equals(senderStr) ? parts[1] : parts[0];
                ServerPlayer target = server.getPlayerList().getPlayer(UUID.fromString(recipientStr));
                if (target != null) {
                    target.connection.send(new ClientboundCustomPayloadPacket(relay));
                } else {
                    storage.store(voiceMessageId, senderStr, conversationId, conversationType, frameCount, audioData);
                }
            }
        });
    }

    private static void writeUtf(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf) {
        int len = buf.readInt();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }
}
