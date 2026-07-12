package cn.qcofa.offlineskin.mixin;

import cn.qcofa.offlineskin.client.ClientSkinRegistry;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 1.21.3+ 的皮肤 API 已合并为 {@link PlayerListEntry#getSkinTextures()}，
 * 返回不可变的 {@link SkinTextures} 记录。
 *
 * 使玩家列表（Tab 列表 / 社交面板）中的头像与模型类型也使用离线皮肤。
 */
@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void qcofa$overrideSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        PlayerListEntry self = (PlayerListEntry) (Object) this;
        UUID id = self.getProfile() != null ? self.getProfile().getId() : null;
        if (id == null) return;
        ClientSkinRegistry.Entry entry = ClientSkinRegistry.getSkin(id);
        if (entry != null) {
            SkinTextures original = cir.getReturnValue();
            SkinTextures.Model model = entry.slim ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE;
            cir.setReturnValue(new SkinTextures(entry.textureId, original.textureUrl(),
                    original.capeTexture(), original.elytraTexture(), model, original.secure()));
        }
    }
}
