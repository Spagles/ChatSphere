package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import cn.sarskin.ChatSphere.storage.ModStoragePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChatHistoryManager {
    public static final String COMMAND_CONVERSATION_ID = "__commands__";
    public static final String DEFAULT_CHANNEL_ID = cn.sarskin.ChatSphere.ModMain.DEFAULT_CHANNEL_ID;
    private static final ChatHistoryManager INSTANCE = new ChatHistoryManager();
    private static final int MAX_COMMAND_HISTORY = 50;

    private final List<ChatMessageData> messages = new ArrayList<>();
    private final Map<String, Component> conversationDisplayNames = new LinkedHashMap<>();
    private final Set<String> knownPrivateConversations = new LinkedHashSet<>();
    private final Set<String> knownChannels = new LinkedHashSet<>();
    private final Map<String, ChatDataStore.ChannelConfig> channelConfigs = new LinkedHashMap<>();
    private final Map<String, List<String>> commandHistory = new LinkedHashMap<>();
    private final Map<String, String> knownPlayers = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private List<ClientboundPublicChannelListPayload.PublicChannelEntry> publicChannels;
    private boolean publicChannelsDirty;
    private boolean loaded;
    private boolean newMessageSinceLastCheck;
    private boolean serverConnected;
    private volatile boolean saveDirty;
    private ScheduledFuture<?> pendingSave;
    private int bridgeProtocolVersion;
    private String bridgeVersion;
    private int bridgeCapabilities;
    private Set<String> bridgeOnlinePlayers = Set.of();
    private static final ScheduledExecutorService SAVE_TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ChatSphere-SaveTimer");
        t.setDaemon(true);
        return t;
    });
    private String savedInput;
    private final List<String> blockedPlayers = new ArrayList<>();
    private final List<Runnable> channelConfigChangeListeners = new ArrayList<>();

    public void addChannelConfigChangeListener(Runnable listener) {
        synchronized (channelConfigChangeListeners) {
            channelConfigChangeListeners.add(listener);
        }
    }

    public void removeChannelConfigChangeListener(Runnable listener) {
        synchronized (channelConfigChangeListeners) {
            channelConfigChangeListeners.remove(listener);
        }
    }
    private String pendingReplyContent;
    private String pendingReplySender;

    public static ChatHistoryManager getInstance() {
        return INSTANCE;
    }

    public void addMessage(Component senderName, UUID senderUuid, Component content,
                           String conversationId, ChatMessageData.ConversationType type, boolean isOwn) {
        addMessage(senderName, senderUuid, content, conversationId, type, isOwn, null, null);
    }

    public void addMessage(Component senderName, UUID senderUuid, Component content,
                           String conversationId, ChatMessageData.ConversationType type, boolean isOwn,
                           String replyContent, String replySender) {
        String contentStr = content.getString();
        synchronized (messages) {
            if (ModServerConfig.CONFIG.antiSpam.get() && !messages.isEmpty()) {
                ChatMessageData last = null;
                for (int i = messages.size() - 1; i >= 0; i--) {
                    ChatMessageData m = messages.get(i);
                    if (m.conversationId().equals(conversationId)) {
                        last = m;
                        break;
                    }
                }
                if (last != null && last.senderName().getString().equals(senderName.getString())
                        && last.content().getString().equals(contentStr)
                        && Objects.equals(last.replyContent(), replyContent)
                        && Objects.equals(last.replySender(), replySender)) {
                    last.setDuplicateCount(last.duplicateCount() + 1);
                    newMessageSinceLastCheck = true;
                    if (!isOwn) notifySoundForMessage(content, type);
                    return;
                }
            }
            if (messages.size() >= ModServerConfig.CONFIG.maxChatHistory.get()) {
                messages.remove(0);
            }
            ChatMessageData msg = new ChatMessageData(senderName, senderUuid, content,
                    System.currentTimeMillis(), conversationId, type, isOwn);
            if (replyContent != null && replySender != null) {
                msg = msg.withReply(replyContent, replySender);
            } else if (isOwn && pendingReplyContent != null) {
                msg = msg.withReply(pendingReplyContent, pendingReplySender);
                pendingReplyContent = null;
                pendingReplySender = null;
            }
            messages.add(msg);
        }
        if (senderUuid != null && type == ChatMessageData.ConversationType.CHANNEL) {
            ChatDataStore.ChannelConfig cfg = channelConfigs.get(conversationId);
            if (cfg != null) {
                cfg.playerNames.put(senderUuid.toString(), senderName.getString());
            }
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
            unreadCounts.merge(conversationId, 1, Integer::sum);
            notifySoundForMessage(content, type);
            checkMentionAndHint(contentStr, senderName);
        }
        markDirty();
    }

    private void markDirty() {
        saveDirty = true;
        synchronized (this) {
            if (pendingSave == null || pendingSave.isDone()) {
                pendingSave = SAVE_TIMER.schedule(() -> {
                    if (saveDirty) {
                        saveDirty = false;
                        net.minecraft.client.Minecraft.getInstance().execute(ChatHistoryManager.this::save);
                    }
                }, 2, TimeUnit.SECONDS);
            }
        }
    }

    public void saveNow() {
        synchronized (this) {
            if (pendingSave != null) {
                pendingSave.cancel(false);
                pendingSave = null;
            }
        }
        saveDirty = false;
        save();
    }

    private void notifySoundForMessage(Component content, ChatMessageData.ConversationType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ModClientConfig.CONFIG.notificationSound.get()) return;
        String text = content.getString();
        String playerName = mc.player.getName().getString();
        if (text.contains("@" + playerName) && ModClientConfig.CONFIG.soundMention.get()) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.0F);
            return;
        }
        if (type == ChatMessageData.ConversationType.PRIVATE && ModClientConfig.CONFIG.soundWhisper.get()) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.4F, 1.5F);
            return;
        }
        if (type == ChatMessageData.ConversationType.COMMAND && ModClientConfig.CONFIG.soundSystem.get()) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.2F, 0.8F);
            return;
        }
        if (type == ChatMessageData.ConversationType.CHANNEL && ModClientConfig.CONFIG.soundPublic.get()) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.3F, 1.0F);
        }
    }

    public List<Integer> searchMessages(String conversationId, String query) {
        if (query == null || query.isEmpty()) return List.of();
        String lower = query.toLowerCase();
        synchronized (messages) {
            List<Integer> results = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                ChatMessageData msg = messages.get(i);
                if (msg.conversationId().equals(conversationId)
                        && msg.content().getString().toLowerCase().contains(lower)) {
                    results.add(i);
                }
            }
            return results;
        }
    }

    public ChatMessageData getMessageByIndex(int index) {
        synchronized (messages) {
            if (index < 0 || index >= messages.size()) return null;
            return messages.get(index);
        }
    }

    public int getMessageIndex(ChatMessageData target) {
        synchronized (messages) {
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i) == target) return i;
            }
            return -1;
        }
    }

    public void setPendingReply(String content, String sender) {
        this.pendingReplyContent = content;
        this.pendingReplySender = sender;
    }

    public String getPendingReplyContent() { return pendingReplyContent; }
    public String getPendingReplySender() { return pendingReplySender; }

    public void setSavedInput(String input) { this.savedInput = input; }
    public String getSavedInput() { return savedInput; }

    private void checkMentionAndHint(String text, Component senderName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String playerName = mc.player.getName().getString();
        if (text.contains("@" + playerName)) {
            cn.sarskin.ChatSphere.client.ChatHintsManager.getInstance().addHint(
                    Component.translatable("hint.chatsphere.mentioned", senderName.getString()), true);
        }
    }

    public static String timeSeparatorKey(long millis, int intervalMinutes) {
        if (intervalMinutes <= 0) return "";
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        int totalMin = dt.getHour() * 60 + dt.getMinute();
        int bucket = totalMin / intervalMinutes;
        return String.format("%02d:%02d", (bucket * intervalMinutes) / 60, (bucket * intervalMinutes) % 60);
    }

    public static String formatTimestamp(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public boolean consumeNewMessageFlag() {
        boolean flag = newMessageSinceLastCheck;
        newMessageSinceLastCheck = false;
        return flag;
    }

    public int getUnreadCount(String conversationId) {
        return unreadCounts.getOrDefault(conversationId, 0);
    }

    public void markConversationRead(String conversationId) {
        unreadCounts.remove(conversationId);
    }

    public int getTotalUnreadCount() {
        return unreadCounts.values().stream().mapToInt(Integer::intValue).sum();
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
                                config.inviteCode,
                                config.showInExplore, "", "")));
            }
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
                return Component.translatable("screen.chatsphere.mod_chat.general_channel");
            }
            return Component.literal(conversationId.substring(1));
        }
        synchronized (conversationDisplayNames) {
            return conversationDisplayNames.getOrDefault(conversationId,
                    resolveFallbackPrivateName(conversationId));
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
        synchronized (knownPlayers) {
            knownPlayers.clear();
        }
    }

    public static void resolveStorageContext() {
        Minecraft mc = Minecraft.getInstance();
        Path base;
        if (mc.isSingleplayer() && mc.getSingleplayerServer() != null) {
            String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
            base = ModStoragePaths.getSingleplayerDir(worldName);
            ChatDataStore.setDataDir(base);
        } else {
            ServerData serverData = mc.getCurrentServer();
            if (serverData != null) {
                base = ModStoragePaths.getMultiplayerDir(serverData.ip);
                ChatDataStore.setDataDir(base);
            } else {
                base = null;
                ChatDataStore.setDataDir(null);
            }
        }
        if (base != null) PlayerSkinCache.setCacheDir(base);
    }

    public void load() {
        if (loaded) return;
        loaded = true;
        resolveStorageContext();
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
                ChatMessageData loaded = new ChatMessageData(
                        Component.literal(sm.senderName()),
                        sm.senderUuid(),
                        Component.literal(sm.content()),
                        sm.timestamp(),
                        sm.conversationId(),
                        ctype,
                        sm.isOwn()
                );
                if (sm.replyContent() != null && sm.replySender() != null)
                    loaded = loaded.withReply(sm.replyContent(), sm.replySender());
                if (sm.duplicateCount() > 1)
                    loaded.setDuplicateCount(sm.duplicateCount());
                messages.add(loaded);
            }
        }
        savedInput = data.savedInput;
    }

    public void save() {
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
                        msg.isOwn(),
                        msg.duplicateCount(),
                        msg.replyContent(),
                        msg.replySender()
                ));
            }
        }

        data.savedInput = savedInput;
        ChatDataStore.saveAsync(data);
    }

    public boolean isServerConnected() {
        return serverConnected;
    }

    public void setKnownPlayers(Map<String, String> names) {
        synchronized (knownPlayers) {
            knownPlayers.clear();
            knownPlayers.putAll(names);
        }
    }

    public boolean isPlayerBlocked(String uuid) {
        return blockedPlayers.contains(uuid);
    }

    public void blockPlayer(String uuid) {
        if (!blockedPlayers.contains(uuid)) {
            blockedPlayers.add(uuid);
            save();
        }
    }

    public void unblockPlayer(String uuid) {
        if (blockedPlayers.remove(uuid)) {
            save();
        }
    }

    public List<String> getBlockedPlayers() {
        return new ArrayList<>(blockedPlayers);
    }

    public String getPlayerName(String uuid) {
        synchronized (knownPlayers) {
            String name = knownPlayers.get(uuid);
            if (name != null) return name;
        }
        synchronized (channelConfigs) {
            for (ChatDataStore.ChannelConfig cfg : channelConfigs.values()) {
                String n = cfg.playerNames.get(uuid);
                if (n != null) return n;
            }
        }
        return null;
    }

    public List<ClientboundPublicChannelListPayload.PublicChannelEntry> getPublicChannels() {
        return publicChannels;
    }

    public void setPublicChannels(List<ClientboundPublicChannelListPayload.PublicChannelEntry> list) {
        this.publicChannels = list;
        this.publicChannelsDirty = false;
    }

    public void setBridgeInfo(ClientboundBridgeInfoPayload payload) {
        this.bridgeProtocolVersion = payload.protocolVersion();
        this.bridgeVersion = payload.bridgeVersion();
        this.bridgeCapabilities = payload.capabilities();
        this.bridgeOnlinePlayers = payload.onlinePlayers() != null ? payload.onlinePlayers() : Set.of();
    }

    public int getBridgeProtocolVersion() { return bridgeProtocolVersion; }
    public String getBridgeVersion() { return bridgeVersion; }
    public int getBridgeCapabilities() { return bridgeCapabilities; }
    public Set<String> getBridgeOnlinePlayers() { return bridgeOnlinePlayers; }
    public boolean hasBridgeCapability(int cap) { return (bridgeCapabilities & cap) != 0; }

    public boolean isPublicChannelsDirty() {
        return publicChannelsDirty;
    }

    public void applyServerChannels(List<ModServerChannels.ChannelEntry> serverChannels, Map<String, String> knownPlayersMap) {
        serverConnected = true;
        publicChannelsDirty = true;
        setKnownPlayers(knownPlayersMap != null ? knownPlayersMap : Map.of());
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
                cfg.showInExplore = e.showInExplore();
                cfg.voiceRooms.clear();
                for (ModServerChannels.VoiceRoom vr : e.voiceRooms()) {
                    cn.sarskin.ChatSphere.client.voice.VoiceRoom cvr = new cn.sarskin.ChatSphere.client.voice.VoiceRoom();
                    cvr.name = vr.name();
                    cvr.members.addAll(vr.members());
                    cfg.voiceRooms.add(cvr);
                }
                channelConfigs.put(e.id(), cfg);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    String uuid = info.getProfile().getId().toString();
                    String name = info.getProfile().getName();
                    for (ChatDataStore.ChannelConfig cfg : channelConfigs.values()) {
                        if (cfg.members.contains(uuid)) {
                            cfg.playerNames.put(uuid, name);
                        }
                    }
                }
            }
        }
        refreshPrivateConversationDisplayNames();
        loaded = true;
        synchronized (channelConfigChangeListeners) {
            for (Runnable listener : channelConfigChangeListeners) {
                listener.run();
            }
        }
    }

    public void applyServerMessages(List<ClientboundMessageSyncPayload.StoredMessage> serverMessages, UUID localPlayerUuid) {
        serverConnected = true;
        if (!loaded) load();
        loaded = true;
        synchronized (messages) {
            Map<String, String[]> replyMap = new HashMap<>();
            for (ChatMessageData existing : messages) {
                if (existing.replyContent() != null) {
                    replyMap.put(existing.senderName().getString() + "|" + existing.content().getString() + "|" + existing.conversationId(),
                            new String[]{existing.replyContent(), existing.replySender()});
                }
            }
            messages.clear();
            for (ClientboundMessageSyncPayload.StoredMessage sm : serverMessages) {
                boolean isOwn = localPlayerUuid != null && sm.senderUuid().equals(localPlayerUuid);
                String convId = sm.conversationId() != null ? sm.conversationId() : DEFAULT_CHANNEL_ID;
                ChatMessageData.ConversationType ctype;
                String typeStr = sm.conversationType();
                if ("COMMAND".equals(typeStr)) {
                    ctype = ChatMessageData.ConversationType.COMMAND;
                } else if ("PRIVATE".equals(typeStr)) {
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
                String rc = sm.replyContent();
                String rs = sm.replySender();
                ChatMessageData msgData;
                if (ctype == ChatMessageData.ConversationType.COMMAND) {
                    msgData = new ChatMessageData(
                            Component.literal(text.isEmpty() ? senderName : text),
                            sm.senderUuid(),
                            Component.literal(""),
                            sm.timestamp(),
                            convId,
                            ctype,
                            isOwn
                    );
                } else {
                    msgData = new ChatMessageData(
                            Component.literal(senderName),
                            sm.senderUuid(),
                            Component.literal(text),
                            sm.timestamp(),
                            convId,
                            ctype,
                            isOwn
                    );
                }
                if (rc != null && !rc.isEmpty() && rs != null && !rs.isEmpty()) {
                    msgData = msgData.withReply(rc, rs);
                }
                messages.add(msgData);
            }

            // Recalculate duplicate counts for consecutive same-sender same-text messages per conversation
            Map<String, ChatMessageData> lastPerConv = new HashMap<>();
            List<ChatMessageData> deduped = new ArrayList<>();
            for (ChatMessageData msg : messages) {
                ChatMessageData last = lastPerConv.get(msg.conversationId());
                if (last != null && last.senderName().getString().equals(msg.senderName().getString())
                        && last.content().getString().equals(msg.content().getString())) {
                    last.setDuplicateCount(last.duplicateCount() + 1);
                } else {
                    deduped.add(msg);
                    lastPerConv.put(msg.conversationId(), msg);
                }
            }
            messages.clear();
            messages.addAll(deduped);
            for (int i = 0; i < messages.size(); i++) {
                ChatMessageData msg = messages.get(i);
                if (msg.replyContent() != null) continue;
                String key = msg.senderName().getString() + "|" + msg.content().getString() + "|" + msg.conversationId();
                String[] replyData = replyMap.get(key);
                if (replyData != null) {
                    messages.set(i, msg.withReply(replyData[0], replyData[1]));
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
        String cached = getInstance().getPlayerName(otherUuidStr);
        if (cached != null) return Component.literal(cached);
        return Component.literal(otherUuidStr.substring(0, 8) + "...");
    }

    private static Component resolveFallbackPrivateName(String conversationId) {
        if (!conversationId.contains(":")) {
            return Component.literal(conversationId.substring(0, Math.min(conversationId.length(), 8)) + "...");
        }
        String[] parts = conversationId.split(":");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Component.literal(conversationId.substring(0, Math.min(conversationId.length(), 8)) + "...");
        }
        String localStr = mc.player.getUUID().toString();
        String otherUuidStr = parts[0].equals(localStr) ? parts[1] : parts[0];
        String cached = getInstance().getPlayerName(otherUuidStr);
        if (cached != null) return Component.literal(cached);
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

    public void sendVoiceRoomAction(ServerboundChannelActionPayload.Action action, String channelId, String roomName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        UUID playerUuid = mc.player.getUUID();
        var payload = new ServerboundChannelActionPayload(
                action, channelId, playerUuid, true, roomName, "",
                List.of(), List.of(), List.of(), "", true, "", ""
        );
        mc.getConnection().send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(payload));
    }
}
