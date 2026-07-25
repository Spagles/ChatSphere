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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ClientboundChannelSyncPayload(List<ModServerChannels.ChannelEntry> channels, Map<String, String> knownPlayers) implements CustomPacketPayload {
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
            buf.writeBoolean(e.showInExplore());
            List<ModServerChannels.VoiceRoom> rooms = e.voiceRooms();
            buf.writeInt(rooms.size());
            for (ModServerChannels.VoiceRoom vr : rooms) {
                writeUtf(buf, vr.name());
                writeStringList(buf, vr.members());
            }
        }
        Map<String, String> kp = p.knownPlayers != null ? p.knownPlayers : Map.of();
        buf.writeInt(kp.size());
        for (Map.Entry<String, String> entry : kp.entrySet()) {
            writeUtf(buf, entry.getKey());
            writeUtf(buf, entry.getValue());
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
            boolean showInExplore = buf.readBoolean();
            int vrCount = buf.readInt();
            List<ModServerChannels.VoiceRoom> rooms = new ArrayList<>(vrCount);
            for (int j = 0; j < vrCount; j++) {
                String vrName = readUtf(buf);
                List<String> vrMembers = readStringList(buf);
                rooms.add(new ModServerChannels.VoiceRoom(vrName, vrMembers));
            }
            list.add(new ModServerChannels.ChannelEntry(id, owner, isPublic, description, displayName, admins, muted, invited, members, inviteCode, showInExplore, rooms));
        }
        int kpSize = buf.readInt();
        Map<String, String> knownPlayers = new HashMap<>(kpSize);
        for (int i = 0; i < kpSize; i++) {
            String uuid = readUtf(buf);
            String name = readUtf(buf);
            knownPlayers.put(uuid, name);
        }
        return new ClientboundChannelSyncPayload(list, knownPlayers);
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
            history.applyServerChannels(channels, knownPlayers);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
