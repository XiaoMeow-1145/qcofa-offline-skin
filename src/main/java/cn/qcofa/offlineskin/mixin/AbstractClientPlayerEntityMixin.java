package cn.qcofa.offlineskin.mixin;

import cn.qcofa.offlineskin.client.ClientSkinRegistry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.3+ 的皮肤 API 已合并为 {@link AbstractClientPlayerEntity#getSkinTextures()}，
 * 返回不可变的 {@link SkinTextures} 记录。
 *
 * 当该玩家在 {@link ClientSkinRegistry} 中存在离线皮肤时，
 * 用模组注册的纹理与模型类型构造一个新的 {@link SkinTextures} 替换原返回值。
 *
 * 这是让"只有装了本模组的玩家才能看到更换后皮肤"的关键：
 * 未安装本模组的客户端不会执行此 mixin，因此仍显示原版（正版/离线默认）皮肤。
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void qcofa$overrideSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        ClientSkinRegistry.Entry entry = ClientSkinRegistry.getSkin(self.getUuid());
        if (entry != null) {
            SkinTextures original = cir.getReturnValue();
            SkinTextures.Model model = entry.slim ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE;
            cir.setReturnValue(new SkinTextures(entry.textureId, original.textureUrl(),
                    original.capeTexture(), original.elytraTexture(), model, original.secure()));
        }
    }
}
