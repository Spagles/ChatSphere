package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.ModMain;
import static cn.sarskin.ChatSphere.ModMain.DEFAULT_CHANNEL_ID;
import cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload.StoredMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ModServerChannels {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModServerChannels");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, ModServerChannels> INSTANCES = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGE_HISTORY = 200;
    private static final int BACKUP_KEEP_MAX = 20;
    private static final int BACKUP_INTERVAL_MINUTES = 30;
    private static final String BACKUPS_DIR_NAME = "chatsphere_backups";
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final MinecraftServer server;
    private final Map<String, ChannelEntry> channels = new LinkedHashMap<>();
    private final List<StoredMessage> messageHistory = new ArrayList<>();
    private boolean loaded;
    private long lastBackupTime;

    private ModServerChannels(MinecraftServer server) {
        this.server = server;
    }

    public static ModServerChannels getInstance(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> {
            ModServerChannels msc = new ModServerChannels(s);
            msc.load();
            return msc;
        });
    }

    public static void removeServer(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public synchronized List<ChannelEntry> getAllChannels() {
        return new ArrayList<>(channels.values());
    }

    public synchronized ChannelEntry getChannel(String id) {
        return channels.get(id);
    }

    public synchronized void createChannel(String id, UUID ownerUuid) {
        if (channels.containsKey(id)) return;
        List<String> members = new ArrayList<>();
        if (ownerUuid != null) members.add(ownerUuid.toString());
        ChannelEntry entry = new ChannelEntry(id, ownerUuid != null ? ownerUuid.toString() : "", true, "", "",
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), members, generateInviteCode());
        channels.put(id, entry);
        save();
        broadcastSync();
    }

    public synchronized void updateChannelConfig(String channelId, boolean isPublic, String description, String displayName,
                                                  List<String> admins, List<String> mutedPlayers,
                                                  List<String> invitedPlayers, String inviteCode, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!entry.owner().equals(reqStr) && !entry.admins().contains(reqStr)) return;
        String newCode = (inviteCode != null && !inviteCode.isEmpty()) ? inviteCode : entry.inviteCode();
        ChannelEntry updated = new ChannelEntry(channelId, entry.owner(), isPublic, description, displayName,
                new ArrayList<>(admins), new ArrayList<>(mutedPlayers), new ArrayList<>(invitedPlayers),
                new ArrayList<>(entry.members()), newCode);
        channels.put(channelId, updated);
        save();
        broadcastSync();
    }

    public synchronized boolean removeChannel(String channelId, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return false;
        if (!entry.owner().equals(requester.toString())) return false;
        channels.remove(channelId);
        save();
        broadcastSync();
        return true;
    }

    public void sendToPlayer(ServerPlayer player) {
        List<ChannelEntry> list = getAllChannels().stream()
                .filter(e -> e.members().contains(player.getUUID().toString()))
                .collect(Collectors.toList());
        if (!list.isEmpty()) {
            player.connection.send(new ClientboundCustomPayloadPacket(
                    new ClientboundChannelSyncPayload(list)));
        }
    }

    public void sendMessagesToPlayer(ServerPlayer player) {
        List<StoredMessage> msgs;
        synchronized (messageHistory) {
            if (messageHistory.isEmpty()) return;
            msgs = new ArrayList<>();
            String playerUuid = player.getUUID().toString();
            for (StoredMessage m : messageHistory) {
                if ("PRIVATE".equals(m.conversationType())) {
                    String convId = m.conversationId();
                    boolean isForPlayer = m.senderUuid().toString().equals(playerUuid);
                    if (!isForPlayer) {
                        if (convId != null && convId.contains(":")) {
                            isForPlayer = convId.startsWith(playerUuid + ":") || convId.endsWith(":" + playerUuid);
                        } else {
                            isForPlayer = convId != null && convId.equals(playerUuid);
                        }
                    }
                    if (!isForPlayer) continue;
                }
                msgs.add(m);
            }
            if (msgs.isEmpty()) return;
        }
        player.connection.send(new ClientboundCustomPayloadPacket(
                new ClientboundMessageSyncPayload(msgs)));
    }

    public synchronized String joinByCode(String inviteCode, UUID playerUuid) {
        for (ChannelEntry entry : channels.values()) {
            if (entry.inviteCode().equalsIgnoreCase(inviteCode)) {
                String puid = playerUuid.toString();
                if (entry.members().contains(puid)) return "already_member";
                List<String> newMembers = new ArrayList<>(entry.members());
                newMembers.add(puid);
                ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                        entry.description(), entry.displayName(),
                        new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                        new ArrayList<>(entry.invitedPlayers()), newMembers, entry.inviteCode());
                channels.put(entry.id(), updated);
                save();
                broadcastSync();
                return "success";
            }
        }
        return "not_found";
    }

    public void addChatMessage(String senderName, UUID senderUuid, String content,
                                String conversationId, String conversationType) {
        StoredMessage msg = new StoredMessage(senderName, senderUuid, content, System.currentTimeMillis(),
                conversationId, conversationType);
        synchronized (messageHistory) {
            messageHistory.add(msg);
            if (messageHistory.size() > MAX_MESSAGE_HISTORY) {
                messageHistory.remove(0);
            }
        }
        if ("CHANNEL".equals(conversationType) && senderUuid != null) {
            addMemberToChannel(conversationId, senderUuid.toString());
        }
        saveMessages();
    }

    public synchronized void addMemberToChannel(String channelId, String playerUuid) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        List<String> newMembers = new ArrayList<>(entry.members());
        if (!newMembers.contains(playerUuid)) {
            newMembers.add(playerUuid);
            ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                    entry.description(), entry.displayName(),
                    new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                    new ArrayList<>(entry.invitedPlayers()), newMembers, entry.inviteCode());
            channels.put(channelId, updated);
            save();
            broadcastSync();
        }
    }

    public void addCommandMessage(String senderName, UUID senderUuid, String commandText) {
        addChatMessage(senderName, senderUuid, commandText,
                "__commands__",
                "COMMAND");
    }

    public List<StoredMessage> getRecentMessages(int count) {
        synchronized (messageHistory) {
            if (messageHistory.isEmpty()) return List.of();
            int from = Math.max(0, messageHistory.size() - count);
            return List.copyOf(messageHistory.subList(from, messageHistory.size()));
        }
    }

    private void broadcastSync() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            List<ChannelEntry> playerChannels = channels.values().stream()
                    .filter(e -> e.members().contains(p.getUUID().toString()))
                    .collect(Collectors.toList());
            p.connection.send(new ClientboundCustomPayloadPacket(
                    new ClientboundChannelSyncPayload(playerChannels)));
        }
    }

    private Path getDataPath() {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("chatsphere_channels.json");
    }

    private Path getMessagesPath() {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data").resolve("chatsphere_messages.json");
    }

    public synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path path = getDataPath();
        if (!Files.exists(path)) {
            channels.clear();
            channels.put(DEFAULT_CHANNEL_ID, new ChannelEntry(DEFAULT_CHANNEL_ID, "", true, "", "",
                    List.of(), List.of(), List.of(), List.of(), generateInviteCode()));
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) return;
            channels.clear();
            if (obj.has("channels")) {
                JsonArray arr = obj.getAsJsonArray("channels");
                for (var el : arr) {
                    JsonObject c = el.getAsJsonObject();
                    ChannelEntry entry = new ChannelEntry(
                            c.get("id").getAsString(),
                            c.get("owner").getAsString(),
                            c.get("isPublic").getAsBoolean(),
                            c.has("description") ? c.get("description").getAsString() : "",
                            c.has("displayName") ? c.get("displayName").getAsString() : "",
                            readStringList(c, "admins"),
                            readStringList(c, "mutedPlayers"),
                            readStringList(c, "invitedPlayers"),
                            readStringList(c, "members"),
                            c.has("inviteCode") ? c.get("inviteCode").getAsString() : generateInviteCode()
                    );
                    channels.put(entry.id(), entry);
                }
            }
            if (!channels.containsKey(DEFAULT_CHANNEL_ID)) {
                channels.put(DEFAULT_CHANNEL_ID, new ChannelEntry(DEFAULT_CHANNEL_ID, "", true, "", "",
                        List.of(), List.of(), List.of(), List.of(), generateInviteCode()));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load server channels", e);
        }
        loadMessages();
    }

    private void loadMessages() {
        Path path = getMessagesPath();
        if (!Files.exists(path)) return;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null || !obj.has("messages")) return;
            JsonArray arr = obj.getAsJsonArray("messages");
            synchronized (messageHistory) {
                messageHistory.clear();
                for (var el : arr) {
                    JsonObject m = el.getAsJsonObject();
                    StoredMessage sm = new StoredMessage(
                            m.get("senderName").getAsString(),
                            UUID.fromString(m.get("senderUuid").getAsString()),
                            m.get("content").getAsString(),
                            m.get("timestamp").getAsLong(),
                            m.has("conversationId") ? m.get("conversationId").getAsString() : DEFAULT_CHANNEL_ID,
                            m.has("conversationType") ? m.get("conversationType").getAsString() : "CHANNEL"
                    );
                    messageHistory.add(sm);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load server messages", e);
        }
    }

    private void saveMessages() {
        Path path = getMessagesPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            synchronized (messageHistory) {
                for (StoredMessage m : messageHistory) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("senderName", m.senderName());
                    obj.addProperty("senderUuid", m.senderUuid().toString());
                    obj.addProperty("content", m.content());
                    obj.addProperty("timestamp", m.timestamp());
                    obj.addProperty("conversationId", m.conversationId());
                    obj.addProperty("conversationType", m.conversationType());
                    arr.add(obj);
                }
            }
            root.add("messages", arr);
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            performBackupIfNeeded();
        } catch (Exception e) {
            LOGGER.error("Failed to save server messages", e);
        }
    }

    public synchronized void save() {
        Path path = getDataPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (ChannelEntry e : channels.values()) {
                JsonObject c = new JsonObject();
                c.addProperty("id", e.id());
                c.addProperty("owner", e.owner());
                c.addProperty("isPublic", e.isPublic());
                c.addProperty("description", e.description());
                c.addProperty("displayName", e.displayName());
                writeStringList(c, "admins", e.admins());
                writeStringList(c, "mutedPlayers", e.mutedPlayers());
                writeStringList(c, "invitedPlayers", e.invitedPlayers());
                writeStringList(c, "members", e.members());
                c.addProperty("inviteCode", e.inviteCode());
                arr.add(c);
            }
            root.add("channels", arr);
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            performBackupIfNeeded();
        } catch (Exception e) {
            LOGGER.error("Failed to save server channels", e);
        }
    }

    private void performBackupIfNeeded() {
        long intervalMs = BACKUP_INTERVAL_MINUTES * 60 * 1000L;
        if (System.currentTimeMillis() - lastBackupTime < intervalMs) return;
        Path channelsPath = getDataPath();
        Path messagesPath = getMessagesPath();
        try {
            Path backupDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data").resolve(BACKUPS_DIR_NAME);
            Files.createDirectories(backupDir);
            String ts = LocalDateTime.now().format(BACKUP_TIMESTAMP);
            if (Files.exists(channelsPath)) {
                Files.copy(channelsPath, backupDir.resolve("channels_" + ts + ".json"), StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(messagesPath)) {
                Files.copy(messagesPath, backupDir.resolve("messages_" + ts + ".json"), StandardCopyOption.REPLACE_EXISTING);
            }
            lastBackupTime = System.currentTimeMillis();
            LOGGER.info("Created server data backup: {}", ts);
            pruneBackups(backupDir);
        } catch (Exception e) {
            LOGGER.error("Failed to perform server backup", e);
        }
    }

    private static void pruneBackups(Path backupDir) {
        try {
            if (!Files.exists(backupDir)) return;
            List<Path> sorted = Files.list(backupDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (IOException e) { return 0; }
                    }))
                    .collect(Collectors.toList());
            while (sorted.size() > BACKUP_KEEP_MAX) {
                Path oldest = sorted.remove(0);
                Files.deleteIfExists(oldest);
                LOGGER.info("Pruned old server backup: {}", oldest);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to prune server backups", e);
        }
    }

    private static List<String> readStringList(JsonObject obj, String key) {
        List<String> list = new ArrayList<>();
        if (obj.has(key)) {
            JsonArray arr = obj.getAsJsonArray(key);
            for (var el : arr) list.add(el.getAsString());
        }
        return list;
    }

    private static void writeStringList(JsonObject obj, String key, List<String> list) {
        JsonArray arr = new JsonArray();
        for (String s : list) arr.add(s);
        obj.add(key, arr);
    }

    public record ChannelEntry(
            String id, String owner, boolean isPublic, String description, String displayName,
            List<String> admins, List<String> mutedPlayers, List<String> invitedPlayers,
            List<String> members, String inviteCode
    ) {
        public ChannelEntry {
            if (inviteCode == null || inviteCode.isEmpty()) {
                inviteCode = generateInviteCode();
            }
        }
    }

    private static String generateInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
