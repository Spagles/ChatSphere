package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.*;

public class ChannelMemberScreen extends Screen {
    public enum Mode { MANAGE, ADMIN, INVITE, VIEW }

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 200;

    private final Screen parent;
    private final String channelId;
    private final Mode mode;
    private final ChatDataStore.ChannelConfig config;

    private PlayerListWidget playerList;
    private EditBox searchBox;
    private StyledButton actionBtn;

    public ChannelMemberScreen(Screen parent, String channelId, Mode mode) {
        super(Component.translatable(getTitleKeyByMode(mode)));
        this.parent = parent;
        this.channelId = channelId;
        this.mode = mode;
        this.config = ChatHistoryManager.getInstance().getChannelConfig(channelId);
    }

    private static String getTitleKeyByMode(Mode mode) {
        return switch (mode) {
            case MANAGE -> "screen.chatsphere.channel_member.title_manage";
            case ADMIN -> "screen.chatsphere.channel_member.title_admin";
            case INVITE -> "screen.chatsphere.channel_member.title_invite";
            case VIEW -> "screen.chatsphere.channel_member.title_view";
        };
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int topY = (this.height - PANEL_HEIGHT) / 2;

        if (mode != Mode.VIEW) {
            searchBox = new EditBox(this.font, cx - 140, topY + 10, 180, 16,
                    Component.translatable("screen.chatsphere.channel_member.search"));
            searchBox.setMaxLength(32);
            searchBox.setBordered(true);
            searchBox.setHint(Component.translatable("screen.chatsphere.channel_member.search_hint"));
            addWidget(searchBox);

            actionBtn = addRenderableWidget(StyledButton.styledBuilder(
                    Component.translatable(getActionKey()),
                    btn -> performAction()
            ).bounds(cx + 45, topY + 10, 95, 16).style(
                    mode == Mode.MANAGE ? StyledButton.Style.DANGER :
                    mode == Mode.ADMIN ? StyledButton.Style.DEFAULT :
                    StyledButton.Style.CONFIRM
            ).build());
        }

        int listTop = (mode != Mode.VIEW) ? topY + 32 : topY + 10;
        int listHeight = (mode != Mode.VIEW) ? PANEL_HEIGHT - 72 : PANEL_HEIGHT - 50;

        playerList = new PlayerListWidget(cx - 140, listTop, 280, listHeight, 18);
        refreshPlayerList();
        addWidget(playerList);

        addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.channel_member.back"),
                btn -> onClose()
        ).bounds(cx - 40, topY + PANEL_HEIGHT - 30, 80, 20).style(StyledButton.Style.CANCEL).build());
    }

    private String getActionKey() {
        return switch (mode) {
            case MANAGE -> "screen.chatsphere.channel_member.action_mute";
            case ADMIN -> "screen.chatsphere.channel_member.action_admin";
            case INVITE -> "screen.chatsphere.channel_member.action_invite";
            case VIEW -> "";
        };
    }

    private void refreshPlayerList() {
        playerList.clearEntries();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        Collection<PlayerInfo> onlinePlayers = mc.getConnection().getOnlinePlayers();

        if (mode == Mode.VIEW) {
            for (PlayerInfo info : onlinePlayers) {
                String uuid = info.getProfile().getId().toString();
                if (!config.members.contains(uuid)) continue;
                String name = info.getProfile().getName();
                String role = getMemberRole(uuid);
                boolean isOwner = uuid.equals(config.owner);
                playerList.addListEntry(new PlayerEntry(uuid, name, role, isOwner));
            }
            return;
        }

        if (mc.player == null) return;

        if (mode == Mode.INVITE) {
            for (PlayerInfo info : onlinePlayers) {
                String uuid = info.getProfile().getId().toString();
                if (uuid.equals(mc.player.getUUID().toString())) continue;
                String name = info.getProfile().getName();
                boolean isExisting = config.invitedPlayers.contains(uuid);
                boolean isOwner = uuid.equals(config.owner);
                playerList.addListEntry(new PlayerEntry(uuid, name, isExisting, isOwner));
            }
        } else {
            Set<String> existingSet = getExistingSet();
            for (PlayerInfo info : onlinePlayers) {
                String uuid = info.getProfile().getId().toString();
                if (!config.members.contains(uuid)) continue;
                String name = info.getProfile().getName();
                boolean isExisting = existingSet.contains(uuid);
                boolean isOwner = uuid.equals(config.owner);
                playerList.addListEntry(new PlayerEntry(uuid, name, isExisting, isOwner));
            }
        }
    }

    private String getMemberRole(String uuid) {
        if (uuid.equals(config.owner)) return "owner";
        if (config.admins.contains(uuid)) return "admin";
        if (config.members.contains(uuid)) return "member";
        return "";
    }

    private Set<String> getExistingSet() {
        return switch (mode) {
            case MANAGE -> new HashSet<>(config.mutedPlayers);
            case ADMIN -> new HashSet<>(config.admins);
            case INVITE -> new HashSet<>(config.invitedPlayers);
            case VIEW -> Set.of();
        };
    }

    private void performAction() {
        PlayerEntry selected = playerList.getSelected();
        if (selected == null || selected.isOwner) return;
        List<String> targetSet = getTargetSet();
        if (selected.isExisting) {
            targetSet.remove(selected.uuid);
            playerList.removeListEntry(selected);
        } else {
            targetSet.add(selected.uuid);
            playerList.markExisting(selected.uuid);
            selected.isExisting = true;
        }
        ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
    }

    private List<String> getTargetSet() {
        return switch (mode) {
            case MANAGE -> config.mutedPlayers;
            case ADMIN -> config.admins;
            case INVITE -> config.invitedPlayers;
            case VIEW -> List.of();
        };
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int topY = (this.height - PANEL_HEIGHT) / 2;

        guiGraphics.fill(cx - PANEL_WIDTH / 2, topY, cx + PANEL_WIDTH / 2, topY + PANEL_HEIGHT, 0xCC222244);
        guiGraphics.renderOutline(cx - PANEL_WIDTH / 2, topY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF6666AA);

        String titleStr = this.title.getString();
        guiGraphics.drawString(this.font, titleStr,
                cx - this.font.width(titleStr) / 2, topY + 2, 0xFFFFFFFF, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        if (searchBox != null && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            filterPlayerList();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.charTyped(codePoint, modifiers)) {
            filterPlayerList();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void filterPlayerList() {
        String query = searchBox.getValue().toLowerCase();
        for (int i = 0; i < playerList.children().size(); i++) {
            PlayerEntry entry = (PlayerEntry) playerList.children().get(i);
            boolean visible = query.isEmpty() || entry.name.toLowerCase().contains(query);
            entry.setVisible(visible);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ChatHistoryManager.getInstance().save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ChannelConfigScreen(parent, channelId));
        }
    }

    private static class PlayerEntry extends ObjectSelectionList.Entry<PlayerEntry> {
        final String uuid;
        final String name;
        boolean isExisting;
        final boolean isOwner;
        final String role;
        private boolean visible = true;

        PlayerEntry(String uuid, String name, boolean isExisting, boolean isOwner) {
            this.uuid = uuid;
            this.name = name;
            this.isExisting = isExisting;
            this.isOwner = isOwner;
            this.role = "";
        }

        PlayerEntry(String uuid, String name, String role, boolean isOwner) {
            this.uuid = uuid;
            this.name = name;
            this.isExisting = true;
            this.isOwner = isOwner;
            this.role = role;
        }

        void setVisible(boolean v) { this.visible = v; }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int bgColor;
            if (isOwner) bgColor = 0x556644AA;
            else if (isExisting) bgColor = 0x55338833;
            else bgColor = 0x33333388;
            if (hovering) bgColor = 0x556666AA;
            guiGraphics.fill(left, top, left + width, top + height, bgColor);
            int textColor = isOwner ? 0xFFAA88FF : (isExisting ? 0xFF88FF88 : 0xFFCCCCCC);
            guiGraphics.drawString(Minecraft.getInstance().font, name, left + 4, top + 3, textColor, false);
            if (isOwner) {
                guiGraphics.drawString(Minecraft.getInstance().font,
                        Component.translatable("screen.chatsphere.channel_member.owner"),
                        left + width - 30, top + 3, 0xFFAA88FF, false);
            } else if (!role.isEmpty()) {
                guiGraphics.drawString(Minecraft.getInstance().font,
                        Component.translatable("screen.chatsphere.channel_member.role_" + role),
                        left + width - 30, top + 3, 0xFF8888FF, false);
            } else if (isExisting) {
                guiGraphics.drawString(Minecraft.getInstance().font,
                        Component.translatable("screen.chatsphere.channel_member.checked"),
                        left + width - 14, top + 3, 0xFF44FF44, false);
            }
        }

        @Override
        public Component getNarration() {
            return Component.literal(name);
        }
    }

    private static class PlayerListWidget extends ObjectSelectionList<PlayerEntry> {
        private PlayerEntry selected;

        PlayerListWidget(int x, int y, int w, int h, int itemHeight) {
            super(Minecraft.getInstance(), w, h, y, itemHeight);
            this.setX(x);
        }

        @Override
        protected void renderListBackground(GuiGraphics guiGraphics) {
        }

        @Override
        protected void renderListSeparators(GuiGraphics guiGraphics) {
        }

        protected void clearEntries() {
            this.children().clear();
            selected = null;
        }

        void addListEntry(PlayerEntry entry) {
            super.addEntry(entry);
        }

        void removeListEntry(PlayerEntry entry) {
            super.removeEntry(entry);
            if (selected == entry) selected = null;
        }

        void markExisting(String uuid) {
            for (PlayerEntry e : this.children()) {
                if (e.uuid.equals(uuid)) {
                    e.isExisting = true;
                    break;
                }
            }
        }

        public PlayerEntry getSelected() { return selected; }

        @Override
        public void setSelected(PlayerEntry entry) {
            super.setSelected(entry);
            selected = entry;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getRight() - 6;
        }

        @Override
        protected void renderListItems(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int y0 = this.getY();
            for (int i = 0; i < this.children().size(); i++) {
                PlayerEntry entry = this.children().get(i);
                if (!entry.visible) continue;
                int y = y0 + i * this.itemHeight - (int) this.getScrollAmount();
                if (y < this.getY() - this.itemHeight || y > this.getY() + this.height) continue;
                entry.render(guiGraphics, i, y, this.getX(), this.getWidth(), this.itemHeight, mouseX, mouseY, entry == this.selected, partialTick);
            }
        }
    }
}