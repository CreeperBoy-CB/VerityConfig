package com.cb2495.verityconfig.config;

import com.cb2495.verityconfig.util.Log;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 让 Verity 模组立刻重新读取 {@code verity-common.toml}，无需重启游戏。
 * <p>为什么需要主动做这件事：Forge 本来给每个 TOML 配置挂了文件监视器
 * （{@code ConfigFileTypeHandler.reader()} 里对 {@code CommentedFileConfig} 调
 * {@code addWatch}），但本模组写配置走的是「临时文件 + 原子改名替换」，
 * 替换后路径指向的是新文件，监视器监听的旧文件已经不存在，因此收不到变更、
 * 也就不会触发重载。
 * <p>解决办法是直接调用 {@link ModConfig#acceptSyncedConfig(byte[])}：它的实现是
 * {@code setConfigData(解析结果)} + {@code fireEvent(reloading)}，前者内部会调
 * {@code IConfigSpec#acceptConfig}，正是把新值绑回 {@code ForgeConfigSpec} 的那一步。
 * 这个方法本意是给「服务端下发配置」用的，但它对本地文件同样适用。
 * <p>Verity 侧读配置全部是 {@code VerityConfig.XXX.get()} 即时取值、没有缓存副本，
 * 所以一旦 spec 被重新绑定，后续的读取立刻就是新值。
 */
final class VerityConfigReloader {

    private VerityConfigReloader() {}

    /** Verity 的 modId；配置文件名为 {@code verity-common.toml}。 */
    private static final String VERITY_MODID = "verity";

    /**
     * 把改动后的 {@code verity-common.toml} 推给 Verity 的配置对象。
     * <p>只处理 Verity；简单翻译用的是它自己的一套 JSON 配置，与本方法无关。
     *
     * @return {@code true} 表示已成功重载；{@code false} 表示未能重载，
     *         调用方应当退回「提示玩家重启游戏」
     */
    static boolean reloadVerityConfig(Path configFile) {
        try {
            if (!Files.exists(configFile)) {
                Log.warn("重载跳过：配置文件不存在 " + configFile);
                return false;
            }
            ModConfig target = findModConfig();
            if (target == null) {
                Log.warn("重载跳过：未找到 Verity 的 COMMON 配置（可能未加载）");
                return false;
            }
            // 解析与绑定由 Forge 内部完成；解析失败会抛异常，由下面统一兜住
            target.acceptSyncedConfig(Files.readAllBytes(configFile));
            Log.info("已重载 Verity 配置：" + configFile.getFileName());
            return true;
        } catch (Exception e) {
            // 任何失败都要让调用方知道，以便退回到「提示重启」而不是静默失效
            Log.error("重载 Verity 配置失败: " + e.getMessage());
            return false;
        }
    }

    /** 在 Forge 的配置表里找 Verity 的 COMMON 配置。找不到时返回 null。 */
    private static ModConfig findModConfig() {
        for (ModConfig config : ConfigTracker.INSTANCE.fileMap().values()) {
            if (VERITY_MODID.equals(config.getModId()) && config.getType() == ModConfig.Type.COMMON) {
                return config;
            }
        }
        return null;
    }
}
