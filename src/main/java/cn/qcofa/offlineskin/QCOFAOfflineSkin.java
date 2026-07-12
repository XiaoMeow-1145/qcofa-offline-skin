package cn.qcofa.offlineskin;

import cn.qcofa.offlineskin.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QCOFA Offline Skin 服务端主入口。
 *
 * 由 QCOFA 额外模组提供，允许在 QuestCraft / 离线客户端中本地更换皮肤。
 * 仅安装此模组的玩家之间可互相看到更换后的皮肤，正版玩家不会看到。
 *
 * @author xiaomeow_cn
 */
public class QCOFAOfflineSkin implements ModInitializer {
    public static final String MOD_ID = "qcofa_offline_skin";
    public static final Logger LOGGER = LoggerFactory.getLogger("QCOFA-OfflineSkin");

    /** 网络通道标识：客户端 -> 服务端 上报自己的皮肤信息 */
    public static final Identifier SKIN_UPLOAD_CHANNEL = new Identifier(MOD_ID, "skin_upload");
    /** 网络通道标识：服务端 -> 客户端 广播某玩家的皮肤信息 */
    public static final Identifier SKIN_BROADCAST_CHANNEL = new Identifier(MOD_ID, "skin_broadcast");
    /** 网络通道标识：服务端 -> 客户端 请求当前皮肤（玩家加入时） */
    public static final Identifier SKIN_REQUEST_CHANNEL = new Identifier(MOD_ID, "skin_request");

    /** 皮肤 PNG 上传大小上限（64x64/64x64 skin 通常 < 4KB，留足余量） */
    public static final int MAX_SKIN_BYTES = 64 * 1024;

    @Override
    public void onInitialize() {
        LOGGER.info("[QCOFA-OfflineSkin] 正在初始化 QCOFA Offline Skin by xiaomeow_cn");
        NetworkHandler.registerServerReceivers();
    }
}
