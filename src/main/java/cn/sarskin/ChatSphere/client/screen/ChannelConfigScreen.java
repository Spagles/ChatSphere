package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ChannelConfigScreen extends Screen {
    private static final int CLOSE_ICON_SIZE = 10;
    private static final ResourceLocation CLOSE_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/close_icon.png");

    private final Screen parent;
    private final String channelId;
    private ChatDataStore.ChannelConfig config;

    private int panelWidth;
    private int panelHeight;
    private StyledButton publicToggle;
    private EditBox descriptionInput;
    private EditBox displayNameInput;

    public ChannelConfigScreen(Screen parent, String channelId) {
        super(Component.translatable("screen.chatsphere.channel_config.title", channelId.substring(1)));
        this.parent = parent;
        this.channelId = channelId;
    }

    @Override
    protected void init() {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        config = history.getChannelConfig(channelId);

        UUID playerUuid = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
        boolean isOwner = history.isOwner(channelId, playerUuid);
        boolean canEdit = isOwner || history.isAdmin(channelId, playerUuid);

        if (!canEdit) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
            return;
        }

        panelWidth = Math.min(Math.max((int)(this.width * 0.55), 260), 420);
        panelHeight = Math.min(Math.max((int)(this.height * 0.65), 280), 440);

        int cx = this.width / 2;
        int topY = (this.height - panelHeight) / 2;
        int btnW = panelWidth - 40;
        int btnX = cx - btnW / 2;
        int fieldH = 16;
        int btnH = 20;

        int yCursor = topY + 34;

        publicToggle = addRenderableWidget(StyledButton.styledBuilder(
                buildPublicLabel(),
                btn -> {
                    config.isPublic = !config.isPublic;
                    btn.setMessage(buildPublicLabel());
                    ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
                }
        ).bounds(btnX, yCursor, btnW, btnH).style(
                config.isPublic ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.TOGGLE_OFF
        ).build());

        yCursor += btnH + 10;

        Component displayNameLabel = Component.translatable("screen.chatsphere.channel_config.display_name");
        displayNameInput = new EditBox(this.font, btnX, yCursor, btnW, fieldH, displayNameLabel);
        displayNameInput.setMaxLength(32);
        displayNameInput.setBordered(true);
        displayNameInput.setValue(config.displayName);
        displayNameInput.setResponder(val -> {
            config.displayName = val;
            ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
        });
        addWidget(displayNameInput);

        yCursor += fieldH + 10;

        Component descLabel = Component.translatable("screen.chatsphere.channel_config.description");
        descriptionInput = new EditBox(this.font, btnX, yCursor, btnW, fieldH, descLabel);
        descriptionInput.setMaxLength(64);
        descriptionInput.setBordered(true);
        descriptionInput.setValue(config.description);
        descriptionInput.setResponder(val -> {
            config.description = val;
            ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
        });
        addWidget(descriptionInput);

        yCursor += fieldH + 16;

        int regenBtnW = 90;
        addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.channel_config.regenerate_code"),
                btn -> {
                    config.inviteCode = generateCode();
                    ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
                }
        ).bounds(btnX + btnW - regenBtnW, yCursor, regenBtnW, btnH).build());

        yCursor += btnH + 10;

        if (isOwner) {
            addRenderableWidget(StyledButton.styledBuilder(
                    Component.translatable("screen.chatsphere.channel_config.delete_channel"),
                    btn -> deleteChannel()
            ).bounds(btnX, yCursor, btnW, btnH).style(StyledButton.Style.DANGER).build());
            yCursor += btnH + 12;
        }

        addRenderableWidget(StyledButton.styledBuilder(
                CommonComponents.GUI_DONE,
                btn -> onClose()
        ).bounds(cx - 50, topY + panelHeight - 26, 100, btnH).style(StyledButton.Style.CONFIRM).build());
    }

    private void deleteChannel() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        UUID playerUuid = this.minecraft.player.getUUID();
        if (!ChatHistoryManager.getInstance().isOwner(channelId, playerUuid)) return;
        if (this.minecraft.getConnection() != null) {
            var conn = this.minecraft.getConnection().getConnection();
            conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                    new ServerboundChannelActionPayload(
                            ServerboundChannelActionPayload.Action.REMOVE_CHANNEL,
                            channelId, playerUuid, true, "", "",
                            List.of(), List.of(), List.of(), "")));
        }
        onClose();
    }

    private static String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rng = new Random();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private Component buildPublicLabel() {
        MutableComponent label = Component.translatable("screen.chatsphere.channel_config.public_label");
        if (config.isPublic) {
            label.append(Component.translatable("screen.chatsphere.channel_config.enabled").withStyle(ChatFormatting.GREEN));
        } else {
            label.append(Component.translatable("screen.chatsphere.channel_config.disabled").withStyle(ChatFormatting.RED));
        }
        return label;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int topY = (this.height - panelHeight) / 2;

        guiGraphics.fill(cx - panelWidth / 2, topY, cx + panelWidth / 2, topY + panelHeight, 0xCC1A1A2E);
        guiGraphics.renderOutline(cx - panelWidth / 2, topY, panelWidth, panelHeight, 0xFF4444AA);

        String titleStr = this.title.getString();
        guiGraphics.drawString(this.font, titleStr,
                cx - this.font.width(titleStr) / 2, topY + 8, 0xFFFFFFFF, false);

        int closeX = cx + panelWidth / 2 - CLOSE_ICON_SIZE - 6;
        int closeY = topY + 6;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + CLOSE_ICON_SIZE
                && mouseY >= closeY && mouseY <= closeY + CLOSE_ICON_SIZE;
        int closeTint = closeHovered ? 0xFFFF4444 : 0xFFAAAAAA;
        guiGraphics.setColor(
                ((closeTint >> 16) & 0xFF) / 255f,
                ((closeTint >> 8) & 0xFF) / 255f,
                (closeTint & 0xFF) / 255f,
                ((closeTint >> 24) & 0xFF) / 255f);
        guiGraphics.blit(CLOSE_ICON, closeX, closeY, 0, 0, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE, CLOSE_ICON_SIZE);
        guiGraphics.setColor(1f, 1f, 1f, 1f);

        int btnW = panelWidth - 40;
        int btnX = cx - btnW / 2;
        int yCursor = topY + 34;

        yCursor += 20 + 10;

        Component displayNameLabel = Component.translatable("screen.chatsphere.channel_config.display_name");
        guiGraphics.drawString(this.font, displayNameLabel, btnX, yCursor - 10, 0xFFAAAAAA, false);
        if (this.displayNameInput != null) {
            this.displayNameInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        yCursor += 16 + 10;

        Component descLabel = Component.translatable("screen.chatsphere.channel_config.description");
        guiGraphics.drawString(this.font, descLabel, btnX, yCursor - 10, 0xFFAAAAAA, false);
        if (this.descriptionInput != null) {
            this.descriptionInput.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        yCursor += 16 + 16;

        int memberCount = config.members.size();

        Component statsLine = Component.literal("§7")
                .append(Component.translatable("screen.chatsphere.channel_config.member_count", memberCount));
        guiGraphics.drawString(this.font, statsLine, btnX, yCursor, 0xFFCCCCCC, false);

        yCursor += 10;
        long onlineMemberCount = config.members.stream()
                .filter(u -> {
                    if (this.minecraft == null || this.minecraft.getConnection() == null) return false;
                    return this.minecraft.getConnection().getOnlinePlayers().stream()
                            .anyMatch(p -> p.getProfile().getId().toString().equals(u));
                }).count();
        Component onlineLine = Component.literal("§7")
                .append(Component.translatable("screen.chatsphere.channel_config.online_member_count", onlineMemberCount));
        guiGraphics.drawString(this.font, onlineLine, btnX, yCursor, 0xFFCCCCCC, false);

        yCursor += 12;
        String code = config.inviteCode.isEmpty() ? "N/A" : config.inviteCode;
        Component codeLine = Component.literal("§7")
                .append(Component.translatable("screen.chatsphere.channel_config.invite_code", code));
        guiGraphics.drawString(this.font, codeLine, btnX, yCursor, 0xFFCCCCCC, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = this.width / 2;
            int topY = (this.height - panelHeight) / 2;
            int closeX = cx + panelWidth / 2 - CLOSE_ICON_SIZE - 6;
            int closeY = topY + 6;
            if (mouseX >= closeX && mouseX <= closeX + CLOSE_ICON_SIZE
                    && mouseY >= closeY && mouseY <= closeY + CLOSE_ICON_SIZE) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ChatHistoryManager.getInstance().save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}