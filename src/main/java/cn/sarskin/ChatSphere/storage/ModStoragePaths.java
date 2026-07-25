package cn.sarskin.ChatSphere.storage;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class ModStoragePaths {

    private static final String BASE = "ChatSphere";

    public static Path getBaseDir() {
        return FMLPaths.GAMEDIR.get().resolve(BASE);
    }

    public static Path getClientBaseDir() {
        return getBaseDir().resolve("client");
    }

    public static Path getSingleplayerDir(String worldName) {
        return getClientBaseDir().resolve("singleplayer").resolve(sanitize(worldName));
    }

    public static Path getMultiplayerDir(String serverIp) {
        return getClientBaseDir().resolve("multiplayer").resolve(sanitize(serverIp));
    }

    public static Path getServerDataDir() {
        return getBaseDir().resolve("server");
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
