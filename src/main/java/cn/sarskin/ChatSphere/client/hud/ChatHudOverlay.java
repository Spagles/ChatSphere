package cn.sarskin.ChatSphere.client.hud;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ChatHudOverlay implements LayeredDraw.Layer {
    public static final ResourceLocation HUD_ID = ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "chat_hud");
    public static final ChatHudOverlay INSTANCE = new ChatHudOverlay();

    private static final int MAX_VISIBLE_MESSAGES = 5;
    private static final int BUBBLE_PADDING = 6;
    private static final int BUBBLE_MARGIN = 4;
    private static final int AVATAR_SIZE = 10;
    private static final long MESSAGE_DISPLAY_TIME = 8000;
    private static final int ICON_SIZE = 16;
    private static final int ICON_PADDING = 4;
    private static final ResourceLocation CHAT_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/chat_icon.png");

    private long lastMessageTime;
    private boolean flashing;
    private long flashStartTime;

    private ChatHudOverlay() {}

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        if (mc.screen instanceof cn.sarskin.ChatSphere.client.screen.ModChatScreen) return;

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        List<ChatMessageData> recentMessages = history.getRecentMessages(MAX_VISIBLE_MESSAGES);

        long now = System.currentTimeMillis();

        int chatStartY = screenHeight - ICON_PADDING - ICON_SIZE - 10;
        int bubbleX = 4;

        int shown = 0;
        for (int i = recentMessages.size() - 1; i >= 0 && shown < MAX_VISIBLE_MESSAGES; i--) {
            ChatMessageData msg = recentMessages.get(i);
            if (msg.conversationType() == ChatMessageData.ConversationType.COMMAND) continue;
            if (now - msg.timestamp() > MESSAGE_DISPLAY_TIME) continue;

            Component bubbleText = buildBubbleText(msg);
            int textWidth = mc.font.width(bubbleText);
            int bubbleWidth = textWidth + BUBBLE_PADDING * 2 + (ModClientConfig.CONFIG.showAvatar.get() ? AVATAR_SIZE + 4 : 0);
            int bubbleHeight = 12 + BUBBLE_PADDING * 2;

            int bubbleY = chatStartY - (shown + 1) * (bubbleHeight + BUBBLE_MARGIN);
            if (bubbleY < 0) break;

            drawBubble(guiGraphics, mc, msg, bubbleText, bubbleX, bubbleY, bubbleWidth, bubbleHeight);
            shown++;
        }

        drawIcon(guiGraphics, mc, screenWidth, screenHeight, history, now);
    }

    private Component buildBubbleText(ChatMessageData msg) {
        MutableComponent text = Component.literal("");
        boolean showName = ModClientConfig.CONFIG.showSenderName.get();
        boolean showTime = ModClientConfig.CONFIG.showTimestamp.get();

        if (showName) {
            text.append(msg.senderName().copy().withStyle(ChatFormatting.AQUA));
            text.append(" ");
        }
        text.append(msg.content().copy());
        if (showTime) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            text.append("  ").append(Component.literal(sdf.format(new Date(msg.timestamp()))).withStyle(ChatFormatting.GRAY));
        }
        return text;
    }

    private void drawBubble(GuiGraphics guiGraphics, Minecraft mc, ChatMessageData msg,
                            Component bubbleText, int x, int y, int width, int height) {
        int color = msg.isOwn() ? 0xFFDCF8C6 : 0xFFFFFFFF;
        int borderColor = 0xFF999999;

        guiGraphics.fill(x, y, x + width, y + height, color);
        guiGraphics.renderOutline(x, y, width, height, borderColor);

        int textX = x + BUBBLE_PADDING;
        if (ModClientConfig.CONFIG.showAvatar.get()) {
            int avatarX = x + BUBBLE_PADDING;
            int avatarY = y + (height - AVATAR_SIZE) / 2;
            drawAvatar(guiGraphics, mc, msg, avatarX, avatarY);
            textX = avatarX + AVATAR_SIZE + 4;
        }

        int textY = y + (height - 9) / 2;
        guiGraphics.drawString(mc.font, bubbleText, textX, textY, msg.isOwn() ? 0xFF000000 : 0xFF333333, false);
    }

    private void drawAvatar(GuiGraphics guiGraphics, Minecraft mc, ChatMessageData msg, int x, int y) {
        PlayerSkin skin;
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(msg.senderUuid());
            if (info != null) {
                skin = info.getSkin();
            } else {
                skin = DefaultPlayerSkin.get(msg.senderUuid());
            }
        } else {
            skin = DefaultPlayerSkin.get(msg.senderUuid());
        }
        PlayerFaceRenderer.draw(guiGraphics, skin, x, y, AVATAR_SIZE);
    }

    private void drawIcon(GuiGraphics guiGraphics, Minecraft mc, int screenWidth, int screenHeight,
                          ChatHistoryManager history, long now) {
        int iconX = ICON_PADDING;
        int iconY = screenHeight - ICON_PADDING - ICON_SIZE;

        if (history.consumeNewMessageFlag()) {
            if (ModClientConfig.CONFIG.notificationSound.get()) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            }
            if (ModClientConfig.CONFIG.notificationFlash.get()) {
                flashing = true;
                flashStartTime = now;
            }
        }

        float alpha = 1.0f;
        if (flashing) {
            long elapsed = now - flashStartTime;
            if (elapsed < 2000) {
                alpha = (float) Math.sin(elapsed * 0.005) * 0.5f + 0.5f;
                alpha = 0.5f + alpha * 0.5f;
            } else {
                flashing = false;
            }
        }

        guiGraphics.setColor(1f, 1f, 1f, alpha);
        guiGraphics.blit(CHAT_ICON, iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        guiGraphics.setColor(1f, 1f, 1f, 1f);
        guiGraphics.renderOutline(iconX, iconY, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
    }
}
