package cn.qcofa.offlineskin.skin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端皮肤存储。按玩家 UUID 记录其上报的离线皮肤，
 * 供新加入的模组玩家拉取，以及供服务器广播变更使用。
 *
 * 线程安全：使用 {@link ConcurrentHashMap}，因为网络回调与主线程均会访问。
 */
public final class ServerSkinStorage {
    private static final Map<UUID, SkinData> SKINS = new ConcurrentHashMap<>();

    private ServerSkinStorage() {}

    /** 记录/更新某玩家的皮肤 */
    public static void put(UUID uuid, SkinData data) {
        if (data == null || !data.isPresent()) {
            SKINS.remove(uuid);
        } else {
            SKINS.put(uuid, data);
        }
    }

    /** 移除某玩家的皮肤（玩家退出时调用） */
    public static void remove(UUID uuid) {
        SKINS.remove(uuid);
    }

    /** 读取某玩家的皮肤 */
    public static SkinData get(UUID uuid) {
        return SKINS.getOrDefault(uuid, SkinData.EMPTY);
    }

    /** 返回当前所有玩家的皮肤快照（用于向新加入玩家下发全量） */
    public static Map<UUID, SkinData> snapshot() {
        return Map.copyOf(SKINS);
    }
}
