package cn.qcofa.offlineskin.client;

import cn.qcofa.offlineskin.QCOFAOfflineSkin;
import cn.qcofa.offlineskin.skin.SkinData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端皮肤注册表。保存所有从服务器收到的离线皮肤，
 * 并将 PNG 字节注册为 Minecraft 可用的纹理 Identifier。
 *
 * 渲染时通过 {@link #getSkin(UUID)} 查询，若返回非 null，
 * 则用该 Identifier 覆盖原版皮肤纹理。
 */
public final class ClientSkinRegistry {
    /** 单条皮肤记录：纹理标识 + 模型类型 + 底层纹理（用于释放） */
    public static final class Entry {
        public final Identifier textureId;
        public final boolean slim;
        public final String hash;
        private NativeImageBackedTexture backing;

        Entry(Identifier id, boolean slim, String hash, NativeImageBackedTexture backing) {
            this.textureId = id;
            this.slim = slim;
            this.hash = hash;
            this.backing = backing;
        }

        public NativeImageBackedTexture backing() { return backing; }
    }

    private static final Map<UUID, Entry> SKINS = new ConcurrentHashMap<>();

    private ClientSkinRegistry() {}

    /** 注册或更新某玩家的离线皮肤。data 为空则移除。 */
    public static void put(UUID uuid, SkinData data) {
        // 先移除旧的
        Entry old = SKINS.remove(uuid);
        if (old != null) release(old);

        if (data == null || !data.isPresent()) return;

        // 同 hash 已存在则可复用纹理，避免重复上传
        for (Entry e : SKINS.values()) {
            if (e.hash.equals(data.hash())) {
                SKINS.put(uuid, e);
                return;
            }
        }

        NativeImage image;
        try {
            image = NativeImage.read(new java.io.ByteArrayInputStream(data.data()));
        } catch (Exception ex) {
            QCOFAOfflineSkin.LOGGER.warn("无法解析皮肤 PNG: {}", ex.getMessage());
            return;
        }

        NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
        Identifier id = new Identifier(QCOFAOfflineSkin.MOD_ID, "dynamic/" + uuid.toString().replace("-", ""));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        SKINS.put(uuid, new Entry(id, data.isSlim(), data.hash(), tex));
    }

    /** 移除某玩家皮肤 */
    public static void remove(UUID uuid) {
        Entry e = SKINS.remove(uuid);
        if (e != null) release(e);
    }

    /** 查询某玩家皮肤，无则返回 null */
    public static Entry getSkin(UUID uuid) {
        return SKINS.get(uuid);
    }

    /** 清空全部（断开连接时） */
    public static void clear() {
        for (Entry e : SKINS.values()) release(e);
        SKINS.clear();
    }

    private static void release(Entry e) {
        try {
            if (e.backing != null) {
                e.backing.close();
                e.backing = null;
            }
            MinecraftClient.getInstance().getTextureManager().destroyTexture(e.textureId);
        } catch (Exception ignored) {}
    }
}
