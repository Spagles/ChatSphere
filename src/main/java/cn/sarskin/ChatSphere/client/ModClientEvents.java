package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.screen.ConfigScreen;
import cn.sarskin.ChatSphere.client.screen.ModChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    public static volatile long lastCommandTime;

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChatHintsManager.getInstance().tick();

        while (mc.options.keyChat.consumeClick()) {
            mc.setScreen(new ModChatScreen(""));
        }

        if (mc.screen == null && mc.options.keyCommand.consumeClick()) {
            mc.setScreen(new ModChatScreen("/"));
        }

        while (ModKeyMappings.OPEN_CONFIG_KEY.get().consumeClick()) {
            mc.setScreen(new ConfigScreen());
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        history.saveNow();
        history.setServerConnected(false);
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Cancel every chat event to hide vanilla chat display
        event.setCanceled(true);

        Component message = event.getMessage();
        UUID sender = event.getSender();

        // ALL system messages (screenshots, command feedback, overlays, etc.) → COMMAND console
        if (event.isSystem()) {
            // For system events from ChatListener, get the overlay flag
            if (event instanceof ClientChatReceivedEvent.System sys && sys.isOverlay()) {
                return;
            }
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            history.addCommandMessage(message, sender, Component.literal(""), false);
            return;
        }

        String text = message.getString();
        boolean isOwn = sender.equals(mc.player.getUUID());

        // Own messages (echo from server) - already added locally by ModChatScreen.sendMessage()
        if (isOwn) {
            return;
        }

        Component senderName;
        String content;

        if (text.startsWith("<") && text.contains("> ")) {
            int endBracket = text.indexOf("> ");
            senderName = Component.literal(text.substring(1, endBracket));
            content = text.substring(endBracket + 2);
        } else {
            int colonIndex = text.indexOf(": ");
            if (colonIndex > 0 && colonIndex < 30) {
                senderName = Component.literal(text.substring(0, colonIndex));
                content = text.substring(colonIndex + 2);
            } else {
                senderName = Component.literal("\u7CFB\u7EDF");
                content = text;
            }
        }

        ChatHistoryManager history = ChatHistoryManager.getInstance();

        // Detect private messages via boundChatType
        ChatType.Bound boundChatType = event.getBoundChatType();
        if (boundChatType != null) {
            var optKey = boundChatType.chatType().unwrapKey();
            if (optKey.isPresent()) {
                var key = optKey.get();
                if (key.equals(ChatType.MSG_COMMAND_INCOMING)) {
                    String convId;
                    if (mc.player != null) {
                        UUID localUuid = mc.player.getUUID();
                        convId = localUuid.compareTo(sender) < 0
                                ? localUuid + ":" + sender
                                : sender + ":" + localUuid;
                    } else {
                        convId = sender.toString();
                    }
                    history.addPrivateConversation(convId, senderName);
                    history.addMessage(senderName, sender, Component.literal(content),
                            convId, ChatMessageData.ConversationType.PRIVATE, false);
                    return;
                }
                if (key.equals(ChatType.MSG_COMMAND_OUTGOING)) {
                    return;
                }
            }
        }

        // Standard player chat goes to default channel
        history.addMessage(senderName, sender, Component.literal(content),
                ChatHistoryManager.DEFAULT_CHANNEL_ID, ChatMessageData.ConversationType.CHANNEL, false);
    }
}
