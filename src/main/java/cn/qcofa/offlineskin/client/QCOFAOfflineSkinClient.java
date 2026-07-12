package cn.qcofa.offlineskin.client;

import cn.qcofa.offlineskin.QCOFAOfflineSkin;
import cn.qcofa.offlineskin.skin.LocalSkinFile;
import cn.qcofa.offlineskin.skin.SkinData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * 客户端入口。注册网络接收器：
 * <ul>
 *   <li>skin_request：服务器请求上传自己的皮肤 → 读取本地 skin.png 上传</li>
 *   <li>skin_broadcast：服务器广播某玩家皮肤 → 注册到 {@link ClientSkinRegistry}</li>
 * </ul>
 * 并在断开连接时清空本地缓存。
 */
public class QCOFAOfflineSkinClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        QCOFAOfflineSkin.LOGGER.info("[QCOFA-OfflineSkin] 客户端初始化 by xiaomeow_cn");

        // 1. 服务器请求上传皮肤
        ClientPlayNetworking.registerGlobalReceiver(QCOFAOfflineSkin.SKIN_REQUEST_CHANNEL,
                (client, handler, buf, responseSender) -> {
                    client.execute(QCOFAOfflineSkinClient::uploadLocalSkin);
                });

        // 2. 服务器广播皮肤
        ClientPlayNetworking.registerGlobalReceiver(QCOFAOfflineSkin.SKIN_BROADCAST_CHANNEL,
                (client, handler, buf, responseSender) -> handleBroadcast(client, buf));

        // 3. 断开连接清空缓存
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(ClientSkinRegistry::clear);
        });
    }

    /** 读取本地皮肤 PNG 并上传给服务器，同时应用到本地玩家自身（让自己也能看到） */
    public static void uploadLocalSkin() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        String model = LocalSkinFile.readModel(mc.runDirectory.toPath());
        byte[] data = LocalSkinFile.readSkinPng(mc.runDirectory.toPath());

        if (data == null || data.length == 0) {
            // 无皮肤：清除本地自身记录
            ClientSkinRegistry.remove(mc.player.getUuid());
            // 若在服务器上，也通知移除
            if (ClientPlayNetworking.canSend(QCOFAOfflineSkin.SKIN_UPLOAD_CHANNEL)) {
                PacketByteBuf out = PacketByteBufs.create();
                out.writeString(model, 16);
                out.writeVarInt(0);
                ClientPlayNetworking.send(QCOFAOfflineSkin.SKIN_UPLOAD_CHANNEL, out);
            }
            return;
        }

        // 应用到本地自身
        SkinData skin = new SkinData(model, sha1Hex(data), data);
        ClientSkinRegistry.put(mc.player.getUuid(), skin);

        // 上传服务器
        if (ClientPlayNetworking.canSend(QCOFAOfflineSkin.SKIN_UPLOAD_CHANNEL)) {
            PacketByteBuf out = PacketByteBufs.create();
            out.writeString(model, 16);
            out.writeVarInt(data.length);
            out.writeBytes(data);
            ClientPlayNetworking.send(QCOFAOfflineSkin.SKIN_UPLOAD_CHANNEL, out);
        }
    }

    private static String sha1Hex(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(data.hashCode());
        }
    }

    /** 处理服务器广播的皮肤 */
    private static void handleBroadcast(MinecraftClient client, PacketByteBuf buf) {
        UUID uuid = buf.readUuid();
        String model = buf.readString(16);
        String hash = buf.readString(64);
        int len = buf.readVarInt();
        byte[] data = new byte[len];
        buf.readBytes(data);

        client.execute(() -> {
            SkinData skin = (len == 0) ? SkinData.EMPTY : new SkinData(model, hash, data);
            if (skin.isPresent()) {
                ClientSkinRegistry.put(uuid, skin);
            } else {
                ClientSkinRegistry.remove(uuid);
            }
        });
    }
}
