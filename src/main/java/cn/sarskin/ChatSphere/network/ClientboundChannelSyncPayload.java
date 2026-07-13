package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ClientboundChannelSyncPayload(List<ModServerChannels.ChannelEntry> channels) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundChannelSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "channel_sync"));

    public static final StreamCodec<ByteBuf, ClientboundChannelSyncPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundChannelSyncPayload::write, ClientboundChannelSyncPayload::read);

    private static void write(ByteBuf buf, ClientboundChannelSyncPayload p) {
        buf.writeInt(p.channels.size());
        for (ModServerChannels.ChannelEntry e : p.channels) {
            writeUtf(buf, e.id());
            writeUtf(buf, e.owner());
            buf.writeBoolean(e.isPublic());
            writeUtf(buf, e.description());
            writeUtf(buf, e.displayName());
            writeStringList(buf, e.admins());
            writeStringList(buf, e.mutedPlayers());
            writeStringList(buf, e.invitedPlayers());
            writeStringList(buf, e.members());
            writeUtf(buf, e.inviteCode());
        }
    }

    private static ClientboundChannelSyncPayload read(ByteBuf buf) {
        int count = buf.readInt();
        List<ModServerChannels.ChannelEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = readUtf(buf);
            String owner = readUtf(buf);
            boolean isPublic = buf.readBoolean();
            String description = readUtf(buf);
            String displayName = readUtf(buf);
            List<String> admins = readStringList(buf);
            List<String> muted = readStringList(buf);
            List<String> invited = readStringList(buf);
            List<String> members = readStringList(buf);
            String inviteCode = readUtf(buf);
            list.add(new ModServerChannels.ChannelEntry(id, owner, isPublic, description, displayName, admins, muted, invited, members, inviteCode));
        }
        return new ClientboundChannelSyncPayload(list);
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

    private static void writeStringList(ByteBuf buf, List<String> list) {
        buf.writeInt(list.size());
        for (String s : list) writeUtf(buf, s);
    }

    private static List<String> readStringList(ByteBuf buf) {
        int n = buf.readInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(readUtf(buf));
        return list;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            history.applyServerChannels(channels);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
