package cn.sarskin.ChatSphere;

import cn.sarskin.ChatSphere.client.ModKeyMappings;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ModNetworkSetup;
import cn.sarskin.ChatSphere.server.ModServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ModMain.MODID)
public class ModMain {
    public static final String MODID = "chatsphere";
    public static final String DEFAULT_CHANNEL_ID = "#general";
    public static final Logger LOGGER = LoggerFactory.getLogger(ModMain.class);

    public ModMain(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ModClientConfig.CONFIG_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, ModServerConfig.CONFIG_SPEC);
        modEventBus.register(ModNetworkSetup.class);
        NeoForge.EVENT_BUS.register(ModServerEvents.class);
        if (FMLLoader.getDist() == Dist.CLIENT) {
            modEventBus.register(ModKeyMappings.class);
            // Load client-only setup reflectively to avoid RuntimeDistCleaner errors on server
            try {
                Class.forName("cn.sarskin.ChatSphere.client.ModClientSetup")
                    .getMethod("init", ModContainer.class)
                    .invoke(null, modContainer);
            } catch (Exception ignored) {}
        }

        // Load PlasmoVoice room addon if PV is installed
        try {
            if (ModList.get().isLoaded("plasmovoice")) {
                Class<?> pvsClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServer");
                Object loader = pvsClass.getMethod("getAddonsLoader").invoke(null);
                Object addon = Class.forName("cn.sarskin.ChatSphere.server.voice.PlasmoRoomAddon")
                        .getConstructor().newInstance();
                loader.getClass().getMethod("load", Object.class).invoke(loader, addon);
            }
        } catch (Exception ignored) {}
    }
}
