package cn.sarskin.ChatSphere.client;

import net.minecraft.network.chat.Component;

import java.util.UUID;

public record ChatMessageData(
        Component senderName,
        UUID senderUuid,
        Component content,
        long timestamp,
        String conversationId,
        ConversationType conversationType,
        boolean isOwn
) {
    public enum ConversationType {
        CHANNEL, PRIVATE, COMMAND
    }
}
