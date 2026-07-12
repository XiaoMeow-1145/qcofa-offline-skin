package cn.qcofa.offlineskin.network;

import cn.qcofa.offlineskin.QCOFAOfflineSkin;
import cn.qcofa.offlineskin.skin.ServerSkinStorage;
import cn.qcofa.offlineskin.skin.SkinData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端网络处理。负责：
 * <ul>
 *   <li>接收客户端上报的皮肤（skin_upload）</li>
 *   <li>玩家加入时请求其皮肤，并下发当前全量皮肤</li>
 *   <li>玩家变更皮肤时向其他模组玩家广播</li>
 *   <li>玩家退出时清除并广播移除</li>
 * </ul>
 *
 * 注意：只有注册了对应接收器的客户端（即装了本模组的玩家）才会收到广播，
 * 因此正版玩家 / 未装模组玩家看不到任何离线皮肤——这正是模组的设计目标。
 */
public final class NetworkHandler {
    private NetworkHandler() {}

    public static void registerServerReceivers() {
        // 1. 接收客户端上报的皮肤
        ServerPlayNetworking.registerGlobalReceiver(QCOFAOfflineSkin.SKIN_UPLOAD_CHANNEL,
                (server, player, handler, buf, responseSender) -> handleUpload(server, player, buf));

        // 2. 玩家加入：发送请求 + 全量下发
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> onPlayerJoin(player));
        });

        // 3. 玩家退出：清除并广播移除
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> onPlayerQuit(player));
        });
    }

    /** 处理客户端上传的皮肤数据。协议：readString(model) + readVarInt(len) + bytes */
    private static void handleUpload(net.minecraft.server.MinecraftServer server,
                                     ServerPlayerEntity player, PacketByteBuf buf) {
        try {
            String modelType = buf.readString(16);
            int len = buf.readVarInt();
            if (len == 0) {
                // 客户端请求清除自己的皮肤
                if (ServerSkinStorage.get(player.getUuid()).isPresent()) {
                    ServerSkinStorage.remove(player.getUuid());
                    broadcastRemoval(player);
                }
                return;
            }
            if (len < 0 || len > QCOFAOfflineSkin.MAX_SKIN_BYTES) {
                return;
            }
            byte[] data = new byte[len];
            buf.readBytes(data);
            String hash = sha1Hex(data);

            SkinData skin = new SkinData(modelType, hash, data);
            ServerSkinStorage.put(player.getUuid(), skin);

            // 广播给所有（除自己）装了模组的玩家
            broadcastSkin(player, skin, false);
        } catch (Exception e) {
            QCOFAOfflineSkin.LOGGER.warn("处理皮肤上传失败: {}", e.getMessage());
        }
    }

    /** 广播移除某玩家皮肤（空皮肤） */
    private static void broadcastRemoval(ServerPlayerEntity source) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(source.getUuid());
        buf.writeString("default", 16);
        buf.writeString("", 64);
        buf.writeVarInt(0);
        for (ServerPlayerEntity p : PlayerLookup.all(source.server)) {
            if (p.getUuid().equals(source.getUuid())) continue;
            if (ServerPlayNetworking.canSend(p, QCOFAOfflineSkin.SKIN_BROADCAST_CHANNEL)) {
                ServerPlayNetworking.send(p, QCOFAOfflineSkin.SKIN_BROADCAST_CHANNEL, buf);
            }
        }
    }

    /** 玩家加入：请求其皮肤，并把当前全量皮肤发给它 */
    private static void onPlayerJoin(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, QCOFAOfflineSkin.SKIN_REQUEST_CHANNEL)) {
            // 未装本模组的玩家，跳过
            return;
        }
        // 请求客户端上传自己的皮肤
        ServerPlayNetworking.send(player, QCOFAOfflineSkin.SKIN_REQUEST_CHANNEL, PacketByteBufs.empty());

        // 下发当前所有玩家的皮肤
        for (Map.Entry<UUID, SkinData> e : ServerSkinStorage.snapshot().entrySet()) {
            UUID uuid = e.getKey();
            if (uuid.equals(player.getUuid())) continue;
            SkinData skin = e.getValue();
            if (!skin.isPresent()) continue;
            sendSkinTo(player, uuid, skin);
        }
    }

    /** 玩家退出：清除皮肤并通知其他人 */
    private static void onPlayerQuit(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!ServerSkinStorage.get(uuid).isPresent()) return;
        ServerSkinStorage.remove(uuid);
        // 广播移除（空皮肤）
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(uuid);
        buf.writeString("default", 16);
        buf.writeString("", 64);
        buf.writeVarInt(0); // 0 字节 = 移除
        for (ServerPlayerEntity p : PlayerLookup.all(player.server)) {
            if (p.getUuid().equals(uuid)) continue;
            if (ServerPlayNetworking.canSend(p, QCOFAOfflineSkin.SKIN_BROADCAST_CHANNEL)) {
                ServerPlayNetworking.send(p, QCOFAOfflineSkin.SKIN_BROADCAST_CHANNEL, buf);
            }
        }
    }

    /** 广播某玩家皮肤给所有（可选含自己）模组玩家 */
    public static void broadcastSkin(ServerPlayerEntity source, SkinData skin, boolean includeSelf) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(source.getUuid());
        buf.writeString(skin.modelType(), 16);
        buf.writeString(skin.hash(), 64);
        buf.writeVarInt(skin.data().length);
        buf.writeBytes(skin.data());

        for (ServerPlayerEntity p : PlayerLookup.all(source.server)) {
            if (!includeSelf && p.getUuid().equals(source.getUuid())) continue;
            if (ServerPlayNetworking.canSend(p, QCOFAOfflineSkin.SKIN_BROADCAST_CHANNEL)) {
                ServerPlayNetworking.send(p, QCOFAOfflineSkin.SKIN_BROADCAST_CHANNEL, buf);
            }
        }
    }

    /** 发送单个玩家皮肤给指定目标玩家 */
    private static void sendSkinTo(ServerPlayerEntity target, UUID uuid, SkinData skin) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(uuid);
        buf.writeString(skin.modelType(), 16);
        buf.writeString(skin.hash(), 64);
        buf.writeVarInt(skin.data().length);
        buf.writeBytes(skin.data());
        ServerPlayNetworking.send(target, QCOFAOfflineSkin.SKIN_BROADCAST_CHANNEL, buf);
    }

    private static String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(data.hashCode());
        }
    }

    /** 通道标识（供客户端校验） */
    public static Identifier broadcastChannel() {
        return QCOFAOfflineSkin.SKIN_BROADCAST_CHANNEL;
    }
}
