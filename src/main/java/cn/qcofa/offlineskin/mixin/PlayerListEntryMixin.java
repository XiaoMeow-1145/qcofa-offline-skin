package cn.qcofa.offlineskin.mixin;

import cn.qcofa.offlineskin.client.ClientSkinRegistry;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 拦截 {@link PlayerListEntry#getSkinTexture()} 与 {@link PlayerListEntry#getModel()}，
 * 使玩家列表（Tab 列表 / 社交面板）中的头像与模型类型也使用离线皮肤。
 */
@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {

    @Inject(method = "getSkinTexture", at = @At("RETURN"), cancellable = true)
    private void qcofa$overrideSkinTexture(CallbackInfoReturnable<Identifier> cir) {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        UUID id = self.getProfile() != null ? self.getProfile().getId() : null;
        if (id == null) return;
        ClientSkinRegistry.Entry entry = ClientSkinRegistry.getSkin(id);
        if (entry != null) {
            cir.setReturnValue(entry.textureId);
        }
    }

    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
    private void qcofa$overrideModel(CallbackInfoReturnable<String> cir) {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        UUID id = self.getProfile() != null ? self.getProfile().getId() : null;
        if (id == null) return;
        ClientSkinRegistry.Entry entry = ClientSkinRegistry.getSkin(id);
        if (entry != null) {
            cir.setReturnValue(entry.slim ? "slim" : "default");
        }
    }
}
