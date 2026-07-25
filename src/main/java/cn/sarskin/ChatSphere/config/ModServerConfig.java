package cn.sarskin.ChatSphere.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ModServerConfig {
    public static final ModServerConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    public final ModConfigSpec.BooleanValue antiSpam;
    public final ModConfigSpec.BooleanValue enableChannels;
    public final ModConfigSpec.IntValue maxChatHistory;
    public final ModConfigSpec.BooleanValue showStrongHint;
    public final ModConfigSpec.BooleanValue syncDefaultChannel;
    public final ModConfigSpec.BooleanValue channelHistoryEnabled;
    public final ModConfigSpec.BooleanValue exploreEnabled;
    public final ModConfigSpec.IntValue exploreMinMembers;
    public final ModConfigSpec.IntValue backupIntervalMinutes;
    public final ModConfigSpec.IntValue backupKeepMax;
    public final ModConfigSpec.BooleanValue preventsChatReports;

    static {
        Pair<ModServerConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ModServerConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    private ModServerConfig(ModConfigSpec.Builder builder) {
        builder.push("chat");
        antiSpam = builder
                .comment("Collapse consecutive duplicate messages from the same player")
                .define("antiSpam", true);
        enableChannels = builder
                .comment("Enable chat channels")
                .define("enableChannels", true);
        maxChatHistory = builder
                .comment("Maximum number of chat messages to keep per conversation")
                .defineInRange("maxChatHistory", 200, 50, 1000);
        showStrongHint = builder
                .comment("Show strong hint above hotbar for mentions and system messages")
                .define("showStrongHint", true);
        backupIntervalMinutes = builder
                .comment("Minutes between automatic server data backups (0 to disable)")
                .defineInRange("backupIntervalMinutes", 30, 0, 1440);
        backupKeepMax = builder
                .comment("Maximum number of backup files to keep")
                .defineInRange("backupKeepMax", 20, 1, 100);
        builder.pop();
        builder.push("sync");
        syncDefaultChannel = builder
                .comment("Sync the default channel (#general) to all players on login")
                .define("syncDefaultChannel", true);
        channelHistoryEnabled = builder
                .comment("Enable broadcast of channel chat history on login (does not affect private messages)")
                .define("channelHistoryEnabled", true);
        builder.pop();
        builder.push("explore");
        exploreEnabled = builder
                .comment("Enable the Explore Public Servers feature")
                .define("exploreEnabled", true);
        exploreMinMembers = builder
                .comment("Minimum number of members required for a channel to appear in Explore")
                .defineInRange("exploreMinMembers", 0, 0, 100);
        builder.pop();
        builder.push("ncr");
        preventsChatReports = builder
                .comment("Mark this server as preventing chat reports (adds preventsChatReports to ping response)")
                .define("preventsChatReports", false);
        builder.pop();
    }
}
