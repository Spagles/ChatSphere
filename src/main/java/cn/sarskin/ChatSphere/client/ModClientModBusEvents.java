package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.hud.ChatHudOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public class ModClientModBusEvents {

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ChatHudOverlay.HUD_ID, ChatHudOverlay.INSTANCE);
    }
}
