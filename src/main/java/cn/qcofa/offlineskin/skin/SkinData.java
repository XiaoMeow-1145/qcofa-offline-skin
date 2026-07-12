package cn.qcofa.offlineskin.skin;

/**
 * 表示一个离线皮肤的元数据。
 *
 * @param modelType 皮肤模型类型："default" 经典宽臂，"slim" 苗条细臂
 * @param hash       皮肤 PNG 数据的 SHA-1 哈希（十六进制），用作缓存/标识键
 * @param data       皮肤 PNG 的原始字节数据（可能为空，仅携带元数据时）
 */
public record SkinData(String modelType, String hash, byte[] data) {

    public static final SkinData EMPTY = new SkinData("default", "", null);

    /** 是否为有效（已设置）的皮肤 */
    public boolean isPresent() {
        return hash != null && !hash.isEmpty() && data != null && data.length > 0;
    }

    public boolean isSlim() {
        return "slim".equalsIgnoreCase(modelType);
    }
}
