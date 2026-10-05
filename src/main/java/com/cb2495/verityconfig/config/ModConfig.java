package com.cb2495.verityconfig.config;

import com.cb2495.verityconfig.util.ConfigIO;
import com.cb2495.verityconfig.util.Log;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 本模组自身配置（{@code config/verityconfig-common.toml}）的读写。
 * <p>与 Verity JE 的 {@code verity-common.toml} 分开存放：
 * 这里的选项属于配置界面自身的外观，不应混进被配置模组的配置里。
 * <p>值在内存里缓存：读取不再碰磁盘（配置界面的 {@code init()} 会读它，
 * 而 {@code init()} 在窗口缩放时会被反复调用），写入交给 {@link ConfigIO}
 * 在后台线程做。
 */
public final class ModConfig {

    private ModConfig() {}

    private static final String SECTION = "[General]";
    private static final String SHOW_SPONSOR = "ShowSponsor";
    private static final String SHOW_DEEPSEEK_HINT = "ShowDeepSeekHint";

    /** 赞助码显示开关的默认值。 */
    private static final boolean DEFAULT_SHOW_SPONSOR = true;

    /** DeepSeek 峰谷时段提示的默认值。 */
    private static final boolean DEFAULT_SHOW_DEEPSEEK_HINT = true;

    /** 配置文件路径，运行时解析（字段初始化早于 Screen 构造，不能提前取实例）。 */
    private static Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/verityconfig-common.toml");
    }

    // ---------- 缓存 ----------

    /**
     * 两个开关的当前值。
     * <p>整体替换而不是就地改字段：{@link #values} 是 volatile 的，
     * 换成新对象能保证读线程立刻看到完整的一次更新，而不是改了一半的状态。
     */
    private static final class Values {
        boolean showSponsor = DEFAULT_SHOW_SPONSOR;
        boolean showDeepSeekHint = DEFAULT_SHOW_DEEPSEEK_HINT;
    }

    private static volatile Values values = null;
    private static final Object LOAD_LOCK = new Object();

    /** 取当前值；首次访问时读一次磁盘，之后都走内存。 */
    private static Values values() {
        Values current = values;
        if (current != null) return current;
        synchronized (LOAD_LOCK) {
            if (values == null) {
                values = readFromDisk();
            }
            return values;
        }
    }

    /**
     * 配置文件不存在时创建并写入默认值，供客户端启动时调用。
     * <p>已存在则只读取，不做任何改动，避免覆盖玩家设置。
     */
    public static void createDefaultIfMissing() {
        if (Files.exists(configFile())) {
            values();
            return;
        }
        Values defaults = new Values();
        values = defaults;
        ConfigIO.run(() -> writeToDisk(defaults));
    }

    // ---------- 赞助码开关 ----------

    /**
     * 是否允许显示赞助码。默认 {@code true}。
     * <p>配置项缺失或读取失败时返回默认值。
     */
    public static boolean isShowSponsor() {
        return values().showSponsor;
    }

    /** 写入 ShowSponsor；文件或段落不存在时自动创建。 */
    public static void setShowSponsor(boolean value) {
        Values current = values();
        Values updated = new Values();
        updated.showSponsor = value;
        updated.showDeepSeekHint = current.showDeepSeekHint;
        values = updated;
        ConfigIO.run(() -> writeToDisk(updated));
    }

    // ---------- DeepSeek 峰谷提示开关 ----------

    /**
     * 是否显示 DeepSeek 峰谷时段提示。默认 {@code true}。
     * <p>配置项缺失或读取失败时返回默认值。
     */
    public static boolean isShowDeepSeekHint() {
        return values().showDeepSeekHint;
    }

    /** 写入 ShowDeepSeekHint；文件或段落不存在时自动创建。 */
    public static void setShowDeepSeekHint(boolean value) {
        Values current = values();
        Values updated = new Values();
        updated.showSponsor = current.showSponsor;
        updated.showDeepSeekHint = value;
        values = updated;
        ConfigIO.run(() -> writeToDisk(updated));
    }

    // ---------- 磁盘读写 ----------

    private static Values readFromDisk() {
        Values result = new Values();
        Path file = configFile();
        if (!Files.exists(file)) return result;
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                if (ConfigFileUtils.lineMatchesKey(line, SHOW_SPONSOR)) {
                    result.showSponsor = ConfigFileUtils.parseBoolean(line);
                } else if (ConfigFileUtils.lineMatchesKey(line, SHOW_DEEPSEEK_HINT)) {
                    result.showDeepSeekHint = ConfigFileUtils.parseBoolean(line);
                }
            }
        } catch (Exception e) {
            Log.warn("读取 " + file + " 失败: " + e.getMessage());
        }
        return result;
    }

    private static void writeToDisk(Values snapshot) {
        Path file = configFile();
        List<String> lines = ConfigFileUtils.readLines(file);
        // 首次写入时文件为空，ConfigFileUtils 找不到目标段落会写成裸键，
        // 这里先补上段落头，保证生成的文件是规范的 TOML
        boolean hasSection = lines.stream().anyMatch(l -> l.trim().equals(SECTION));
        if (!hasSection) {
            if (!lines.isEmpty()) lines.add("");
            lines.add(SECTION);
        }
        ConfigFileUtils.replaceOrAddRaw(lines, SHOW_SPONSOR, String.valueOf(snapshot.showSponsor), SECTION);
        ConfigFileUtils.replaceOrAddRaw(lines, SHOW_DEEPSEEK_HINT, String.valueOf(snapshot.showDeepSeekHint), SECTION);
        ConfigFileUtils.writeLines(file, lines);
    }
}
