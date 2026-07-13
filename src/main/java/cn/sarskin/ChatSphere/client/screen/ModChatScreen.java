package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.text.SimpleDateFormat;
import java.util.*;

public class ModChatScreen extends Screen {
    private static final String COMMAND_CONVERSATION_ID = "__commands__";
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int SIDEBAR_WIDTH = 100;
    private static final int HEADER_BAR_HEIGHT = 14;
    private static final int AVATAR_SIZE = 10;
    private static final int SIDEBAR_AVATAR_SIZE = 12;
    private static final int BUBBLE_HPAD = 8;
    private static final int BUBBLE_VPAD = 4;
    private static final int SIDEBAR_ITEM_HEIGHT = 18;
    private static final int CONFIG_ICON_SIZE = 10;
    private static final int RIGHT_SIDEBAR_WIDTH = 80;
    private static final ResourceLocation SETTINGS_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/settings_gear.png");

    private EditBox input;
    private String initial;
    private int historyPos = -1;
    private final List<String> sentHistory = new ArrayList<>();
    private CommandSuggestions commandSuggestions;
    private final List<String> cmdHistoryEntries = new ArrayList<>();
    private int cmdHistoryPos = -1;
    private String currentConversation = ChatHistoryManager.DEFAULT_CHANNEL_ID;
    private int scrollOffset;

    private final List<SidebarEntry> sidebarEntries = new ArrayList<>();
    private final Map<String, PlayerInfo> onlinePlayers = new LinkedHashMap<>();
    private boolean showRightSidebar = ModClientConfig.CONFIG.showRightSidebar.get();

    public ModChatScreen(String initial) {
        super(Component.translatable("screen.chatsphere.mod_chat.title"));
        this.initial = initial;
        this.scrollOffset = 0;
        ChatHistoryManager.getInstance().load();
    }

    @Override
    protected void init() {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        history.load();
        history.ensureDefaultChannel();
        if (!history.getChannels().contains(currentConversation)
                && !COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            List<String> channels = history.getChannels();
            currentConversation = channels.isEmpty() ? ChatHistoryManager.DEFAULT_CHANNEL_ID : channels.get(0);
        }

        if (this.initial.startsWith("/") && !COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            currentConversation = COMMAND_CONVERSATION_ID;
            cmdHistoryEntries.clear();
            cmdHistoryEntries.addAll(history.getCommandHistory(this.minecraft.player.getUUID()));
            cmdHistoryPos = cmdHistoryEntries.size();
            this.initial = this.initial.substring(1);
        }

        int inputY = this.height - 12;
        int inputX = SIDEBAR_WIDTH + 2;
        int inputWidth = this.width - SIDEBAR_WIDTH - 6;
        if (showRightSidebar) inputWidth -= RIGHT_SIDEBAR_WIDTH;
        this.input = new EditBox(this.minecraft.fontFilterFishy, inputX, inputY,
                inputWidth, 12,
                Component.translatable("chat.editBox"));
        this.input.setMaxLength(MAX_MESSAGE_LENGTH);
        this.input.setBordered(false);
        this.input.setValue(this.initial);
        this.addWidget(this.input);
        this.setInitialFocus(this.input);

        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font,
                true, false, 1, 10, true, -805306368);
        this.commandSuggestions.setAllowHiding(false);
        this.commandSuggestions.updateCommandInfo();
        this.input.setResponder(this::onCommandInputChanged);

        refreshOnlinePlayers();
    }

    private void refreshOnlinePlayers() {
        onlinePlayers.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.player != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                onlinePlayers.put(info.getProfile().getId().toString(), info);
            }
        }
    }

    private void onCommandInputChanged(String value) {
        if (this.commandSuggestions != null) {
            this.commandSuggestions.setAllowSuggestions(true);
            this.commandSuggestions.updateCommandInfo();
        }
    }

    @Override
    public void tick() {
        super.tick();
        refreshOnlinePlayers();
        ChatHistoryManager.getInstance().refreshPrivateConversationDisplayNames();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean showRight = showRightSidebar && ChatHistoryManager.getInstance().getConversationType(currentConversation) == ChatMessageData.ConversationType.CHANNEL;
            int effectiveWidth = showRight ? this.width - RIGHT_SIDEBAR_WIDTH : this.width;
            int btnX = showRight ? effectiveWidth - 10 : this.width - 10;
            int btnY = 4;
            if (mouseX >= btnX && mouseX <= btnX + 10 && mouseY >= btnY && mouseY <= btnY + 30) {
                showRightSidebar = !showRightSidebar;
                ModClientConfig.CONFIG.showRightSidebar.set(showRightSidebar);
                return true;
            }
            if (showRight && mouseX >= effectiveWidth && mouseX < this.width && mouseY > 18) {
                ChatHistoryManager history = ChatHistoryManager.getInstance();
                ChatDataStore.ChannelConfig cfg = history.getChannelConfig(currentConversation);
                Minecraft mc = Minecraft.getInstance();
                String localPlayerUuid = mc.player != null ? mc.player.getUUID().toString() : null;
                int y = 18;
                for (Map.Entry<String, PlayerInfo> entry : onlinePlayers.entrySet()) {
                    String uuid = entry.getKey();
                    boolean isChannelMember = cfg.members.contains(uuid);
                    if (!isChannelMember && !uuid.equals(localPlayerUuid) && !uuid.equals(cfg.owner) && !cfg.admins.contains(uuid)) {
                        y += 10;
                        continue;
                    }
                    if (mouseY >= y && mouseY < y + 10) {
                        if (localPlayerUuid != null && !uuid.equals(localPlayerUuid)) {
                            UUID localUuid = mc.player.getUUID();
                            UUID targetUuid = UUID.fromString(uuid);
                            String convId = localUuid.compareTo(targetUuid) < 0
                                    ? localUuid + ":" + targetUuid
                                    : targetUuid + ":" + localUuid;
                            String targetName = entry.getValue().getProfile().getName();
                            history.addPrivateConversation(convId, Component.literal(targetName));
                            currentConversation = convId;
                            scrollOffset = 0;
                        }
                        return true;
                    }
                    y += 10;
                    if (y > height - 10) break;
                }
            }
        }

        if (COMMAND_CONVERSATION_ID.equals(currentConversation)
                && this.commandSuggestions != null
                && this.commandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && mouseX < SIDEBAR_WIDTH) {
            int yOffset = 10;
            int headerIdx = 0;
            for (int i = 0; i < sidebarEntries.size(); i++) {
                SidebarEntry entry = sidebarEntries.get(i);
                if (entry.isHeader) {
                    if (headerIdx == 0) {
                        int plusX = SIDEBAR_WIDTH - 6 - 10;
                        int plusY = yOffset + (SIDEBAR_ITEM_HEIGHT - 8) / 2;
                        if (mouseX >= plusX && mouseX <= plusX + 10 && mouseY >= plusY && mouseY <= plusY + 10) {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new CreateChannelScreen(this));
                            }
                            return true;
                        }
                        int joinX = plusX - 14;
                        int joinY = plusY;
                        if (mouseX >= joinX && mouseX <= joinX + 10 && mouseY >= joinY && mouseY <= joinY + 10) {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new JoinChannelScreen(this));
                            }
                            return true;
                        }
                    }
                    headerIdx++;
                    yOffset += SIDEBAR_ITEM_HEIGHT;
                    continue;
                }
                if (mouseY >= yOffset && mouseY < yOffset + SIDEBAR_ITEM_HEIGHT) {
                    if (entry.type == ChatMessageData.ConversationType.CHANNEL && entry.conversationId != null) {
                        int gearX = SIDEBAR_WIDTH - 6 - CONFIG_ICON_SIZE;
                        if (mouseX >= gearX && mouseX <= gearX + CONFIG_ICON_SIZE) {
                            if (this.minecraft != null) {
                                ChatHistoryManager hist = ChatHistoryManager.getInstance();
                                UUID playerUuid = this.minecraft.player.getUUID();
                                if (hist.isOwner(entry.conversationId, playerUuid) || hist.isAdmin(entry.conversationId, playerUuid)) {
                                    this.minecraft.setScreen(new ChannelConfigScreen(this, entry.conversationId));
                                }
                            }
                            return true;
                        }
                    }
                    if (entry.conversationId != null && !entry.conversationId.equals(currentConversation)) {
                        ChatHistoryManager history = ChatHistoryManager.getInstance();
                        if (entry.type == ChatMessageData.ConversationType.PRIVATE && !history.hasConversation(entry.conversationId)) {
                            history.addPrivateConversation(entry.conversationId, entry.displayName);
                        }
                        if (entry.type == ChatMessageData.ConversationType.COMMAND) {
                            cmdHistoryEntries.clear();
                            cmdHistoryEntries.addAll(history.getCommandHistory(this.minecraft.player.getUUID()));
                            cmdHistoryPos = cmdHistoryEntries.size();
                        }
                        if (entry.type == ChatMessageData.ConversationType.CHANNEL && history.isServerConnected()) {
                            sendChannelPacket(ServerboundChannelActionPayload.Action.JOIN_MEMBER, entry.conversationId, this.minecraft.player.getUUID());
                        }
                        currentConversation = entry.conversationId;
                        scrollOffset = 0;
                    }
                    return true;
                }
                yOffset += SIDEBAR_ITEM_HEIGHT;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        ChatHistoryManager.getInstance().save();
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (COMMAND_CONVERSATION_ID.equals(currentConversation)
                && this.commandSuggestions != null
                && this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 256) {
            this.minecraft.setScreen(null);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            sendMessage(this.input.getValue());
            return true;
        }
        if (keyCode == 265) {
            moveInHistory(-1);
            return true;
        }
        if (keyCode == 264) {
            moveInHistory(1);
            return true;
        }
        return false;
    }

    private void sendMessage(String text) {
        text = text.trim();
        if (text.isEmpty()) return;

        if (text.startsWith("#") && text.length() > 1 && ModClientConfig.CONFIG.enableChannels.get()) {
            String newChannel = text.substring(1).trim();
            if (!newChannel.isEmpty()) {
                String channelId = "#" + newChannel;
                ChatHistoryManager history = ChatHistoryManager.getInstance();
                UUID ownerUuid = this.minecraft != null && this.minecraft.player != null
                        ? this.minecraft.player.getUUID() : null;
                if (ownerUuid != null && history.isServerConnected()) {
                    sendChannelPacket(ServerboundChannelActionPayload.Action.CREATE, channelId, ownerUuid);
                } else {
                    history.addChannel(channelId, ownerUuid);
                }
                currentConversation = channelId;
                if (this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.translatable("screen.chatsphere.mod_chat.switched_channel", newChannel), false);
                }
            }
            this.input.setValue("");
            return;
        }

        String stripped = text.startsWith("/") ? text.substring(1) : text;
        if (stripped.startsWith("msg ") || stripped.startsWith("tell ") || stripped.startsWith("w ")) {
            String[] parts = stripped.split(" ", 3);
            if (parts.length >= 3) {
                String targetName = parts[1];
                String msgText = parts[2];
                UUID localUuid = this.minecraft.player.getUUID();
                PlayerInfo targetInfo = null;
                for (PlayerInfo info : onlinePlayers.values()) {
                    if (info.getProfile().getName().equalsIgnoreCase(targetName)) {
                        targetInfo = info;
                        break;
                    }
                }
                if (targetInfo != null) {
                    UUID targetUuid = targetInfo.getProfile().getId();
                    String convId = localUuid.compareTo(targetUuid) < 0
                            ? localUuid + ":" + targetUuid
                            : targetUuid + ":" + localUuid;
                    currentConversation = convId;
                    ChatHistoryManager history = ChatHistoryManager.getInstance();
                    history.addPrivateConversation(convId, Component.literal(targetInfo.getProfile().getName()));
                    this.minecraft.player.connection.sendCommand("msg " + targetName + " " + msgText);
                    history.addMessage(
                            Component.literal(this.minecraft.player.getName().getString()),
                            localUuid,
                            Component.literal(msgText),
                            convId,
                            ChatMessageData.ConversationType.PRIVATE,
                            true);
                    this.input.setValue("");
                    this.scrollOffset = 0;
                    return;
                }
            }
        }

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ChatMessageData.ConversationType currentType = history.getConversationType(currentConversation);
        if (currentType == ChatMessageData.ConversationType.COMMAND) {
            this.minecraft.player.connection.sendCommand(stripped);
            history.addCommandEntry(this.minecraft.player.getUUID(), stripped);
            cmdHistoryEntries.add(stripped);
            cmdHistoryPos = cmdHistoryEntries.size();
            cn.sarskin.ChatSphere.client.ModClientEvents.lastCommandTime = System.currentTimeMillis();
            history.addCommandMessage(
                    Component.literal(stripped),
                    this.minecraft.player.getUUID(),
                    Component.literal(""),
                    true);
        } else if (currentType == ChatMessageData.ConversationType.PRIVATE) {
            sentHistory.add(text);
            historyPos = sentHistory.size();
            String targetUuidStr = currentConversation;
            if (currentConversation.contains(":")) {
                String[] parts = currentConversation.split(":");
                String localStr = this.minecraft.player.getUUID().toString();
                targetUuidStr = parts[0].equals(localStr) ? parts[1] : parts[0];
            }
            PlayerInfo info = targetUuidStr.length() == 36 ? onlinePlayers.get(targetUuidStr) : null;
            String targetName = info != null ? info.getProfile().getName() : targetUuidStr;
            this.minecraft.player.connection.sendCommand("msg " + targetName + " " + text);
            history.addMessage(
                    Component.literal(this.minecraft.player.getName().getString()),
                    this.minecraft.player.getUUID(),
                    Component.literal(text),
                    currentConversation,
                    ChatMessageData.ConversationType.PRIVATE,
                    true);
        } else {
            sentHistory.add(text);
            historyPos = sentHistory.size();
            if (history.isServerConnected()) {
                sendChannelChatPacket(currentConversation, text);
            } else {
                this.minecraft.player.connection.sendChat(text);
            }
            history.addMessage(
                    Component.literal(this.minecraft.player.getName().getString()),
                    this.minecraft.player.getUUID(),
                    Component.literal(text),
                    currentConversation,
                    ChatMessageData.ConversationType.CHANNEL,
                    true);
        }

        this.input.setValue("");
        this.scrollOffset = 0;
    }

    private void moveInHistory(int direction) {
        if (COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            int newPos = cmdHistoryPos + direction;
            if (newPos < 0 || newPos > cmdHistoryEntries.size()) return;
            if (newPos == cmdHistoryEntries.size()) {
                cmdHistoryPos = newPos;
                this.input.setValue("");
            } else {
                if (cmdHistoryPos == cmdHistoryEntries.size()) {
                    this.initial = this.input.getValue();
                }
                cmdHistoryPos = newPos;
                this.input.setValue(cmdHistoryEntries.get(newPos));
            }
        } else {
            int newPos = historyPos + direction;
            if (newPos < 0 || newPos > sentHistory.size()) return;
            if (newPos == sentHistory.size()) {
                historyPos = newPos;
                this.input.setValue("");
            } else {
                if (historyPos == sentHistory.size()) {
                    this.initial = this.input.getValue();
                }
                historyPos = newPos;
                this.input.setValue(sentHistory.get(newPos));
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (COMMAND_CONVERSATION_ID.equals(currentConversation)
                && this.commandSuggestions != null
                && this.commandSuggestions.mouseScrolled(scrollY)) {
            return true;
        }
        boolean scrollRightBlocked = showRightSidebar && ChatHistoryManager.getInstance().getConversationType(currentConversation) == ChatMessageData.ConversationType.CHANNEL && mouseX >= this.width - RIGHT_SIDEBAR_WIDTH;
        if (mouseX >= SIDEBAR_WIDTH && !scrollRightBlocked && mouseY < height - 26) {
            scrollOffset += (int) -scrollY;
            int maxScroll = Math.max(0, getMessageCount() - getVisibleMessageCount());
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        buildSidebarEntries();
        drawSidebar(guiGraphics, mouseX, mouseY);

        int screenWidth = this.width;
        int screenHeight = this.height;

        boolean showRight = showRightSidebar && ChatHistoryManager.getInstance().getConversationType(currentConversation) == ChatMessageData.ConversationType.CHANNEL;
        int effectiveWidth = showRight ? screenWidth - RIGHT_SIDEBAR_WIDTH : screenWidth;

        if (showRight) {
            drawRightSidebar(guiGraphics, mouseX, mouseY, effectiveWidth, screenHeight);
            drawToggleRightSidebarButton(guiGraphics, mouseX, mouseY, screenWidth, screenHeight, showRight, effectiveWidth);
        } else {
            drawToggleRightSidebarButton(guiGraphics, mouseX, mouseY, screenWidth, screenHeight, showRight, effectiveWidth);
        }

        drawHeaderBar(guiGraphics);
        renderMessages(guiGraphics, effectiveWidth, screenHeight);

        guiGraphics.fill(SIDEBAR_WIDTH, screenHeight - 14, effectiveWidth, screenHeight, 0x88000000);

        this.input.render(guiGraphics, mouseX, mouseY, partialTick);
        for (Renderable renderable : this.renderables) {
            if (renderable != this.input) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        if (COMMAND_CONVERSATION_ID.equals(currentConversation) && this.commandSuggestions != null) {
            this.commandSuggestions.render(guiGraphics, mouseX, mouseY);
        }
    }

    private void drawToggleRightSidebarButton(GuiGraphics guiGraphics, int mouseX, int mouseY, int screenWidth, int screenHeight, boolean rightSidebarVisible, int effectiveWidth) {
        int btnX = rightSidebarVisible ? effectiveWidth - 10 : screenWidth - 10;
        int btnY = 4;
        int btnW = 10;
        int btnH = 30;
        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, hovered ? 0x88333388 : 0x661A1A2E);
        guiGraphics.renderOutline(btnX, btnY, btnW, btnH, 0x446666AA);
        String arrow = rightSidebarVisible ? "\u25B6" : "\u25C0";
        guiGraphics.drawString(font, arrow, btnX + 1, btnY + 10, 0xFF888888, false);
    }

    private void drawRightSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY, int effectiveWidth, int screenHeight) {
        guiGraphics.fill(effectiveWidth, 0, effectiveWidth + RIGHT_SIDEBAR_WIDTH, screenHeight, 0xDD1A1A2E);

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ChatDataStore.ChannelConfig cfg = history.getChannelConfig(currentConversation);
        int channelMemberCount = cfg.members.size();
        Component header = Component.translatable(
                "screen.chatsphere.mod_chat.members_header",
                channelMemberCount);
        guiGraphics.drawString(font, header, effectiveWidth + 4, 4, 0xFF888888, false);

        Minecraft mc = Minecraft.getInstance();
        String localPlayerUuid = mc.player != null ? mc.player.getUUID().toString() : null;

        int y = 18;
        for (Map.Entry<String, PlayerInfo> entry : onlinePlayers.entrySet()) {
            String uuid = entry.getKey();
            String name = entry.getValue().getProfile().getName();

            boolean isChannelMember = cfg.members.contains(uuid);
            int textColor = 0xFF444444;
            if (uuid.equals(localPlayerUuid)) {
                textColor = 0xFFFFFF88;
            } else if (uuid.equals(cfg.owner)) {
                textColor = 0xFFAA88FF;
            } else if (cfg.admins.contains(uuid)) {
                textColor = 0xFF8888FF;
            } else if (cfg.mutedPlayers.contains(uuid)) {
                textColor = 0xFF666666;
            } else if (isChannelMember) {
                textColor = 0xFFCCCCCC;
            }

            if (!isChannelMember && !uuid.equals(localPlayerUuid) && !uuid.equals(cfg.owner) && !cfg.admins.contains(uuid)) {
                continue;
            }

            int maxLen = RIGHT_SIDEBAR_WIDTH / (font.width("W") + 1) - 1;
            String displayName = name.length() > maxLen ? name.substring(0, maxLen - 1) + ".." : name;
            guiGraphics.drawString(font, displayName, effectiveWidth + 4, y, textColor, false);
            y += 10;
            if (y > screenHeight - 10) break;
        }
    }

    private void buildSidebarEntries() {
        sidebarEntries.clear();
        ChatHistoryManager history = ChatHistoryManager.getInstance();

        sidebarEntries.add(new SidebarEntry(null,
                Component.translatable("screen.chatsphere.mod_chat.channels_header"),
                null, true, null));

        List<String> channelIds = history.getChannels();
        for (String id : channelIds) {
            if (id == null || id.isEmpty() || id.equals("null")) continue;
            Component display = history.getConversationDisplayName(id);
            if (display == null) display = Component.literal(id);
            sidebarEntries.add(new SidebarEntry(id, display,
                    ChatMessageData.ConversationType.CHANNEL, false, null));
        }
        if (channelIds.isEmpty()) {
            sidebarEntries.add(new SidebarEntry(ChatHistoryManager.DEFAULT_CHANNEL_ID,
                    Component.translatable("screen.chatsphere.mod_chat.default_channel"),
                    ChatMessageData.ConversationType.CHANNEL, false, null));
        }

        sidebarEntries.add(new SidebarEntry(null,
                Component.translatable("screen.chatsphere.mod_chat.private_header"),
                null, true, null));

        for (String id : history.getConversationIds()) {
            if (id == null || id.isEmpty() || id.equals("null")) continue;
            if (id.equals(ChatHistoryManager.DEFAULT_CHANNEL_ID)) continue; // 跳过默认频道被误加入私聊
            if (history.getConversationType(id) == ChatMessageData.ConversationType.PRIVATE) {
                Component name = history.getConversationDisplayName(id);
                if (name == null || name.getString().contains("Default") || name.getString().contains("公共")) continue;
                UUID targetUuid = null;
                if (id.contains(":") && this.minecraft != null && this.minecraft.player != null) {
                    try {
                        String[] parts = id.split(":");
                        String localStr = this.minecraft.player.getUUID().toString();
                        String targetStr = parts[0].equals(localStr) ? parts[1] : parts[0];
                        if (targetStr.length() == 36) {
                            targetUuid = UUID.fromString(targetStr);
                        }
                    } catch (Exception ignored) {}
                }
                sidebarEntries.add(new SidebarEntry(id, name,
                        ChatMessageData.ConversationType.PRIVATE, false, targetUuid));
            }
        }

        sidebarEntries.add(new SidebarEntry(null,
                Component.translatable("screen.chatsphere.mod_chat.commands_header"),
                null, true, null));
        sidebarEntries.add(new SidebarEntry(COMMAND_CONVERSATION_ID,
                Component.translatable("screen.chatsphere.mod_chat.console_name"),
                ChatMessageData.ConversationType.COMMAND, false, null));
    }

    private void drawSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xDD1A1A2E);

        int y = 10;
        int headerIdx = 0;
        for (int i = 0; i < sidebarEntries.size(); i++) {
            SidebarEntry entry = sidebarEntries.get(i);
            boolean hovered = mouseX < SIDEBAR_WIDTH && mouseX >= 0
                    && mouseY >= y && mouseY < y + SIDEBAR_ITEM_HEIGHT;
            boolean active = entry.conversationId != null && entry.conversationId.equals(currentConversation);

            if (entry.isHeader) {
                if (headerIdx > 0 && y > 20) {
                    guiGraphics.fill(4, y - 3, SIDEBAR_WIDTH - 4, y - 2, 0x44FFFFFF);
                }
                guiGraphics.drawString(font, entry.displayName, 8, y + 3, 0xFF888888, false);
                if (headerIdx == 0) {
                    int plusX = SIDEBAR_WIDTH - 6 - 10;
                    int plusY = y + (SIDEBAR_ITEM_HEIGHT - 8) / 2;
                    boolean plusHovered = mouseX >= plusX && mouseX <= plusX + 10
                            && mouseY >= plusY && mouseY <= plusY + 10;
                    guiGraphics.fill(plusX, plusY, plusX + 10, plusY + 10,
                            plusHovered ? 0xFF44AA44 : 0xFF333388);
                    guiGraphics.drawString(font, "+", plusX + 2, plusY + 1, 0xFFFFFFFF, false);

                    int joinX = plusX - 14;
                    int joinY = plusY;
                    boolean joinHovered = mouseX >= joinX && mouseX <= joinX + 10
                            && mouseY >= joinY && mouseY <= joinY + 10;
                    guiGraphics.fill(joinX, joinY, joinX + 10, joinY + 10,
                            joinHovered ? 0xFF4488AA : 0xFF333366);
                    guiGraphics.drawString(font, "=", joinX + 1, joinY + 1, 0xFFFFFFFF, false);
                }
                headerIdx++;
                y += SIDEBAR_ITEM_HEIGHT;
            } else {
                int bgColor = active ? 0x66333388 : (hovered ? 0x44333388 : 0x00000000);
                if (bgColor != 0) {
                    guiGraphics.fill(2, y, SIDEBAR_WIDTH - 2, y + SIDEBAR_ITEM_HEIGHT, bgColor);
                }
                if (entry.type == ChatMessageData.ConversationType.COMMAND) {
                    guiGraphics.drawString(font, ">", 4, y + 4, 0xFF66AA66, false);
                    guiGraphics.drawString(font, entry.displayName, 12, y + 4,
                            active ? 0xFFFFFFFF : 0xFFAAAAAA, false);
                } else if (entry.type == ChatMessageData.ConversationType.PRIVATE) {
                    drawPlayerFace(guiGraphics, entry.targetUuid, 4, y + 3, SIDEBAR_AVATAR_SIZE);
                    guiGraphics.drawString(font, entry.displayName, 4 + SIDEBAR_AVATAR_SIZE + 2, y + 4,
                            0xFFCCCCCC, false);
                } else {
                    MutableComponent label = Component.literal("# ");
                    label.append(entry.displayName);
                    guiGraphics.drawString(font, label, 6, y + 4,
                            active ? 0xFFFFFFFF : 0xFFAAAAAA, false);

                    int gearX = SIDEBAR_WIDTH - 6 - CONFIG_ICON_SIZE;
                    int gearY = y + (SIDEBAR_ITEM_HEIGHT - CONFIG_ICON_SIZE) / 2;
                    boolean gearHovered = mouseX >= gearX && mouseX <= gearX + CONFIG_ICON_SIZE
                            && mouseY >= gearY && mouseY <= gearY + CONFIG_ICON_SIZE;
                    int tint = gearHovered ? 0xFFFFFFFF : 0xCCAAAAAA;
                    guiGraphics.setColor(
                            ((tint >> 16) & 0xFF) / 255f,
                            ((tint >> 8) & 0xFF) / 255f,
                            (tint & 0xFF) / 255f,
                            ((tint >> 24) & 0xFF) / 255f);
                    guiGraphics.blit(SETTINGS_ICON, gearX, gearY, 0, 0, CONFIG_ICON_SIZE, CONFIG_ICON_SIZE, CONFIG_ICON_SIZE, CONFIG_ICON_SIZE);
                    guiGraphics.setColor(1f, 1f, 1f, 1f);
                }
                y += SIDEBAR_ITEM_HEIGHT;
            }
        }
    }

    private void drawPlayerFace(GuiGraphics guiGraphics, UUID uuid, int x, int y, int size) {
        // 如果 UUID 为 null，直接返回，不绘制任何内容
        if (uuid == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        PlayerSkin skin;
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                skin = info.getSkin();
            } else {
                skin = DefaultPlayerSkin.get(uuid);
            }
        } else {
            skin = DefaultPlayerSkin.get(uuid);
        }
        PlayerFaceRenderer.draw(guiGraphics, skin, x, y, size);
    }

    private void drawHeaderBar(GuiGraphics guiGraphics) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ChatMessageData.ConversationType type = history.getConversationType(currentConversation);

        Component header;
        if (type == ChatMessageData.ConversationType.COMMAND) {
            header = Component.literal("> ")
                    .append(history.getConversationDisplayName(currentConversation))
                    .withStyle(ChatFormatting.BOLD);
        } else if (type == ChatMessageData.ConversationType.CHANNEL) {
            header = Component.literal("# ").append(history.getConversationDisplayName(currentConversation))
                    .withStyle(ChatFormatting.BOLD);
        } else {
            header = Component.literal("").append(history.getConversationDisplayName(currentConversation))
                    .withStyle(ChatFormatting.BOLD);
        }

        boolean showRight = showRightSidebar && type == ChatMessageData.ConversationType.CHANNEL;
        int effectiveWidth = showRight ? this.width - RIGHT_SIDEBAR_WIDTH : this.width;
        int hw = font.width(header);
        int headerX = SIDEBAR_WIDTH + (effectiveWidth - SIDEBAR_WIDTH - hw) / 2;
        guiGraphics.drawString(font, header, headerX, 3, 0xFFFFFFFF, false);
    }

    private void renderMessages(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        int chatAreaLeft = SIDEBAR_WIDTH + 4;
        int chatAreaRight = screenWidth - 4;
        int chatAreaTop = HEADER_BAR_HEIGHT + 6;
        int chatAreaBottom = screenHeight - 18;

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        List<ChatMessageData> messages = history.getMessagesByConversation(currentConversation);
        int totalMessages = messages.size();
        if (totalMessages == 0) return;

        int yOffset = chatAreaBottom;
        int idx = Math.max(0, totalMessages - 1 - scrollOffset);
        for (int i = idx; i >= 0; i--) {
            ChatMessageData msg = messages.get(i);
            int bubbleHeight = renderMessageBubble(guiGraphics, msg, chatAreaLeft, chatAreaRight, yOffset);
            yOffset -= bubbleHeight + 2;
            if (yOffset < chatAreaTop) break;
        }
    }

    private int renderMessageBubble(GuiGraphics guiGraphics, ChatMessageData msg,
                                    int areaLeft, int areaRight, int y) {
        Minecraft mc = Minecraft.getInstance();
        boolean showName = ModClientConfig.CONFIG.showSenderName.get();
        boolean showTime = ModClientConfig.CONFIG.showTimestamp.get();
        boolean showAvatar = ModClientConfig.CONFIG.showAvatar.get();

        boolean isCommand = msg.conversationType() == ChatMessageData.ConversationType.COMMAND;

        Component contentText;
        MutableComponent infoLine = Component.literal("");
        if (isCommand) {
            contentText = msg.senderName().copy();
            if (msg.isOwn()) {
                infoLine.append(Component.literal("\u8F93\u5165").withStyle(ChatFormatting.DARK_GREEN));
            } else {
                infoLine.append(Component.literal("\u8F93\u51FA").withStyle(ChatFormatting.GRAY));
            }
        } else {
            contentText = msg.content().copy();
            if (showName) {
                infoLine.append(msg.senderName().copy().withStyle(ChatFormatting.AQUA));
            }
        }
        if (showTime && !isCommand) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            if (showName) infoLine.append("  ");
            infoLine.append(Component.literal(sdf.format(new Date(msg.timestamp()))).withStyle(ChatFormatting.GRAY));
        }

        int contentWidth = mc.font.width(contentText);
        int infoWidth = mc.font.width(infoLine);
        int maxLineWidth = Math.max(contentWidth, infoWidth);
        if (isCommand) {
            int prefixWidth = mc.font.width(msg.isOwn() ? "> " : "\u2192 ");
            maxLineWidth = Math.max(maxLineWidth, contentWidth + prefixWidth);
        }

        int lineH = mc.font.lineHeight;
        int bubbleW = Math.min(maxLineWidth + BUBBLE_HPAD * 2, areaRight - areaLeft - 30);
        int bubbleH = (isCommand ? 1 : 2) * lineH + BUBBLE_VPAD * 2 + 1;

        int avatarSize = AVATAR_SIZE;
        int avatarX = areaLeft;
        int bubbleX;

        if (isCommand || msg.isOwn()) {
            bubbleX = areaRight - bubbleW;
        } else {
            int offset = (showAvatar ? avatarSize + 4 : 0);
            bubbleX = Math.max(areaLeft + offset, areaLeft);
        }
        int bubbleY = y - bubbleH;
        if (bubbleY < 0) return bubbleH + 2;

        int bgColor;
        if (isCommand) {
            bgColor = msg.isOwn() ? 0xFF2D2D2D : 0xFF1E1E2E;
        } else {
            bgColor = msg.isOwn() ? 0xFFDCF8C6 : 0xFFFFFFFF;
        }
        int borderColor = isCommand ? 0xFF444444 : 0xFFCCCCCC;
        guiGraphics.fill(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH, bgColor);
        guiGraphics.renderOutline(bubbleX, bubbleY, bubbleW, bubbleH, borderColor);

        if (showAvatar && !msg.isOwn() && !isCommand) {
            UUID senderUuid = msg.senderUuid();
            if (senderUuid != null) {
                drawPlayerFace(guiGraphics, senderUuid, avatarX, bubbleY + 4, avatarSize);
            }
        }

        int textX = bubbleX + BUBBLE_HPAD;
        if (isCommand) {
            Component displayText = msg.isOwn()
                    ? Component.literal("> ").withStyle(ChatFormatting.GREEN).append(contentText)
                    : Component.literal("\u2192 ").withStyle(ChatFormatting.GRAY).append(contentText);
            guiGraphics.drawString(mc.font, displayText, textX, bubbleY + BUBBLE_VPAD,
                    0xFFFFFFFF, false);
        } else {
            guiGraphics.drawString(mc.font, infoLine, textX, bubbleY + BUBBLE_VPAD, 0xFF555555, false);
            guiGraphics.drawString(mc.font, contentText, textX, bubbleY + BUBBLE_VPAD + lineH + 1,
                    msg.isOwn() ? 0xFF000000 : 0xFF222222, false);
        }

        return bubbleH + 2;
    }

    @Override
    protected void insertText(String text, boolean overwrite) {
        if (overwrite) {
            this.input.setValue(text);
        } else {
            this.input.insertText(text);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0x88000000, 0x44000000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int getMessageCount() {
        return ChatHistoryManager.getInstance().getMessagesByConversation(currentConversation).size();
    }

    private int getVisibleMessageCount() {
        return (height - 50) / (font.lineHeight + 10);
    }

    private void sendChannelChatPacket(String channelId, String text) {
        if (this.minecraft == null || this.minecraft.getConnection() == null) return;
        var conn = this.minecraft.getConnection().getConnection();
        conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(
                        ServerboundChannelActionPayload.Action.SEND_CHAT,
                        channelId, this.minecraft.player.getUUID(),
                        true, text, "", List.of(), List.of(), List.of(), "")));
    }

    private void sendChannelPacket(ServerboundChannelActionPayload.Action action, String channelId, UUID ownerUuid) {
        if (this.minecraft == null || this.minecraft.getConnection() == null) return;
        var conn = this.minecraft.getConnection().getConnection();
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ChatDataStore.ChannelConfig cfg = history.getChannelConfig(channelId);
        conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(action, channelId, ownerUuid,
                        cfg.isPublic, cfg.description, cfg.displayName,
                        cfg.admins, cfg.mutedPlayers, cfg.invitedPlayers, "")));
    }

    private static class SidebarEntry {
        final String conversationId;
        final Component displayName;
        final ChatMessageData.ConversationType type;
        final boolean isHeader;
        final UUID targetUuid;

        SidebarEntry(String conversationId, Component displayName,
                     ChatMessageData.ConversationType type, boolean isHeader, UUID targetUuid) {
            this.conversationId = conversationId;
            this.displayName = displayName;
            this.type = type;
            this.isHeader = isHeader;
            this.targetUuid = targetUuid;
        }
    }
}