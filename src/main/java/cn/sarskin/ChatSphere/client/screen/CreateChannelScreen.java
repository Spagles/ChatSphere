package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class CreateChannelScreen extends Screen {
    private static final int POPUP_WIDTH = 200;
    private static final int POPUP_HEIGHT = 80;

    private final Screen parent;
    private EditBox nameInput;
    private StyledButton confirmBtn;
    private StyledButton cancelBtn;

    public CreateChannelScreen(Screen parent) {
        super(Component.translatable("screen.chatsphere.create_channel.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int popupX = (this.width - POPUP_WIDTH) / 2;
        int popupY = (this.height - POPUP_HEIGHT) / 2;

        this.nameInput = new EditBox(this.font, popupX + 10, popupY + 20, POPUP_WIDTH - 20, 16,
                Component.translatable("screen.chatsphere.create_channel.input_label"));
        this.nameInput.setMaxLength(32);
        this.nameInput.setBordered(true);
        this.nameInput.setHint(Component.translatable("screen.chatsphere.create_channel.input_hint"));
        this.addWidget(this.nameInput);
        this.setInitialFocus(this.nameInput);

        this.confirmBtn = this.addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.create_channel.confirm"),
                btn -> confirm()
        ).bounds(popupX + 10, popupY + 50, 80, 20).style(StyledButton.Style.CONFIRM).build());

        this.cancelBtn = this.addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.create_channel.cancel"),
                btn -> cancel()
        ).bounds(popupX + POPUP_WIDTH - 10 - 80, popupY + 50, 80, 20).style(StyledButton.Style.CANCEL).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int popupX = (this.width - POPUP_WIDTH) / 2;
        int popupY = (this.height - POPUP_HEIGHT) / 2;

        guiGraphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, 0xCC222244);
        guiGraphics.renderOutline(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, 0xFF6666AA);

        String title = this.title.getString();
        guiGraphics.drawString(this.font, title,
                popupX + (POPUP_WIDTH - this.font.width(title)) / 2,
                popupY + 5, 0xFFFFFFFF, false);

        this.nameInput.render(guiGraphics, mouseX, mouseY, partialTick);

        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.nameInput.mouseClicked(mouseX, mouseY, button);
        if (this.confirmBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.cancelBtn.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            cancel();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        String name = this.nameInput.getValue().trim();
        if (!name.isEmpty()) {
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            UUID ownerUuid = this.minecraft != null && this.minecraft.player != null
                    ? this.minecraft.player.getUUID() : null;
            String channelId = name.startsWith("#") ? name : "#" + name;
            if (ownerUuid != null && history.isServerConnected() && this.minecraft != null
                    && this.minecraft.getConnection() != null) {
                var conn = this.minecraft.getConnection().getConnection();
                conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                        new ServerboundChannelActionPayload(
                                ServerboundChannelActionPayload.Action.CREATE,
                                channelId, ownerUuid, true, "", "",
                                List.of(), List.of(), List.of(), "")));
            } else {
                history.addChannel(name, ownerUuid);
            }
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void cancel() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
