package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.UUID;

@EventBusSubscriber(modid = ModMain.MODID)
public class ModServerEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModServerChannels channels = ModServerChannels.getInstance(player.getServer());
            channels.addMemberToChannel(ChatHistoryManager.DEFAULT_CHANNEL_ID, player.getUUID().toString());
            channels.sendToPlayer(player);
            channels.sendMessagesToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String rawText = event.getRawText();
        ModServerChannels channels = ModServerChannels.getInstance(event.getPlayer().getServer());
        // Detect /msg and /tell commands as private messages
        if (rawText.startsWith("/msg ") || rawText.startsWith("/tell ") || rawText.startsWith("/w ")) {
            String[] parts = rawText.split(" ", 3);
            if (parts.length >= 3) {
                String targetName = parts[1];
                var targetPlayer = event.getPlayer().getServer().getPlayerList().getPlayerByName(targetName);
                String convId;
                if (targetPlayer != null) {
                    UUID senderUUID = event.getPlayer().getUUID();
                    UUID targetUUID = targetPlayer.getUUID();
                    convId = senderUUID.compareTo(targetUUID) < 0
                            ? senderUUID + ":" + targetUUID
                            : targetUUID + ":" + senderUUID;
                } else {
                    UUID senderUUID = event.getPlayer().getUUID();
                    UUID offlineUuid = UUIDUtil.createOfflinePlayerUUID(targetName);
                    convId = senderUUID.compareTo(offlineUuid) < 0
                            ? senderUUID + ":" + offlineUuid
                            : offlineUuid + ":" + senderUUID;
                }
                channels.addChatMessage(event.getUsername(), event.getPlayer().getUUID(), parts[2],
                        convId, "PRIVATE");
                return;
            }
        }
        String convId = ChatHistoryManager.DEFAULT_CHANNEL_ID;
        channels.addChatMessage(event.getUsername(), event.getPlayer().getUUID(), rawText,
                convId, "CHANNEL");
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        var source = event.getParseResults().getContext().getSource();
        if (source.getEntity() instanceof ServerPlayer player) {
            String input = event.getParseResults().getReader().getString();
            ModServerChannels channels = ModServerChannels.getInstance(player.getServer());
            if (input.startsWith("msg ") || input.startsWith("tell ") || input.startsWith("w ")) {
                String[] parts = input.split(" ", 3);
                if (parts.length >= 3) {
                    String targetName = parts[1];
                    ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(targetName);
                    String convId;
                    if (target != null) {
                        UUID senderUUID = player.getUUID();
                        UUID targetUUID = target.getUUID();
                        convId = senderUUID.compareTo(targetUUID) < 0
                                ? senderUUID + ":" + targetUUID
                                : targetUUID + ":" + senderUUID;
                    } else {
                        UUID senderUUID = player.getUUID();
                        UUID offlineUuid = UUIDUtil.createOfflinePlayerUUID(targetName);
                        convId = senderUUID.compareTo(offlineUuid) < 0
                                ? senderUUID + ":" + offlineUuid
                                : offlineUuid + ":" + senderUUID;
                    }
                    channels.addChatMessage(player.getGameProfile().getName(), player.getUUID(), parts[2],
                            convId, "PRIVATE");
                }
                return;
            }
            String cmdText = input.startsWith("/") ? input.substring(1) : input;
            channels.addCommandMessage(player.getGameProfile().getName(), player.getUUID(), cmdText);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ModServerChannels.removeServer(event.getServer());
    }
}
