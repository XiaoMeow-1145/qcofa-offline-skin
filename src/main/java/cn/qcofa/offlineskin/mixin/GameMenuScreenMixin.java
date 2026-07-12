package cn.qcofa.offlineskin.mixin;

import cn.qcofa.offlineskin.client.SkinScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在游戏内暂停菜单（GameMenuScreen）的左上角添加一个
 * "QCOFA 更换皮肤" 按钮，点击后打开 {@link SkinScreen}。
 *
 * 这是模组的核心入口：玩家通过此按钮在本地更换皮肤。
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void qcofa$addSkinButton(CallbackInfo ci) {
        // 左上角小按钮，避开原有居中按钮布局
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("qcofa_offline_skin.button.change_skin"),
                button -> MinecraftClient.getInstance().setScreen(new SkinScreen(this))
        ).dimensions(4, 4, 120, 20).build());
    }
}
