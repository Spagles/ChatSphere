package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.compat.ncr.NCRCompat;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload;
import cn.sarskin.ChatSphere.network.ServerboundPermissionCheckPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static cn.sarskin.ChatSphere.config.ModClientConfig.CONFIG_SPEC;

public class ConfigScreen extends Screen {
    private final Screen lastScreen;
    private String pendingOpMsg;

    private static final int ROW_H = 28;
    private static final int TAB_Y = 38;
    private static final int CONTENT_Y = 68;
    private static final int TAB_PAD = 6;

    private int tabX, optLabelX, inputX, btnW, tabW;
    private int selectedCat;
    private int scrollOffset;
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    private interface WidgetFactory {
        AbstractWidget create(int y);
    }

    private record Opt(String key, WidgetFactory factory, java.util.function.Supplier<String> previewColor) {
        Opt(String key, WidgetFactory factory) { this(key, factory, null); }
    }
    private record Cat(String key, List<Opt> opts) {}

    private List<Cat> cats;

    public ConfigScreen() {
        super(Component.translatable("screen.chatsphere.config.title"));
        this.lastScreen = null;
    }

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("screen.chatsphere.config.title"));
        this.lastScreen = lastScreen;
    }

    private void buildCats() {
        if (cats != null) return;
        cats = new ArrayList<>();

        List<Opt> ui = new ArrayList<>();
        ui.add(new Opt("config.chatsphere.show_timestamp", y -> mkBool(y, ModClientConfig.CONFIG.showTimestamp)));
        ui.add(new Opt("config.chatsphere.show_sender_name", y -> mkBool(y, ModClientConfig.CONFIG.showSenderName)));
        ui.add(new Opt("config.chatsphere.show_avatar", y -> mkBool(y, ModClientConfig.CONFIG.showAvatar)));
        ui.add(new Opt("config.chatsphere.theme", y -> mkBool(y, ModClientConfig.CONFIG.themeDark)));
        ui.add(new Opt("config.chatsphere.strong_hint", y -> mkServerBool(y, "showStrongHint", ModServerConfig.CONFIG.showStrongHint)));
        cats.add(new Cat("config.chatsphere.ui", ui));

        List<Opt> behavior = new ArrayList<>();
        behavior.add(new Opt("config.chatsphere.anti_spam", y -> mkServerBool(y, "antiSpam", ModServerConfig.CONFIG.antiSpam)));
        behavior.add(new Opt("config.chatsphere.preserve_input", y -> mkBool(y, ModClientConfig.CONFIG.preserveInput)));
        behavior.add(new Opt("config.chatsphere.max_chat_history",
            y -> mkIntBox(y, safeGetStr(ModServerConfig.CONFIG.maxChatHistory, "50"), 50, 1000, 4, v -> sendConfigUpdate("maxChatHistory", String.valueOf(v))), null));
        behavior.add(new Opt("config.chatsphere.max_command_messages",
            y -> mkIntBox(y, safeGetStr(ModServerConfig.CONFIG.maxCommandMessages, "500"), 50, 2000, 4, v -> sendConfigUpdate("maxCommandMessages", String.valueOf(v))), null));
        behavior.add(new Opt("config.chatsphere.scroll_history_limit",
            y -> mkIntBox(y, String.valueOf(ModClientConfig.CONFIG.scrollHistoryLimit.get()), 50, 500, 3, v -> { ModClientConfig.CONFIG.scrollHistoryLimit.set(v); CONFIG_SPEC.save(); }), null));
        behavior.add(new Opt("config.chatsphere.command_history_limit",
            y -> mkIntBox(y, String.valueOf(ModClientConfig.CONFIG.commandHistoryLimit.get()), 10, 500, 3, v -> { ModClientConfig.CONFIG.commandHistoryLimit.set(v); CONFIG_SPEC.save(); }), null));
        cats.add(new Cat("config.chatsphere.behavior", behavior));

        List<Opt> channels = new ArrayList<>();
        channels.add(new Opt("config.chatsphere.enable_channels", y -> mkServerBool(y, "enableChannels", ModServerConfig.CONFIG.enableChannels)));
        cats.add(new Cat("config.chatsphere.channels", channels));

        List<Opt> sound = new ArrayList<>();
        sound.add(new Opt("config.chatsphere.notification_sound", y -> mkBool(y, ModClientConfig.CONFIG.notificationSound)));
        sound.add(new Opt("config.chatsphere.sound_mention", y -> mkBool(y, ModClientConfig.CONFIG.soundMention)));
        sound.add(new Opt("config.chatsphere.sound_whisper", y -> mkBool(y, ModClientConfig.CONFIG.soundWhisper)));
        sound.add(new Opt("config.chatsphere.sound_system", y -> mkBool(y, ModClientConfig.CONFIG.soundSystem)));
        sound.add(new Opt("config.chatsphere.sound_public", y -> mkBool(y, ModClientConfig.CONFIG.soundPublic)));
        cats.add(new Cat("config.chatsphere.sound_settings", sound));

        List<Opt> bubble = new ArrayList<>();
        bubble.add(new Opt("config.chatsphere.bubble_color_own",
            y -> mkHexBox(y, ModClientConfig.CONFIG.bubbleColorOwn.get(), s -> { ModClientConfig.CONFIG.bubbleColorOwn.set(s); CONFIG_SPEC.save(); }),
            ModClientConfig.CONFIG.bubbleColorOwn::get));
        bubble.add(new Opt("config.chatsphere.bubble_color_other",
            y -> mkHexBox(y, ModClientConfig.CONFIG.bubbleColorOther.get(), s -> { ModClientConfig.CONFIG.bubbleColorOther.set(s); CONFIG_SPEC.save(); }),
            ModClientConfig.CONFIG.bubbleColorOther::get));
        bubble.add(new Opt("config.chatsphere.bubble_corner_radius",
            y -> mkIntBox(y, String.valueOf(ModClientConfig.CONFIG.bubbleCornerRadius.get()), 0, 8, 1, v -> { ModClientConfig.CONFIG.bubbleCornerRadius.set(v); CONFIG_SPEC.save(); }), null));
        cats.add(new Cat("config.chatsphere.bubble", bubble));

        List<Opt> skin = new ArrayList<>();
        skin.add(new Opt("config.chatsphere.custom_skin_api_url",
            y -> mkStrBox(y, ModClientConfig.CONFIG.customSkinApiUrl.get(), s -> { ModClientConfig.CONFIG.customSkinApiUrl.set(s); CONFIG_SPEC.save(); }), null));
        skin.add(new Opt("config.chatsphere.avatar_cache_enabled", y -> mkBool(y, ModClientConfig.CONFIG.avatarCacheEnabled)));
        skin.add(new Opt("config.chatsphere.refresh_skin_cache", y ->
            Button.builder(Component.translatable("config.chatsphere.refresh_skin_cache"), btn -> {
                    btn.active = false;
                    PlayerSkinCache.refreshCache();
                })
                .bounds(inputX, y, btnW, 20)
                .tooltip(Tooltip.create(Component.translatable("config.chatsphere.refresh_skin_cache.tip")))
                .build(), null));
        cats.add(new Cat("config.chatsphere.skin", skin));

        List<Opt> network = new ArrayList<>();
        network.add(new Opt("config.chatsphere.allow_vanilla_connection", y -> mkBool(y, ModClientConfig.CONFIG.allowVanillaConnection)));
        cats.add(new Cat("config.chatsphere.network", network));

        if (NCRCompat.isNCRLoaded()) {
            List<Opt> ncrops = new ArrayList<>();
            ncrops.add(new Opt("config.chatsphere.ncr_compat", y -> mkBool(y, ModClientConfig.CONFIG.ncrCompat)));
            ncrops.add(new Opt("config.chatsphere.ncr_safety", y -> {
                Component label = NCRCompat.getSafetyStatusComponent();
                return Button.builder(label, btn -> {})
                        .bounds(inputX, y, btnW, 20).build();
            }));
            ncrops.add(new Opt("config.chatsphere.ncr_prevents_reports", y -> mkServerBool(y, "preventsChatReports", ModServerConfig.CONFIG.preventsChatReports)));
            cats.add(new Cat("config.chatsphere.ncr", ncrops));
        }
    }

    @Override
    protected void init() {
        buildCats();

        int totalTabW = 0;
        for (Cat c : cats) totalTabW += font.width(Component.translatable(c.key())) + TAB_PAD * 2 + 4;
        tabX = (width - totalTabW) / 2;
        tabW = totalTabW / cats.size();

        optLabelX = 30;
        btnW = Math.min(80, width - optLabelX - 80);
        inputX = width - btnW - 40;

        scrollWidgets.clear();
        scrollOffset = Mth.clamp(scrollOffset, 0, calcMaxScroll());

        int y = CONTENT_Y - scrollOffset;
        for (Opt opt : cats.get(selectedCat).opts()) {
            scrollWidgets.add(addRenderableWidget(opt.factory().create(y)));
            y += ROW_H;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
            .bounds(width / 2 - 100, height - 32, 200, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.chatsphere.config.tip_done"))).build());

        addRenderableWidget(Button.builder(
            Component.translatable("screen.chatsphere.config.server_config"),
            btn -> tryOpenServerConfig()
        ).bounds(width / 2 + 106, height - 32, 100, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.chatsphere.config.tip_server_config"))).build());

        try { minecraft.gameRenderer.loadEffect(ResourceLocation.fromNamespaceAndPath("minecraft", "shaders/post/blur.json")); } catch (Exception ignored) {}
    }

    private void switchCategory(int idx) {
        if (idx == selectedCat) return;
        selectedCat = idx;
        scrollOffset = 0;
        setFocused(null);
        clearWidgets();
        init();
    }

    private EditBox mkHexBox(int y, String initial, Consumer<String> onChange) {
        EditBox box = new EditBox(font, inputX, y, btnW, 20, Component.literal(""));
        box.setValue(initial);
        box.setMaxLength(7);
        box.setResponder(s -> {
            if (!s.matches("#?[0-9a-fA-F]{0,6}")) return;
            if (s.length() == 6 && !s.startsWith("#")) {
                box.setValue("#" + s);
                onChange.accept("#" + s);
            } else if (s.length() == 7) {
                onChange.accept(s);
            }
        });
        return box;
    }

    private EditBox mkIntBox(int y, String initial, int min, int max, int maxLen, Consumer<Integer> onChange) {
        EditBox box = new EditBox(font, inputX, y, btnW, 20, Component.literal(""));
        box.setValue(initial);
        box.setMaxLength(maxLen);
        box.setResponder(s -> {
            if (!s.matches("\\d*")) return;
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) onChange.accept(v);
            } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    private EditBox mkStrBox(int y, String initial, Consumer<String> onChange) {
        EditBox box = new EditBox(font, inputX, y, btnW, 20, Component.literal(""));
        box.setMaxLength(512);
        box.setValue(initial);
        box.setResponder(onChange::accept);
        return box;
    }

    private void drawColorPreview(GuiGraphics g, int y, String hex) {
        int color = ModClientConfig.parseHexColor(hex, 0xFF888888);
        int px = inputX - 22;
        g.fill(px, y + 4, px + 12, y + 16, 0xFF3A3A4A);
        g.fill(px + 1, y + 5, px + 11, y + 15, color);
    }

    private Button mkBool(int y, ModConfigSpec.BooleanValue cfg) {
        boolean v = cfg.get();
        return Button.builder(
            v ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF,
            btn -> {
                boolean nv = !cfg.get();
                cfg.set(nv);
                btn.setMessage(nv ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
                CONFIG_SPEC.save();
            }
        ).bounds(inputX, y, btnW, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.chatsphere.config.tip_toggle"))).build();
    }

    private void sendConfigUpdate(String key, String value) {
        Minecraft mc = minecraft;
        if (mc == null || mc.getConnection() == null) return;
        mc.getConnection().getConnection().send(
            new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundConfigUpdatePayload(key, value)));
    }

    private Button mkServerBool(int y, String fieldName, ModConfigSpec.BooleanValue cfg) {
        boolean v = safeGetBool(cfg);
        return Button.builder(
            v ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF,
            btn -> {
                boolean nv = !safeGetBool(cfg);
                cfg.set(nv);
                btn.setMessage(nv ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
                sendConfigUpdate(fieldName, String.valueOf(nv));
            }
        ).bounds(inputX, y, btnW, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.chatsphere.config.tip_toggle"))).build();
    }

    private static boolean safeGetBool(ModConfigSpec.BooleanValue cfg) {
        try {
            return cfg.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static String safeGetStr(ModConfigSpec.ConfigValue<?> cfg, String def) {
        try {
            Object val = cfg.get();
            return val != null ? val.toString() : def;
        } catch (IllegalStateException e) {
            return def;
        }
    }

    private void tryOpenServerConfig() {
        Minecraft mc = minecraft;
        if (mc == null) return;
        if (mc.isSingleplayer()) {
            mc.setScreen(new ServerConfigScreen(this));
            return;
        }
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().send(
                new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                    new ServerboundPermissionCheckPayload("SERVER_CONFIG")));
            pendingOpMsg = "chatsphere.server_config.pending_op";
        }
    }

    public void onPermissionResponse(String scope, boolean allowed) {
        pendingOpMsg = null;
        if (minecraft == null) return;
        if (!"SERVER_CONFIG".equals(scope)) return;
        if (allowed) {
            minecraft.setScreen(new ServerConfigScreen(this));
        } else {
            minecraft.player.displayClientMessage(
                Component.translatable("chatsphere.server_config.no_op"), false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, 0xFFFFFF, false);

        int cx = tabX;
        for (int i = 0; i < cats.size(); i++) {
            Component label = Component.translatable(cats.get(i).key());
            int w = font.width(label) + TAB_PAD * 2;
            boolean sel = i == selectedCat;
            boolean hover = mouseX >= cx && mouseX <= cx + w && mouseY >= TAB_Y && mouseY <= TAB_Y + 22;
            if (sel)
                g.fill(cx, TAB_Y + 20, cx + w, TAB_Y + 22, 0xFF8888FF);
            else if (hover)
                g.fill(cx, TAB_Y, cx + w, TAB_Y + 22, 0x225A4A7E);
            g.drawString(font, label, cx + TAB_PAD, TAB_Y + 7,
                sel ? 0xFF8888FF : 0xFFFFFFFF, false);
            cx += w + 6;
        }

        g.fill(10, CONTENT_Y - 6, width - 10, CONTENT_Y - 5, 0x225A4A7E);

        if (pendingOpMsg != null) {
            Component msg = Component.translatable(pendingOpMsg);
            g.drawString(font, msg, width / 2 - font.width(msg) / 2, height / 2, 0xFF888888, false);
        }

        int y = CONTENT_Y - scrollOffset;
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (y > -ROW_H && y < height) {
                g.drawString(font, Component.translatable(opt.key()), optLabelX, y + 6, 0xFFFFFFFF, false);
                if (opt.previewColor() != null)
                    drawColorPreview(g, y, opt.previewColor().get());
            }
            y += ROW_H;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = tabX;
            for (int i = 0; i < cats.size(); i++) {
                int w = font.width(Component.translatable(cats.get(i).key())) + TAB_PAD * 2;
                if (mouseX >= cx && mouseX <= cx + w && mouseY >= TAB_Y && mouseY <= TAB_Y + 22) {
                    switchCategory(i);
                    return true;
                }
                cx += w + 6;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        CONFIG_SPEC.save();
        if (minecraft != null) minecraft.setScreen(lastScreen);
    }

    @Override
    public void removed() {
        try { minecraft.gameRenderer.loadEffect(null); } catch (Exception ignored) {}
    }

    private int calcMaxScroll() {
        int total = cats.get(selectedCat).opts().size() * ROW_H;
        return Math.max(0, CONTENT_Y + total - (height - 42));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int y = CONTENT_Y - scrollOffset;
        List<Opt> opts = cats.get(selectedCat).opts();
        for (int i = 0; i < opts.size() && i < scrollWidgets.size(); i++) {
            scrollWidgets.get(i).setY(y);
            y += ROW_H;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
