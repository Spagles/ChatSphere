package cn.sarskin.ChatSphere.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ModClientConfig {
    public static final ModClientConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    public final ModConfigSpec.BooleanValue showTimestamp;
    public final ModConfigSpec.BooleanValue showSenderName;
    public final ModConfigSpec.BooleanValue showAvatar;
    public final ModConfigSpec.BooleanValue enableChannels;
    public final ModConfigSpec.BooleanValue notificationSound;
    public final ModConfigSpec.BooleanValue notificationFlash;
    public final ModConfigSpec.BooleanValue notificationPopup;

    public final ModConfigSpec.BooleanValue showRightSidebar;

    static {
        Pair<ModClientConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ModClientConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    private ModClientConfig(ModConfigSpec.Builder builder) {
        builder.push("ui");
        showTimestamp = builder
                .comment("Show timestamp in chat bubbles")
                .define("showTimestamp", true);
        showSenderName = builder
                .comment("Show sender name in chat bubbles")
                .define("showSenderName", true);
        showAvatar = builder
                .comment("Show avatar in chat bubbles")
                .define("showAvatar", true);
        builder.pop();

        builder.push("channels");
        enableChannels = builder
                .comment("Enable chat channels")
                .define("enableChannels", true);
        builder.pop();

        builder.push("notifications");
        notificationSound = builder
                .comment("Play sound on new message")
                .define("notificationSound", true);
        notificationFlash = builder
                .comment("Flash icon on new message")
                .define("notificationFlash", true);
        notificationPopup = builder
                .comment("Show popup on new message")
                .define("notificationPopup", true);
        builder.pop();

        builder.push("sidebar");
        showRightSidebar = builder
                .comment("Show right sidebar with online players in channels")
                .define("showRightSidebar", true);
        builder.pop();
    }
}
