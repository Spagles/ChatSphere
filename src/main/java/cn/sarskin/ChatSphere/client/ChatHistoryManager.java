package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.stream.Collectors;

public class ChatHistoryManager {
    public static final String COMMAND_CONVERSATION_ID = "__commands__";
    public static final String DEFAULT_CHANNEL_ID = cn.sarskin.ChatSphere.ModMain.DEFAULT_CHANNEL_ID;
    private static final ChatHistoryManager INSTANCE = new ChatHistoryManager();
    private static final int MAX_HISTORY = 100;
    private static final int MAX_COMMAND_HISTORY = 50;

    private final List<ChatMessageData> messages = new ArrayList<>(MAX_HISTORY);
    private final Map<String, Component> conversationDisplayNames = new LinkedHashMap<>();
    private final Set<String> knownPrivateConversations = new LinkedHashSet<>();
    private final Set<String> knownChannels = new LinkedHashSet<>();
    private final Map<String, ChatDataStore.ChannelConfig> channelConfigs = new LinkedHashMap<>();
    private final Map<String, List<String>> commandHistory = new LinkedHashMap<>();
    private boolean loaded;
    private boolean newMessageSinceLastCheck;
    private boolean serverConnected;

    public static ChatHistoryManager getInstance() {
        return INSTANCE;
    }

    public void addMessage(Component senderName, UUID senderUuid, Component content,
                           String conversationId, ChatMessageData.ConversationType type, boolean isOwn) {
        synchronized (messages) {
            if (messages.size() >= MAX_HISTORY) {
                messages.remove(0);
            }
            messages.add(new ChatMessageData(senderName, senderUuid, content,
                    System.currentTimeMillis(), conversationId, type, isOwn));
        }
        if (type == ChatMessageData.ConversationType.CHANNEL) {
            synchronized (knownChannels) {
                knownChannels.add(conversationId);
            }
        } else {
            synchronized (conversationDisplayNames) {
                if (!conversationDisplayNames.containsKey(conversationId)) {
                    Component displayName;
                    if (isOwn && type == ChatMessageData.ConversationType.PRIVATE) {
                        displayName = resolveOtherPartyName(conversationId, senderName);
                    } else {
                        displayName = senderName;
                    }
                    conversationDisplayNames.put(conversationId, displayName);
                    knownPrivateConversations.add(conversationId);
                }
            }
        }
        if (!isOwn) {
            newMessageSinceLastCheck = true;
        }
        save();
    }

    public boolean consumeNewMessageFlag() {
        boolean flag = newMessageSinceLastCheck;
        newMessageSinceLastCheck = false;
        return flag;
    }

    public List<ChatMessageData> getMessages() {
        synchronized (messages) {
            return Collections.unmodifiableList(new ArrayList<>(messages));
        }
    }

    public List<ChatMessageData> getMessagesByConversation(String conversationId) {
        synchronized (messages) {
            return messages.stream()
                    .filter(m -> m.conversationId().equals(conversationId))
                    .collect(Collectors.toList());
        }
    }

    public List<ChatMessageData> getRecentMessages(int count) {
        synchronized (messages) {
            int size = messages.size();
            if (size == 0) return List.of();
            int from = Math.max(0, size - count);
            return List.copyOf(messages.subList(from, size));
        }
    }

    public List<ChatMessageData> getRecentMessagesByConversation(String conversationId, int count) {
        synchronized (messages) {
            return messages.stream()
                    .filter(m -> m.conversationId().equals(conversationId))
                    .skip(Math.max(0, messages.size() - count))
                    .collect(Collectors.toList());
        }
    }

    public void addChannel(String channelName, UUID ownerUuid) {
        String id = channelName.startsWith("#") ? channelName : "#" + channelName;
        synchronized (knownChannels) {
            knownChannels.add(id);
        }
        synchronized (channelConfigs) {
            if (!channelConfigs.containsKey(id)) {
                ChatDataStore.ChannelConfig cfg = new ChatDataStore.ChannelConfig();
                if (ownerUuid != null) {
                    String ownerStr = ownerUuid.toString();
                    cfg.owner = ownerStr;
                    if (!cfg.admins.contains(ownerStr)) cfg.admins.add(ownerStr);
                    if (!cfg.members.contains(ownerStr)) cfg.members.add(ownerStr);
                }
                channelConfigs.put(id, cfg);
            }
        }
        save();
    }

    public List<String> getChannelMembers(String channelId) {
        synchronized (channelConfigs) {
            ChatDataStore.ChannelConfig cfg = channelConfigs.get(channelId);
            if (cfg == null) return List.of();
            return new ArrayList<>(cfg.members);
        }
    }

    public boolean isOwner(String channelId, UUID playerUuid) {
        if (playerUuid == null) return false;
        synchronized (channelConfigs) {
            ChatDataStore.ChannelConfig cfg = channelConfigs.get(channelId);
            return cfg != null && cfg.owner.equals(playerUuid.toString());
        }
    }

    public boolean isAdmin(String channelId, UUID playerUuid) {
        if (playerUuid == null) return false;
        synchronized (channelConfigs) {
            ChatDataStore.ChannelConfig cfg = channelConfigs.get(channelId);
            return cfg != null && (cfg.owner.equals(playerUuid.toString()) || cfg.admins.contains(playerUuid.toString()));
        }
    }

    public ChatDataStore.ChannelConfig getChannelConfig(String channelId) {
        synchronized (channelConfigs) {
            return channelConfigs.computeIfAbsent(channelId, k -> new ChatDataStore.ChannelConfig());
        }
    }

    public void updateChannelConfig(String channelId, ChatDataStore.ChannelConfig config) {
        synchronized (channelConfigs) {
            channelConfigs.put(channelId, config);
        }
        if (serverConnected) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.getConnection() != null) {
                var conn = mc.getConnection().getConnection();
                conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                        new ServerboundChannelActionPayload(
                                ServerboundChannelActionPayload.Action.UPDATE_CONFIG,
                                channelId, mc.player.getUUID(),
                                config.isPublic, config.description, config.displayName,
                                new ArrayList<>(config.admins),
                                new ArrayList<>(config.mutedPlayers),
                                new ArrayList<>(config.invitedPlayers),
                                config.inviteCode)));
            }
        } else {
            save();
        }
    }

    public Map<String, ChatDataStore.ChannelConfig> getAllChannelConfigs() {
        synchronized (channelConfigs) {
            return new LinkedHashMap<>(channelConfigs);
        }
    }

    public List<String> getChannels() {
        synchronized (knownChannels) {
            return new ArrayList<>(knownChannels);
        }
    }

    public List<String> getConversationIds() {
        Set<String> ids = new LinkedHashSet<>();
        synchronized (messages) {
            for (ChatMessageData msg : messages) {
                ids.add(msg.conversationId());
            }
        }
        synchronized (knownChannels) {
            ids.addAll(knownChannels);
        }
        synchronized (knownPrivateConversations) {
            ids.addAll(knownPrivateConversations);
        }
        return new ArrayList<>(ids);
    }

    public Component getConversationDisplayName(String conversationId) {
        if (COMMAND_CONVERSATION_ID.equals(conversationId)) {
            return Component.translatable("screen.chatsphere.mod_chat.console_name");
        }
        if (conversationId.startsWith("#")) {
            synchronized (channelConfigs) {
                ChatDataStore.ChannelConfig cfg = channelConfigs.get(conversationId);
                if (cfg != null && !cfg.displayName.isEmpty()) {
                    return Component.literal(cfg.displayName);
                }
            }
            if (DEFAULT_CHANNEL_ID.equals(conversationId)) {
                return Component.translatable("screen.chatsphere.mod_chat.default_channel");
            }
            return Component.literal(conversationId.substring(1));
        }
        synchronized (conversationDisplayNames) {
            return conversationDisplayNames.getOrDefault(conversationId,
                    Component.literal(conversationId.substring(0, Math.min(conversationId.length(), 8)) + "..."));
        }
    }

    public ChatMessageData.ConversationType getConversationType(String conversationId) {
        if (COMMAND_CONVERSATION_ID.equals(conversationId)) {
            return ChatMessageData.ConversationType.COMMAND;
        }
        if (conversationId.startsWith("#")) {
            return ChatMessageData.ConversationType.CHANNEL;
        }
        return ChatMessageData.ConversationType.PRIVATE;
    }

    public boolean hasConversation(String conversationId) {
        synchronized (messages) {
            return messages.stream().anyMatch(m -> m.conversationId().equals(conversationId));
        }
    }

    public void addPrivateConversation(String conversationId, Component playerName) {
        synchronized (conversationDisplayNames) {
            if (!conversationDisplayNames.containsKey(conversationId)) {
                conversationDisplayNames.put(conversationId, playerName);
            }
        }
        synchronized (knownPrivateConversations) {
            knownPrivateConversations.add(conversationId);
        }
    }

    public void addCommandEntry(UUID playerUuid, String commandText) {
        String key = playerUuid.toString();
        synchronized (commandHistory) {
            List<String> cmds = commandHistory.computeIfAbsent(key, k -> new ArrayList<>());
            cmds.add(commandText);
            if (cmds.size() > MAX_COMMAND_HISTORY) {
                cmds.remove(0);
            }
        }
        save();
    }

    public List<String> getCommandHistory(UUID playerUuid) {
        synchronized (commandHistory) {
            return new ArrayList<>(commandHistory.getOrDefault(playerUuid.toString(), List.of()));
        }
    }

    public void addCommandMessage(Component senderName, UUID senderUuid, Component content, boolean isInput) {
        addMessage(senderName, senderUuid, content,
                COMMAND_CONVERSATION_ID, ChatMessageData.ConversationType.COMMAND, isInput);
    }

    public void clear() {
        synchronized (messages) {
            messages.clear();
        }
        synchronized (conversationDisplayNames) {
            conversationDisplayNames.clear();
        }
        synchronized (knownPrivateConversations) {
            knownPrivateConversations.clear();
        }
        synchronized (knownChannels) {
            knownChannels.clear();
        }
        synchronized (channelConfigs) {
            channelConfigs.clear();
        }
        synchronized (commandHistory) {
            commandHistory.clear();
        }
    }

    public void load() {
        if (loaded) return;
        loaded = true;
        if (serverConnected) return;
        ChatDataStore.SavedData data = ChatDataStore.load();

        synchronized (knownChannels) {
            knownChannels.clear();
            for (String ch : data.channels) {
                knownChannels.add(ch.startsWith("#") ? ch : "#" + ch);
            }
            if (knownChannels.isEmpty()) {
                knownChannels.add(DEFAULT_CHANNEL_ID);
            }
        }

        synchronized (conversationDisplayNames) {
            conversationDisplayNames.clear();
            for (Map.Entry<String, String> e : data.privateDisplayNames.entrySet()) {
                conversationDisplayNames.put(e.getKey(), Component.literal(e.getValue()));
            }
        }

        synchronized (knownPrivateConversations) {
            knownPrivateConversations.clear();
            knownPrivateConversations.addAll(data.privateDisplayNames.keySet());
        }

        synchronized (channelConfigs) {
            channelConfigs.clear();
            channelConfigs.putAll(data.channelConfigs);
        }

        synchronized (knownChannels) {
            synchronized (channelConfigs) {
                for (String ch : knownChannels) {
                    channelConfigs.computeIfAbsent(ch, k -> new ChatDataStore.ChannelConfig());
                }
            }
        }

        synchronized (commandHistory) {
            commandHistory.clear();
            commandHistory.putAll(data.commandHistory);
        }

        synchronized (messages) {
            messages.clear();
            for (ChatDataStore.SavedMessage sm : data.messages) {
                ChatMessageData.ConversationType ctype;
                if ("PRIVATE".equals(sm.conversationType())) {
                    ctype = ChatMessageData.ConversationType.PRIVATE;
                } else if ("COMMAND".equals(sm.conversationType())) {
                    ctype = ChatMessageData.ConversationType.COMMAND;
                } else {
                    ctype = ChatMessageData.ConversationType.CHANNEL;
                }
                messages.add(new ChatMessageData(
                        Component.literal(sm.senderName()),
                        sm.senderUuid(),
                        Component.literal(sm.content()),
                        sm.timestamp(),
                        sm.conversationId(),
                        ctype,
                        sm.isOwn()
                ));
            }
        }
    }

    public void save() {
        if (serverConnected) return;
        ChatDataStore.SavedData data = new ChatDataStore.SavedData();

        synchronized (knownChannels) {
            for (String ch : knownChannels) {
                data.channels.add(ch);
            }
        }

        synchronized (conversationDisplayNames) {
            for (Map.Entry<String, Component> e : conversationDisplayNames.entrySet()) {
                data.privateDisplayNames.put(e.getKey(), e.getValue().getString());
            }
        }

        synchronized (channelConfigs) {
            data.channelConfigs.clear();
            data.channelConfigs.putAll(channelConfigs);
        }

        synchronized (commandHistory) {
            data.commandHistory.clear();
            for (Map.Entry<String, List<String>> e : commandHistory.entrySet()) {
                data.commandHistory.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }

        synchronized (messages) {
            for (ChatMessageData msg : messages) {
                data.messages.add(new ChatDataStore.SavedMessage(
                        msg.senderName().getString(),
                        msg.senderUuid(),
                        msg.content().getString(),
                        msg.timestamp(),
                        msg.conversationId(),
                        msg.conversationType().name(),
                        msg.isOwn()
                ));
            }
        }

        ChatDataStore.save(data);
    }

    public boolean isServerConnected() {
        return serverConnected;
    }

    public void applyServerChannels(List<ModServerChannels.ChannelEntry> serverChannels) {
        serverConnected = true;
        synchronized (knownChannels) {
            knownChannels.clear();
            for (ModServerChannels.ChannelEntry e : serverChannels) {
                knownChannels.add(e.id());
            }
        }
        synchronized (channelConfigs) {
            channelConfigs.clear();
            for (ModServerChannels.ChannelEntry e : serverChannels) {
                ChatDataStore.ChannelConfig cfg = new ChatDataStore.ChannelConfig();
                cfg.owner = e.owner();
                cfg.isPublic = e.isPublic();
                cfg.description = e.description();
                cfg.displayName = e.displayName();
                cfg.admins.addAll(e.admins());
                cfg.mutedPlayers.addAll(e.mutedPlayers());
                cfg.invitedPlayers.addAll(e.invitedPlayers());
                cfg.members.addAll(e.members());
                cfg.inviteCode = e.inviteCode();
                channelConfigs.put(e.id(), cfg);
            }
        }
    }

    public void applyServerMessages(List<ClientboundMessageSyncPayload.StoredMessage> serverMessages, UUID localPlayerUuid) {
        serverConnected = true;
        loaded = true;
        synchronized (messages) {
            messages.clear();
            for (ClientboundMessageSyncPayload.StoredMessage sm : serverMessages) {
                boolean isOwn = localPlayerUuid != null && sm.senderUuid().equals(localPlayerUuid);
                String convId = sm.conversationId() != null ? sm.conversationId() : DEFAULT_CHANNEL_ID;
                ChatMessageData.ConversationType ctype;
                String typeStr = sm.conversationType();
                if ("COMMAND".equals(typeStr)) {
                    ctype = ChatMessageData.ConversationType.COMMAND;
                } else                 if ("PRIVATE".equals(typeStr)) {
                    ctype = ChatMessageData.ConversationType.PRIVATE;
                    synchronized (conversationDisplayNames) {
                        if (!conversationDisplayNames.containsKey(convId)) {
                            Component displayName;
                            if (isOwn) {
                                displayName = resolveOtherPartyName(convId, Component.literal(sm.senderName()));
                            } else {
                                displayName = Component.literal(sm.senderName());
                            }
                            conversationDisplayNames.put(convId, displayName);
                            knownPrivateConversations.add(convId);
                        }
                    }
                } else {
                    ctype = ChatMessageData.ConversationType.CHANNEL;
                }
                String text = sm.content();
                String senderName = sm.senderName();
                if (ctype == ChatMessageData.ConversationType.COMMAND) {
                    messages.add(new ChatMessageData(
                            Component.literal(text.isEmpty() ? senderName : text),
                            sm.senderUuid(),
                            Component.literal(""),
                            sm.timestamp(),
                            convId,
                            ctype,
                            isOwn
                    ));
                } else {
                    messages.add(new ChatMessageData(
                            Component.literal(senderName),
                            sm.senderUuid(),
                            Component.literal(text),
                            sm.timestamp(),
                            convId,
                            ctype,
                            isOwn
                    ));
                }
            }
        }
    }

    public void setServerConnected(boolean connected) {
        this.serverConnected = connected;
        if (!connected) {
            loaded = false;
        }
    }

    public static Component resolveOtherPartyName(String conversationId, Component fallback) {
        if (!conversationId.contains(":")) return fallback;
        String[] parts = conversationId.split(":");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return fallback;
        String localStr = mc.player.getUUID().toString();
        String otherUuidStr = parts[0].equals(localStr) ? parts[1] : parts[0];
        var info = mc.getConnection().getPlayerInfo(UUID.fromString(otherUuidStr));
        if (info != null) return Component.literal(info.getProfile().getName());
        return Component.literal(otherUuidStr.substring(0, 8) + "...");
    }

    public void refreshPrivateConversationDisplayNames() {
        synchronized (conversationDisplayNames) {
            List<String> toRefresh = new ArrayList<>();
            for (Map.Entry<String, Component> entry : conversationDisplayNames.entrySet()) {
                if (entry.getKey().contains(":")) {
                    toRefresh.add(entry.getKey());
                }
            }
            for (String convId : toRefresh) {
                Component resolved = resolveOtherPartyName(convId, conversationDisplayNames.get(convId));
                conversationDisplayNames.put(convId, resolved);
            }
        }
    }

    public void ensureDefaultChannel() {
        synchronized (knownChannels) {
            if (knownChannels.isEmpty()) {
                knownChannels.add(DEFAULT_CHANNEL_ID);
            }
        }
        synchronized (channelConfigs) {
            if (!channelConfigs.containsKey(DEFAULT_CHANNEL_ID)) {
                channelConfigs.put(DEFAULT_CHANNEL_ID, new ChatDataStore.ChannelConfig());
            }
        }
    }
}
