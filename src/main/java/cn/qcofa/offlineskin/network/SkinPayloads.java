package cn.qcofa.offlineskin.network;

import cn.qcofa.offlineskin.QCOFAOfflineSkin;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 1.20.5+ 基于 {@link CustomPayload} 的网络载荷定义与注册。
 *
 * <ul>
 *   <li>{@link SkinUploadPayload} (C2S)：客户端上报自己的皮肤，data 长度为 0 表示清除</li>
 *   <li>{@link SkinBroadcastPayload} (S2C)：服务端广播某玩家的皮肤，data 长度为 0 表示移除</li>
 *   <li>{@link SkinRequestPayload} (S2C)：服务端请求客户端上传自己的皮肤（空载荷）</li>
 * </ul>
 *
 * 载荷类型注册在 {@link #registerTypes()} 中完成，由公共入口
 * {@link QCOFAOfflineSkin#onInitialize()} 调用，确保客户端与服务端均注册编解码器。
 */
public final class SkinPayloads {
    private SkinPayloads() {}

    /** 客户端 -> 服务端：上报自己的皮肤（data 长度为 0 表示清除） */
    public record SkinUploadPayload(String model, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<SkinUploadPayload> ID =
                new CustomPayload.Id<>(Identifier.of(QCOFAOfflineSkin.MOD_ID, "skin_upload"));
        public static final PacketCodec<PacketByteBuf, SkinUploadPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeString(value.model(), 16);
                    buf.writeVarInt(value.data().length);
                    buf.writeBytes(value.data());
                },
                buf -> {
                    String m = buf.readString(16);
                    int len = buf.readVarInt();
                    byte[] d = new byte[len];
                    buf.readBytes(d);
                    return new SkinUploadPayload(m, d);
                }
        );
        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** 服务端 -> 客户端：广播某玩家的皮肤（data 长度为 0 表示移除） */
    public record SkinBroadcastPayload(UUID uuid, String model, String hash, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<SkinBroadcastPayload> ID =
                new CustomPayload.Id<>(Identifier.of(QCOFAOfflineSkin.MOD_ID, "skin_broadcast"));
        public static final PacketCodec<PacketByteBuf, SkinBroadcastPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeUuid(value.uuid());
                    buf.writeString(value.model(), 16);
                    buf.writeString(value.hash(), 64);
                    buf.writeVarInt(value.data().length);
                    buf.writeBytes(value.data());
                },
                buf -> {
                    UUID uuid = buf.readUuid();
                    String m = buf.readString(16);
                    String h = buf.readString(64);
                    int len = buf.readVarInt();
                    byte[] d = new byte[len];
                    buf.readBytes(d);
                    return new SkinBroadcastPayload(uuid, m, h, d);
                }
        );
        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** 服务端 -> 客户端：请求客户端上传自己的皮肤（空载荷） */
    public record SkinRequestPayload() implements CustomPayload {
        public static final CustomPayload.Id<SkinRequestPayload> ID =
                new CustomPayload.Id<>(Identifier.of(QCOFAOfflineSkin.MOD_ID, "skin_request"));
        public static final PacketCodec<PacketByteBuf, SkinRequestPayload> CODEC = PacketCodec.of(
                (value, buf) -> {},
                buf -> new SkinRequestPayload()
        );
        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** 注册所有载荷类型的编解码器（客户端与服务端均需调用） */
    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(SkinUploadPayload.ID, SkinUploadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SkinBroadcastPayload.ID, SkinBroadcastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SkinRequestPayload.ID, SkinRequestPayload.CODEC);
    }
}
