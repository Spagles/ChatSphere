package cn.sarskin.ChatSphere;

import cn.sarskin.ChatSphere.client.ModKeyMappings;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.network.ModNetworkSetup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.api.distmarker.Dist;

@Mod(ModMain.MODID)
public class ModMain {
    public static final String MODID = "chatsphere";
    public static final String DEFAULT_CHANNEL_ID = "#\u516C\u5171";

    public ModMain(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ModClientConfig.CONFIG_SPEC);
        modEventBus.register(ModNetworkSetup.class);
        if (FMLLoader.getDist() == Dist.CLIENT) {
            modEventBus.register(ModKeyMappings.class);
        }
    }
}
