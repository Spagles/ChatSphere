package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ConfigScreen extends Screen {
    private static final int TITLE_COLOR = 0xFFFFFF;

    public ConfigScreen() {
        super(Component.translatable("screen.chatsphere.config.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 40;

        addRenderableWidget(StyledButton.styledBuilder(
                toggleLabel(ModClientConfig.CONFIG.showTimestamp.get(), "config.chatsphere.show_timestamp"),
                btn -> {
                    ModClientConfig.CONFIG.showTimestamp.set(!ModClientConfig.CONFIG.showTimestamp.get());
                    btn.setMessage(toggleLabel(ModClientConfig.CONFIG.showTimestamp.get(), "config.chatsphere.show_timestamp"));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        y += 24;
        addRenderableWidget(StyledButton.styledBuilder(
                toggleLabel(ModClientConfig.CONFIG.showSenderName.get(), "config.chatsphere.show_sender_name"),
                btn -> {
                    ModClientConfig.CONFIG.showSenderName.set(!ModClientConfig.CONFIG.showSenderName.get());
                    btn.setMessage(toggleLabel(ModClientConfig.CONFIG.showSenderName.get(), "config.chatsphere.show_sender_name"));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        y += 24;
        addRenderableWidget(StyledButton.styledBuilder(
                toggleLabel(ModClientConfig.CONFIG.showAvatar.get(), "config.chatsphere.show_avatar"),
                btn -> {
                    ModClientConfig.CONFIG.showAvatar.set(!ModClientConfig.CONFIG.showAvatar.get());
                    btn.setMessage(toggleLabel(ModClientConfig.CONFIG.showAvatar.get(), "config.chatsphere.show_avatar"));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        y += 24;
        addRenderableWidget(StyledButton.styledBuilder(
                toggleLabel(ModClientConfig.CONFIG.enableChannels.get(), "config.chatsphere.enable_channels"),
                btn -> {
                    ModClientConfig.CONFIG.enableChannels.set(!ModClientConfig.CONFIG.enableChannels.get());
                    btn.setMessage(toggleLabel(ModClientConfig.CONFIG.enableChannels.get(), "config.chatsphere.enable_channels"));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        y += 30;
        addRenderableWidget(StyledButton.styledBuilder(
                toggleLabel(ModClientConfig.CONFIG.notificationSound.get(), "config.chatsphere.notification_sound"),
                btn -> {
                    ModClientConfig.CONFIG.notificationSound.set(!ModClientConfig.CONFIG.notificationSound.get());
                    btn.setMessage(toggleLabel(ModClientConfig.CONFIG.notificationSound.get(), "config.chatsphere.notification_sound"));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        y += 24;
        addRenderableWidget(StyledButton.styledBuilder(
                toggleLabel(ModClientConfig.CONFIG.notificationFlash.get(), "config.chatsphere.notification_flash"),
                btn -> {
                    ModClientConfig.CONFIG.notificationFlash.set(!ModClientConfig.CONFIG.notificationFlash.get());
                    btn.setMessage(toggleLabel(ModClientConfig.CONFIG.notificationFlash.get(), "config.chatsphere.notification_flash"));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        y += 24;
        addRenderableWidget(StyledButton.styledBuilder(
                toggleLabel(ModClientConfig.CONFIG.notificationPopup.get(), "config.chatsphere.notification_popup"),
                btn -> {
                    ModClientConfig.CONFIG.notificationPopup.set(!ModClientConfig.CONFIG.notificationPopup.get());
                    btn.setMessage(toggleLabel(ModClientConfig.CONFIG.notificationPopup.get(), "config.chatsphere.notification_popup"));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        y += 34;
        addRenderableWidget(StyledButton.styledBuilder(
                toggleLabel(ModClientConfig.CONFIG.showRightSidebar.get(), "config.chatsphere.show_right_sidebar"),
                btn -> {
                    ModClientConfig.CONFIG.showRightSidebar.set(!ModClientConfig.CONFIG.showRightSidebar.get());
                    btn.setMessage(toggleLabel(ModClientConfig.CONFIG.showRightSidebar.get(), "config.chatsphere.show_right_sidebar"));
                }
        ).bounds(centerX - 100, y, 200, 20).build());

        y += 34;
        addRenderableWidget(StyledButton.styledBuilder(
                CommonComponents.GUI_DONE,
                btn -> this.minecraft.setScreen(null)
        ).bounds(centerX - 50, y, 100, 20).style(StyledButton.Style.CONFIRM).build());
    }

    private Component toggleLabel(boolean value, String configKey) {
        MutableComponent result = Component.translatable(configKey).append(": ");
        if (value) {
            result.append(Component.translatable("screen.chatsphere.config.enabled").withStyle(ChatFormatting.GREEN));
        } else {
            result.append(Component.translatable("screen.chatsphere.config.disabled").withStyle(ChatFormatting.RED));
        }
        return result;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        String titleStr = this.title.getString();
        Component hintStr = Component.translatable("screen.chatsphere.config.hint");
        guiGraphics.drawString(this.font, titleStr, (this.width - this.font.width(titleStr)) / 2, 15, TITLE_COLOR, true);
        guiGraphics.drawString(this.font, hintStr, (this.width - this.font.width(hintStr)) / 2, this.height - 20, 0x888888, true);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
