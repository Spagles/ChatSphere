package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ServerboundChannelActionPayload(
        Action action,
        String channelId,
        UUID ownerUuid,
        boolean isPublic,
        String description,
        String displayName,
        List<String> admins,
        List<String> mutedPlayers,
        List<String> invitedPlayers,
        String inviteCode,
        boolean showInExplore,
        String replyContent,
        String replySender
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundChannelActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "channel_action"));

    public static final StreamCodec<ByteBuf, ServerboundChannelActionPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundChannelActionPayload::write, ServerboundChannelActionPayload::read);

    private static void write(ByteBuf buf, ServerboundChannelActionPayload p) {
        buf.writeInt(p.action.ordinal());
        writeUtf(buf, p.channelId);
        writeUtf(buf, p.ownerUuid.toString());
        buf.writeBoolean(p.isPublic);
        writeUtf(buf, p.description);
        writeUtf(buf, p.displayName);
        writeStringList(buf, p.admins);
        writeStringList(buf, p.mutedPlayers);
        writeStringList(buf, p.invitedPlayers);
        writeUtf(buf, p.inviteCode);
        buf.writeBoolean(p.showInExplore);
        writeUtf(buf, p.replyContent);
        writeUtf(buf, p.replySender);
    }

    private static ServerboundChannelActionPayload read(ByteBuf buf) {
        Action action = Action.values()[buf.readInt()];
        String channelId = readUtf(buf);
        UUID owner = UUID.fromString(readUtf(buf));
        boolean isPublic = buf.readBoolean();
        String description = readUtf(buf);
        String displayName = readUtf(buf);
        List<String> admins = readStringList(buf);
        List<String> muted = readStringList(buf);
        List<String> invited = readStringList(buf);
        String inviteCode = readUtf(buf);
        boolean showInExplore = buf.readBoolean();
        String replyContent = readUtf(buf);
        String replySender = readUtf(buf);
        return new ServerboundChannelActionPayload(action, channelId, owner, isPublic, description, displayName, admins, muted, invited, inviteCode, showInExplore, replyContent, replySender);
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
            var player = ctx.player();
            var server = player.getServer();
            if (server == null) return;
            ModServerChannels msc = ModServerChannels.getInstance(server);
            switch (action) {
                case CREATE -> msc.createChannel(channelId, ownerUuid, isPublic, showInExplore);
                case UPDATE_CONFIG -> msc.updateChannelConfig(channelId, isPublic, description, displayName, admins, mutedPlayers, invitedPlayers, inviteCode, ownerUuid, showInExplore);
                case JOIN_MEMBER -> {
                    if (ownerUuid != null) {
                        msc.addMemberToChannel(channelId, ownerUuid.toString());
                    }
                }
                case JOIN_BY_CODE -> {
                    if (ownerUuid != null && inviteCode != null && !inviteCode.isEmpty()) {
                        String result = msc.joinByCode(inviteCode, ownerUuid);
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                            Component msg = switch (result) {
                                case "already_member" -> Component.translatable(
                                        "screen.chatsphere.join_channel.result_already_member");
                                case "not_found" -> Component.translatable(
                                        "screen.chatsphere.join_channel.result_not_found", inviteCode);
                                default -> Component.translatable(
                                        "screen.chatsphere.join_channel.result_success");
                            };
                            sp.sendSystemMessage(msg, false);
                        }
                    }
                }
                case SEND_CHAT -> {
                    if (ownerUuid != null && !channelId.isEmpty() && !description.isEmpty()) {
                        String senderName = player.getName().getString();
                        String convType;
                        UUID targetUuid = null;
                        boolean muted = false;
                        boolean notMember = false;

                        if (channelId.contains(":")) {
                            convType = "PRIVATE";
                            String senderStr = ownerUuid.toString();
                            String[] parts = channelId.split(":");
                            String otherStr = parts[0].equals(senderStr) ? parts[1] : parts[0];
                            try { targetUuid = UUID.fromString(otherStr); } catch (Exception ignored) {}
                        } else {
                            convType = "CHANNEL";
                            var entry = msc.getChannel(channelId);
                            if (entry == null || !entry.members().contains(ownerUuid.toString())) {
                                notMember = true;
                            } else if (entry.mutedPlayers().contains(ownerUuid.toString())) {
                                muted = true;
                            }
                        }
                        if (notMember) return;
                        if (muted) {
                            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                sp.sendSystemMessage(Component.translatable("chatsphere.mute.feedback"), false);
                            }
                            return;
                        }

                        String bannedRaw = cn.sarskin.ChatSphere.config.ModServerConfig.CONFIG.bannedWords.get();
                        if (!bannedRaw.isEmpty()) {
                            String[] patterns = bannedRaw.split("\n");
                            for (String p : patterns) {
                                p = p.trim();
                                if (p.isEmpty()) continue;
                                try {
                                    if (java.util.regex.Pattern.compile(p, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(description).find()) {
                                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                            sp.sendSystemMessage(Component.translatable("chatsphere.banned_word.feedback"), false);
                                        }
                                        return;
                                    }
                                } catch (java.util.regex.PatternSyntaxException ignored) {}
                            }
                        }
                        msc.addChatMessage(senderName, ownerUuid, description, channelId, convType, replyContent, replySender);
                        long now = System.currentTimeMillis();
                        ClientboundChatPayload relay = new ClientboundChatPayload(
                                new ClientboundChatPayload.StoredMessage(senderName, ownerUuid, description, now, channelId, convType, replyContent, replySender));

                        if (targetUuid != null) {
                            ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
                            if (target != null) {
                                target.connection.send(new ClientboundCustomPayloadPacket(relay));
                            }
                        } else {
                            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                                if (!other.getUUID().equals(ownerUuid)) {
                                    other.connection.send(new ClientboundCustomPayloadPacket(relay));
                                }
                            }
                        }
                    }
                }
                case REMOVE_CHANNEL -> {
                    msc.removeChannel(channelId, ownerUuid);
                }
                case TOGGLE_MUTE -> {
                    if (!description.isEmpty() && !channelId.isEmpty()) {
                        msc.toggleMute(channelId, description, ownerUuid);
                    }
                }
                case TOGGLE_ADMIN -> {
                    if (!description.isEmpty() && !channelId.isEmpty()) {
                        msc.toggleAdmin(channelId, description, ownerUuid);
                    }
                }
                case TOGGLE_INVITE -> {
                    if (!description.isEmpty() && !channelId.isEmpty()) {
                        msc.toggleInvite(channelId, description, ownerUuid);
                    }
                }
                case KICK_MEMBER -> {
                    if (!description.isEmpty() && !channelId.isEmpty()) {
                        msc.kickMember(channelId, description, ownerUuid);
                    }
                }
                case LEAVE_CHANNEL -> {
                    msc.leaveChannel(channelId, ownerUuid);
                }
                case LIST_PUBLIC -> {
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                        var publicList = msc.getPublicChannels();
                        sp.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
                                new ClientboundPublicChannelListPayload(publicList)));
                    }
                }
                case CREATE_VOICE_ROOM -> {
                    if (!description.isEmpty()) {
                        msc.createVoiceRoom(channelId, description, ownerUuid);
                    }
                }
                case DELETE_VOICE_ROOM -> {
                    if (!description.isEmpty()) {
                        msc.deleteVoiceRoom(channelId, description, ownerUuid);
                    }
                }
                case JOIN_VOICE_ROOM -> {
                    if (!description.isEmpty()) {
                        msc.joinVoiceRoom(channelId, description, ownerUuid);
                    }
                }
                case LEAVE_VOICE_ROOM -> {
                    if (!description.isEmpty()) {
                        msc.leaveVoiceRoom(channelId, description, ownerUuid);
                    }
                }
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action { CREATE, UPDATE_CONFIG, JOIN_MEMBER, JOIN_BY_CODE, SEND_CHAT, REMOVE_CHANNEL,
        TOGGLE_MUTE, TOGGLE_ADMIN, TOGGLE_INVITE, LEAVE_CHANNEL, LIST_PUBLIC,
        CREATE_VOICE_ROOM, DELETE_VOICE_ROOM, JOIN_VOICE_ROOM, LEAVE_VOICE_ROOM,
        KICK_MEMBER }
}
