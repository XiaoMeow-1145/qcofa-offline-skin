package cn.qcofa.offlineskin.mixin;

import cn.qcofa.offlineskin.client.ClientSkinRegistry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 {@link AbstractClientPlayerEntity#getSkinTexture()} 与 {@link AbstractClientPlayerEntity#getModel()}，
 * 当该玩家在 {@link ClientSkinRegistry} 中存在离线皮肤时，返回模组注册的纹理与模型类型。
 *
 * 这是让"只有装了本模组的玩家才能看到更换后皮肤"的关键：
 * 未安装本模组的客户端不会执行此 mixin，因此仍显示原版（正版/离线默认）皮肤。
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTexture", at = @At("RETURN"), cancellable = true)
    private void qcofa$overrideSkinTexture(CallbackInfoReturnable<Identifier> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        ClientSkinRegistry.Entry entry = ClientSkinRegistry.getSkin(self.getUuid());
        if (entry != null) {
            cir.setReturnValue(entry.textureId);
        }
    }

    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true)
    private void qcofa$overrideModel(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        ClientSkinRegistry.Entry entry = ClientSkinRegistry.getSkin(self.getUuid());
        if (entry != null) {
            cir.setReturnValue(entry.slim ? "slim" : "default");
        }
    }
}
