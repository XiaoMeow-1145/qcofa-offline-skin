package cn.qcofa.offlineskin.skin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 本地皮肤文件读写。皮肤 PNG 与模型类型保存在
 * {@code <game>/config/qcofa_offline_skin/} 目录下。
 */
public final class LocalSkinFile {
    private static final String DIR_NAME = "qcofa_offline_skin";
    private static final String SKIN_PNG = "skin.png";
    private static final String SKIN_PROPS = "skin.properties";

    private LocalSkinFile() {}

    public static Path configDir(Path runDir) {
        return runDir.resolve("config").resolve(DIR_NAME);
    }

    public static Path skinPngPath(Path runDir) {
        return configDir(runDir).resolve(SKIN_PNG);
    }

    public static Path skinPropsPath(Path runDir) {
        return configDir(runDir).resolve(SKIN_PROPS);
    }

    /** 读取本地皮肤 PNG 字节，不存在则返回 null */
    public static byte[] readSkinPng(Path runDir) {
        try {
            Path p = skinPngPath(runDir);
            if (Files.exists(p)) {
                return Files.readAllBytes(p);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** 写入本地皮肤 PNG */
    public static void writeSkinPng(Path runDir, byte[] data) {
        try {
            Path dir = configDir(runDir);
            Files.createDirectories(dir);
            Files.write(skinPngPath(runDir), data);
        } catch (Exception ignored) {}
    }

    /** 读取模型类型：default / slim。默认 default */
    public static String readModel(Path runDir) {
        try {
            Path p = skinPropsPath(runDir);
            if (Files.exists(p)) {
                Properties props = new Properties();
                props.load(Files.newInputStream(p));
                String m = props.getProperty("model", "default");
                return "slim".equalsIgnoreCase(m) ? "slim" : "default";
            }
        } catch (Exception ignored) {}
        return "default";
    }

    /** 写入模型类型 */
    public static void writeModel(Path runDir, String model) {
        try {
            Path dir = configDir(runDir);
            Files.createDirectories(dir);
            Properties props = new Properties();
            props.setProperty("model", "slim".equalsIgnoreCase(model) ? "slim" : "default");
            props.store(Files.newOutputStream(skinPropsPath(runDir)), "QCOFA Offline Skin");
        } catch (Exception ignored) {}
    }
}
