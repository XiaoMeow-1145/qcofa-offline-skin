package cn.qcofa.offlineskin.client;

import cn.qcofa.offlineskin.QCOFAOfflineSkin;
import cn.qcofa.offlineskin.skin.LocalSkinFile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 皮肤更换界面。提供：
 * <ul>
 *   <li>皮肤文件路径输入框（指向一个 PNG 皮肤文件）</li>
 *   <li>模型类型切换（经典宽臂 / 苗条细臂）</li>
 *   <li>应用 / 清除 / 打开皮肤文件夹 按钮</li>
 *   <li>当前皮肤预览（直接绘制 PNG 纹理图）</li>
 * </ul>
 * 应用后会将皮肤写入 {@code config/qcofa_offline_skin/skin.png}，
 * 并通过 {@link QCOFAOfflineSkinClient#uploadLocalSkin()} 上报服务器。
 */
public class SkinScreen extends Screen {
    private static final int PREVIEW_SIZE = 160;

    private final Screen parent;
    private TextFieldWidget pathField;
    private ButtonWidget modelButton;
    private boolean slim = false;

    // 预览纹理
    private Identifier previewTextureId;
    private NativeImageBackedTexture previewTexture;
    private int previewImgW = 64;
    private int previewImgH = 64;

    private Text statusMessage = Text.empty();

    public SkinScreen(Screen parent) {
        super(Text.translatable("qcofa_offline_skin.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Path runDir = client.runDirectory.toPath();
        this.slim = "slim".equalsIgnoreCase(LocalSkinFile.readModel(runDir));

        // 路径输入框
        int cx = this.width / 2;
        int fieldW = 280;
        pathField = new TextFieldWidget(client.textRenderer,
                cx - fieldW / 2, 40, fieldW, 20,
                Text.translatable("qcofa_offline_skin.field.path"));
        pathField.setMaxLength(1024);
        // 默认填入当前皮肤路径
        String current = LocalSkinFile.skinPngPath(runDir).toString();
        pathField.setText(current);
        pathField.setPlaceholder(Text.translatable("qcofa_offline_skin.field.path.hint"));
        addSelectableChild(pathField);

        // 模型切换按钮
        modelButton = ButtonWidget.builder(modelButtonText(), b -> {
            slim = !slim;
            b.setMessage(modelButtonText());
        }).dimensions(cx - fieldW / 2, 66, 120, 20).build();
        addDrawableChild(modelButton);

        // 打开皮肤文件夹
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("qcofa_offline_skin.button.open_folder"),
                b -> {
                    try {
                        Path dir = LocalSkinFile.configDir(runDir);
                        dir.toFile().mkdirs();
                        net.minecraft.Util.getOperatingSystem().open(dir.toFile());
                    } catch (Exception e) {
                        statusMessage = Text.translatable("qcofa_offline_skin.status.open_folder_fail")
                                .formatted(Formatting.RED);
                    }
                }
        ).dimensions(cx - fieldW / 2 + 130, 66, 150, 20).build());

        // 应用
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("qcofa_offline_skin.button.apply"),
                b -> applySkin())
                .dimensions(cx - fieldW / 2, 92, 130, 20).build());

        // 清除
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("qcofa_offline_skin.button.clear"),
                b -> clearSkin())
                .dimensions(cx - fieldW / 2 + 140, 92, 140, 20).build());

        // 完成
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                b -> close())
                .dimensions(cx - 100, this.height - 28, 200, 20).build());

        loadPreview();
    }

    private Text modelButtonText() {
        return Text.translatable("qcofa_offline_skin.button.model")
                .append(": ")
                .append(slim
                        ? Text.translatable("qcofa_offline_skin.model.slim")
                        : Text.translatable("qcofa_offline_skin.model.default"));
    }

    /** 应用皮肤：读取输入路径的 PNG，写入本地配置，并上传服务器 */
    private void applySkin() {
        Path runDir = client.runDirectory.toPath();
        String input = pathField.getText().trim();
        if (input.isEmpty()) {
            statusMessage = Text.translatable("qcofa_offline_skin.status.empty_path")
                    .formatted(Formatting.RED);
            return;
        }
        try {
            Path src = Paths.get(input);
            byte[] data = java.nio.file.Files.readAllBytes(src);
            if (data.length == 0 || data.length > QCOFAOfflineSkin.MAX_SKIN_BYTES) {
                statusMessage = Text.translatable("qcofa_offline_skin.status.too_large")
                        .formatted(Formatting.RED);
                return;
            }
            // 写入本地配置
            LocalSkinFile.writeSkinPng(runDir, data);
            LocalSkinFile.writeModel(runDir, slim ? "slim" : "default");

            // 上传服务器
            QCOFAOfflineSkinClient.uploadLocalSkin();

            // 重新加载预览
            loadPreview();

            statusMessage = Text.translatable("qcofa_offline_skin.status.applied")
                    .formatted(Formatting.GREEN);
        } catch (Exception e) {
            statusMessage = Text.translatable("qcofa_offline_skin.status.read_fail")
                    .append(": " + e.getMessage()).formatted(Formatting.RED);
        }
    }

    /** 清除皮肤：删除本地配置并通知服务器移除 */
    private void clearSkin() {
        Path runDir = client.runDirectory.toPath();
        try {
            java.nio.file.Files.deleteIfExists(LocalSkinFile.skinPngPath(runDir));
        } catch (Exception ignored) {}
        releasePreview();

        // 通知服务器移除：发送 modelType + 长度 0
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                .canSend(QCOFAOfflineSkin.SKIN_UPLOAD_CHANNEL)) {
            net.minecraft.network.PacketByteBuf buf =
                    net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            buf.writeString(slim ? "slim" : "default", 16);
            buf.writeVarInt(0);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    QCOFAOfflineSkin.SKIN_UPLOAD_CHANNEL, buf);
        }

        statusMessage = Text.translatable("qcofa_offline_skin.status.cleared")
                .formatted(Formatting.YELLOW);
    }

    /** 加载当前本地皮肤到预览纹理 */
    private void loadPreview() {
        releasePreview();
        byte[] data = LocalSkinFile.readSkinPng(client.runDirectory.toPath());
        if (data == null) return;
        try {
            NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(data));
            previewImgW = img.getWidth();
            previewImgH = img.getHeight();
            previewTexture = new NativeImageBackedTexture(img);
            previewTextureId = new Identifier(QCOFAOfflineSkin.MOD_ID, "gui/preview_" + System.currentTimeMillis());
            client.getTextureManager().registerTexture(previewTextureId, previewTexture);
        } catch (Exception e) {
            QCOFAOfflineSkin.LOGGER.warn("预览皮肤加载失败: {}", e.getMessage());
        }
    }

    private void releasePreview() {
        if (previewTexture != null) {
            try { previewTexture.close(); } catch (Exception ignored) {}
            if (previewTextureId != null) {
                client.getTextureManager().destroyTexture(previewTextureId);
            }
            previewTexture = null;
            previewTextureId = null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // 标题
        context.drawCenteredTextWithShadow(client.textRenderer, this.title,
                this.width / 2, 14, 0xFFFFFF);

        // 路径输入框
        pathField.render(context, mouseX, mouseY, delta);

        // 状态消息
        context.drawCenteredTextWithShadow(client.textRenderer, statusMessage,
                this.width / 2, 120, 0xFFFFFF);

        // 预览
        int px = this.width / 2 - PREVIEW_SIZE / 2;
        int py = 140;
        context.fill(px - 2, py - 2, px + PREVIEW_SIZE + 2, py + PREVIEW_SIZE + 2, 0x40404040);
        if (previewTextureId != null) {
            // 将整张皮肤纹理图缩放绘制到预览框
            context.drawTexture(previewTextureId, px, py,
                    PREVIEW_SIZE, PREVIEW_SIZE,
                    0, 0, previewImgW, previewImgH,
                    previewImgW, previewImgH);
        } else {
            context.drawCenteredTextWithShadow(client.textRenderer,
                    Text.translatable("qcofa_offline_skin.preview.empty").formatted(Formatting.GRAY),
                    this.width / 2, py + PREVIEW_SIZE / 2 - 4, 0xFFFFFF);
        }

        // 提示文字
        context.drawCenteredTextWithShadow(client.textRenderer,
                Text.translatable("qcofa_offline_skin.hint").formatted(Formatting.DARK_GRAY),
                this.width / 2, this.height - 44, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        releasePreview();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
