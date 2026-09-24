package com.cb2495.verityconfig.util;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 本模组自身配置（{@code config/verityconfig-common.toml}）的读写。
 * <p>与 Verity JE 的 {@code verity-common.toml} 分开存放：
 * 这里的选项属于配置界面自身的外观，不应混进被配置模组的配置里。
 */
public final class ModConfig {

    private ModConfig() {}

    private static final String SECTION = "[General]";
    private static final String SHOW_SPONSOR = "ShowSponsor";

    /** 赞助码显示开关的默认值。 */
    private static final boolean DEFAULT_SHOW_SPONSOR = true;

    /** 配置文件路径，运行时解析（字段初始化早于 Screen 构造，不能提前取实例）。 */
    private static Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/verityconfig-common.toml");
    }

    /**
     * 配置文件不存在时创建并写入默认值，供客户端启动时调用。
     * <p>已存在则不做任何改动，避免覆盖玩家设置。
     */
    public static void createDefaultIfMissing() {
        if (Files.exists(configFile())) return;
        setShowSponsor(DEFAULT_SHOW_SPONSOR);
    }

    /**
     * 是否允许显示赞助码。默认 {@code true}。
     * <p>配置项缺失或读取失败时返回默认值。
     */
    public static boolean isShowSponsor() {
        Path file = configFile();
        if (!Files.exists(file)) return DEFAULT_SHOW_SPONSOR;
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                if (ConfigFileUtils.lineMatchesKey(line, SHOW_SPONSOR)) {
                    return ConfigFileUtils.parseBoolean(line);
                }
            }
        } catch (Exception e) {
            System.err.println("[VerityConfig] 读取 " + file + " 失败: " + e.getMessage());
        }
        return DEFAULT_SHOW_SPONSOR;
    }

    /** 写入 ShowSponsor；文件或段落不存在时自动创建。 */
    public static void setShowSponsor(boolean value) {
        Path file = configFile();
        List<String> lines = ConfigFileUtils.readLines(file);
        // 首次写入时文件为空，ConfigFileUtils 找不到目标段落会写成裸键，
        // 这里先补上段落头，保证生成的文件是规范的 TOML
        boolean hasSection = lines.stream().anyMatch(l -> l.trim().equals(SECTION));
        if (!hasSection) {
            if (!lines.isEmpty()) lines.add("");
            lines.add(SECTION);
        }
        ConfigFileUtils.replaceOrAddRaw(lines, SHOW_SPONSOR, String.valueOf(value), SECTION);
        ConfigFileUtils.writeLines(file, lines);
    }
}
