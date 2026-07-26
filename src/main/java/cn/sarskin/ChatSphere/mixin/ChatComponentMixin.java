package cn.sarskin.ChatSphere.mixin;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        history.addCommandMessage(
                message,
                mc.player.getUUID(),
                Component.literal(""),
                false);
        ci.cancel();
    }
}
