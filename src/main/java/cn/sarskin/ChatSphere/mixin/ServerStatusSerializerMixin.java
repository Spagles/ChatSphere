package cn.sarskin.ChatSphere.mixin;

import cn.sarskin.ChatSphere.config.ModServerConfig;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.status.ServerStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class ServerStatusSerializerMixin {
    private static final Gson CS_GSON = new Gson();

    @Shadow
    private String cachedServerStatus;

    @Inject(method = "resetStatusCache", at = @At("TAIL"))
    private void onResetStatusCache(ServerStatus status, CallbackInfo ci) {
        if (ModServerConfig.CONFIG.preventsChatReports.get() && this.cachedServerStatus != null) {
            try {
                JsonObject json = CS_GSON.fromJson(this.cachedServerStatus, JsonObject.class);
                json.addProperty("preventsChatReports", true);
                this.cachedServerStatus = CS_GSON.toJson(json);
            } catch (Exception ignored) {
            }
        }
    }
}
