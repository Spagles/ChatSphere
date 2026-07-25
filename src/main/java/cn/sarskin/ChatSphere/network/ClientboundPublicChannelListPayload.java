package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ClientboundPublicChannelListPayload(List<PublicChannelEntry> channels) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundPublicChannelListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "public_channel_list"));

    public static final StreamCodec<ByteBuf, ClientboundPublicChannelListPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundPublicChannelListPayload::write, ClientboundPublicChannelListPayload::read);

    private static void write(ByteBuf buf, ClientboundPublicChannelListPayload p) {
        buf.writeInt(p.channels.size());
        for (PublicChannelEntry e : p.channels) {
            writeUtf(buf, e.channelId());
            writeUtf(buf, e.displayName());
            writeUtf(buf, e.description());
            buf.writeInt(e.memberCount());
            buf.writeInt(e.onlineCount());
            writeUtf(buf, e.inviteCode());
        }
    }

    private static ClientboundPublicChannelListPayload read(ByteBuf buf) {
        int count = buf.readInt();
        List<PublicChannelEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String channelId = readUtf(buf);
            String displayName = readUtf(buf);
            String description = readUtf(buf);
            int memberCount = buf.readInt();
            int onlineCount = buf.readInt();
            String inviteCode = readUtf(buf);
            list.add(new PublicChannelEntry(channelId, displayName, description, memberCount, onlineCount, inviteCode));
        }
        return new ClientboundPublicChannelListPayload(list);
    }

    private static void writeUtf(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf) {
        int len = buf.readInt();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            history.setPublicChannels(channels);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record PublicChannelEntry(String channelId, String displayName, String description,
                                     int memberCount, int onlineCount, String inviteCode) {}
}
