package cn.sarskin.ChatSphere.server;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class ModServerEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        ModServerChannels msc = ModServerChannels.getInstance(sp.server);
        msc.sendToPlayer(sp);
        msc.sendMessagesToPlayer(sp);
    }
}
