package com.cb2495.verityconfig.config;

import com.cb2495.verityconfig.util.ConfigIO;
import com.cb2495.verityconfig.util.Log;

import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code config/verity-common.toml} 里 {@code [GeneralSettings]} 段的值。
 * <p>这些选项原本由界面自己逐行匹配读写，每次打开和每次改动都要碰一次磁盘。
 * 集中到这里之后：读取走内存缓存（启动时后台预读一次），写入交给
 * {@link ConfigIO} 在后台线程做，界面不再直接操作文件。
 */
public final class GeneralSettings {

    /** 目标段落头。 */
    public static final String SECTION = "[GeneralSettings]";

    /** 各键名，读取与写入共用，避免两处写字面量写岔。 */
    private static final String KEY_DAY_COUNT = "dayCount";
    private static final String KEY_CAN_CRASH = "canCrash";
    private static final String KEY_PLAY_VIDEO = "playVideo";
    private static final String KEY_REQUIRE_VERITY = "requireVerity";
    private static final String KEY_TRUE_DARKNESS = "trueDarkness";
    private static final String KEY_KILL_WILDLIFE = "killWildlife";
    private static final String KEY_KILL_ENTITIES = "killEntities";
    private static final String KEY_KILL_VILLAGERS = "killVillagers";
    private static final String KEY_SHOW_KARMA = "showKarma";

    // ---------- 值 ----------

    public int dayCount = 5;
    public boolean canCrash = true;
    public boolean playVideo = true;
    public boolean requireVerity = false;
    public boolean trueDarkness = true;
    public boolean killWildlife = true;
    public boolean killEntities = true;
    public boolean killVillagers = true;
    public boolean showKarma = false;

    /** 当前值的一份副本；界面持有副本，避免与缓存互相影响。 */
    public GeneralSettings copy() {
        GeneralSettings copy = new GeneralSettings();
        copy.dayCount = this.dayCount;
        copy.canCrash = this.canCrash;
        copy.playVideo = this.playVideo;
        copy.requireVerity = this.requireVerity;
        copy.trueDarkness = this.trueDarkness;
        copy.killWildlife = this.killWildlife;
        copy.killEntities = this.killEntities;
        copy.killVillagers = this.killVillagers;
        copy.showKarma = this.showKarma;
        return copy;
    }

    // ---------- 缓存与后台读写 ----------

    /** 配置文件路径，运行时解析（字段初始化可能早于 Minecraft 实例创建）。 */
    private static Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/verity-common.toml");
    }

    /** 最近一次读取或保存的结果；界面同步取用，避免在渲染线程上读盘。 */
    private static volatile GeneralSettings cached = null;
    /** 是否已提交过一次后台预读，避免重复排队。 */
    private static final AtomicBoolean preloadQueued = new AtomicBoolean(false);

    /** 启动时后台预读一次，让界面打开时不必再等磁盘。 */
    public static void preload() {
        if (cached != null) return;
        if (!preloadQueued.compareAndSet(false, true)) return;
        ConfigIO.run(() -> {
            try {
                // 在后台线程内再判一次：期间可能已经有保存写入了缓存，
                // 此时不能再用磁盘上的旧值覆盖它
                if (cached == null) cached = readFromDisk();
            } finally {
                preloadQueued.set(false);
            }
        });
    }

    /**
     * 取当前设置。
     * <p>缓存已就绪时直接返回副本；预读尚未完成时退回同步读一次
     * （只可能发生在极早期，之后都由缓存承担）。
     */
    public static GeneralSettings get() {
        GeneralSettings data = cached;
        if (data == null) {
            data = readFromDisk();
            cached = data;
        }
        return data.copy();
    }

    /**
     * 保存设置。
     * <p>内存缓存<b>同步</b>更新——界面重建控件时立刻就能读到新值，
     * 不会出现「刚改完又变回旧值」；磁盘写入则放到后台线程。
     */
    public static void save(GeneralSettings data) {
        GeneralSettings snapshot = data.copy();
        cached = snapshot;
        ConfigIO.run(() -> writeToDisk(snapshot));
    }

    // ---------- 磁盘读写 ----------

    private static GeneralSettings readFromDisk() {
        GeneralSettings data = new GeneralSettings();
        Path file = configFile();
        if (!Files.exists(file)) return data;

        try {
            List<String> lines = ConfigFileUtils.readLines(file);
            for (String line : lines) {
                // 精确键名匹配：避免 hasKarma 误配 showKarma 这类同后缀项
                if (ConfigFileUtils.lineMatchesKey(line, KEY_DAY_COUNT)) {
                    data.dayCount = ConfigFileUtils.parseInt(line, data.dayCount);
                } else if (ConfigFileUtils.lineMatchesKey(line, KEY_CAN_CRASH)) {
                    data.canCrash = ConfigFileUtils.parseBoolean(line);
                } else if (ConfigFileUtils.lineMatchesKey(line, KEY_PLAY_VIDEO)) {
                    data.playVideo = ConfigFileUtils.parseBoolean(line);
                } else if (ConfigFileUtils.lineMatchesKey(line, KEY_REQUIRE_VERITY)) {
                    data.requireVerity = ConfigFileUtils.parseBoolean(line);
                } else if (ConfigFileUtils.lineMatchesKey(line, KEY_TRUE_DARKNESS)) {
                    data.trueDarkness = ConfigFileUtils.parseBoolean(line);
                } else if (ConfigFileUtils.lineMatchesKey(line, KEY_KILL_WILDLIFE)) {
                    data.killWildlife = ConfigFileUtils.parseBoolean(line);
                } else if (ConfigFileUtils.lineMatchesKey(line, KEY_KILL_ENTITIES)) {
                    data.killEntities = ConfigFileUtils.parseBoolean(line);
                } else if (ConfigFileUtils.lineMatchesKey(line, KEY_KILL_VILLAGERS)) {
                    data.killVillagers = ConfigFileUtils.parseBoolean(line);
                } else if (ConfigFileUtils.lineMatchesKey(line, KEY_SHOW_KARMA)) {
                    data.showKarma = ConfigFileUtils.parseBoolean(line);
                }
            }
        } catch (Exception e) {
            Log.warn("读取高级设置失败，使用默认值: " + e.getMessage());
        }
        return data;
    }

    private static void writeToDisk(GeneralSettings data) {
        Path file = configFile();
        List<String> lines = ConfigFileUtils.readLines(file);

        // 段落不存在时补上段落头，保证写出的是规范 TOML；
        // 否则这些键会以裸键形式追加到文件末尾，归属不到 [GeneralSettings]
        boolean hasSection = lines.stream().anyMatch(line -> line.trim().equals(SECTION));
        if (!hasSection) {
            if (!lines.isEmpty()) lines.add("");
            lines.add(SECTION);
        }

        ConfigFileUtils.replaceOrAddRaw(lines, KEY_DAY_COUNT, String.valueOf(data.dayCount), SECTION);
        ConfigFileUtils.replaceOrAddRaw(lines, KEY_CAN_CRASH, String.valueOf(data.canCrash), SECTION);
        ConfigFileUtils.replaceOrAddRaw(lines, KEY_PLAY_VIDEO, String.valueOf(data.playVideo), SECTION);
        ConfigFileUtils.replaceOrAddRaw(lines, KEY_REQUIRE_VERITY, String.valueOf(data.requireVerity), SECTION);
        ConfigFileUtils.replaceOrAddRaw(lines, KEY_TRUE_DARKNESS, String.valueOf(data.trueDarkness), SECTION);
        ConfigFileUtils.replaceOrAddRaw(lines, KEY_KILL_WILDLIFE, String.valueOf(data.killWildlife), SECTION);
        ConfigFileUtils.replaceOrAddRaw(lines, KEY_KILL_ENTITIES, String.valueOf(data.killEntities), SECTION);
        ConfigFileUtils.replaceOrAddRaw(lines, KEY_KILL_VILLAGERS, String.valueOf(data.killVillagers), SECTION);
        ConfigFileUtils.replaceOrAddRaw(lines, KEY_SHOW_KARMA, String.valueOf(data.showKarma), SECTION);

        ConfigFileUtils.writeLines(file, lines);
    }
}
