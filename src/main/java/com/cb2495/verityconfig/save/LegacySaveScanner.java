package com.cb2495.verityconfig.save;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 从当前游戏目录的上级（即 .minecraft）扫描 versions 下的旧整合包存档。
 * <p>整合包实例通常放在 <code>.minecraft/versions/&lt;实例名&gt;/</code> 下，
 * 每个实例自带 mods 与 saves。这里找出带 Verity 关键词模组的实例，
 * 再把其中存档列出来，供玩家导入当前实例。
 * <p>全部操作都是只读的，扫描失败一律当作「没有发现」，不打扰玩家。
 */
public final class LegacySaveScanner {

    private LegacySaveScanner() {}

    /** mods 文件夹里出现这个关键词（忽略大小写）即认为是 Verity 整合包。 */
    private static final String VERITY_KEYWORD = "verity";

    /** 扫描时最多深入的上层实例数，避免目录异常时卡住。 */
    private static final int MAX_SCAN_ENTRIES = 512;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    /**
     * 一个候选存档。
     *
     * @param directory   存档文件夹
     * @param levelName   level.dat 里的显示名，读不到时退回文件夹名
     * @param lastPlayed  最后游玩时间，读不到时为 0
     * @param gameVersion level.dat 里的游戏版本名，读不到时为空串
     * @param sourceName  所属旧实例的文件夹名，用于提示来源
     */
    public record LegacySave(Path directory, String levelName, long lastPlayed,
                             String gameVersion, String sourceName) {

        /** 最后游玩时间，读不到时返回「未知」。 */
        public String lastPlayedText() {
            if (lastPlayed <= 0L) {
                return "未知";
            }
            return TIME_FORMAT.format(
                    Instant.ofEpochMilli(lastPlayed).atZone(ZoneId.systemDefault()));
        }

        /** 版本信息，缺失时留空由界面省略。 */
        public String versionText() {
            return gameVersion == null ? "" : gameVersion;
        }
    }

    /**
     * 扫描旧整合包存档。
     * <p>versions 的位置随启动器而变，所以两个候选位置都找一遍：
     * <ul>
     *   <li>版本隔离（HMCL、PCL、Prism 等默认开启）时 gameDir 本身就是
     *       实例目录，即 {@code versions/<实例>/}，versions 在它的上级</li>
     *   <li>未隔离时 gameDir 是 {@code .minecraft}，versions 在它下面</li>
     * </ul>
     * 当前实例自己的那份会被跳过，所以两个位置即使指向同一层也不会重复。
     *
     * @param currentGameDir 当前实例的游戏目录
     * @return 按最后游玩时间倒序排列的存档；没有发现时返回空列表
     */
    public static List<LegacySave> scan(Path currentGameDir) {
        List<LegacySave> result = new ArrayList<>();
        if (currentGameDir == null) {
            return result;
        }

        Path gameDir = currentGameDir.toAbsolutePath().normalize();

        // 候选 versions 目录：先看上级（版本隔离），再看自身（未隔离），
        // 最后再看上上级，覆盖「启动器根目录下还有一层 .minecraft」的情况
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        Path parent = gameDir.getParent();
        if (parent != null) {
            candidates.add(parent.resolve("versions"));
        }
        candidates.add(gameDir.resolve("versions"));
        Path grandParent = parent == null ? null : parent.getParent();
        if (grandParent != null) {
            candidates.add(grandParent.resolve("versions"));
        }

        for (Path versionsDir : candidates) {
            if (!Files.isDirectory(versionsDir)) {
                continue;
            }
            collectFromVersions(versionsDir, gameDir, result);
        }

        result.sort(Comparator.comparingLong(LegacySave::lastPlayed).reversed());
        return result;
    }

    /** 遍历一个 versions 目录下的所有实例。 */
    private static void collectFromVersions(Path versionsDir, Path gameDir,
                                            List<LegacySave> result) {
        int visited = 0;
        try (DirectoryStream<Path> instances = Files.newDirectoryStream(versionsDir)) {
            for (Path instance : instances) {
                if (++visited > MAX_SCAN_ENTRIES) {
                    break;
                }
                if (!Files.isDirectory(instance)) {
                    continue;
                }
                // 跳过当前实例自己：版本隔离时它就在这个目录里，
                // 扫它等于把玩家眼前的存档再列一遍
                if (isCurrentInstance(instance, gameDir)) {
                    continue;
                }
                try {
                    collectFromInstance(instance, gameDir, result);
                } catch (Exception ignored) {
                    // 单个实例读不了不影响其他实例
                }
            }
        } catch (IOException ignored) {
            // versions 打不开就当作没有旧实例
        }
    }

    /**
     * 这个实例是不是当前正在运行的那一个。
     * <p>只看游戏目录本身：版本隔离时 {@code gameDir} 就是
     * {@code versions/<实例>}，所以实例目录与它相等即为当前实例。
     * <p>不能顺带把「gameDir 下面 versions 里的同名实例」也算进来 ——
     * 未隔离布局时那正是别的实例，误判会让扫描结果整个变空。
     */
    private static boolean isCurrentInstance(Path instance, Path gameDir) {
        return instance.toAbsolutePath().normalize().equals(gameDir);
    }

    /** 一个实例：先看 mods 里有没有 Verity，再看 saves 里的存档。 */
    private static void collectFromInstance(Path instance, Path gameDir,
                                            List<LegacySave> result) {
        if (!hasVerityMod(instance)) {
            return;
        }

        Path saves = instance.resolve("saves");
        if (!Files.isDirectory(saves)) {
            return;
        }

        String instanceName = instance.getFileName().toString();
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(saves)) {
            for (Path save : dirs) {
                if (!Files.isDirectory(save)) {
                    continue;
                }
                Path normalized = save.toAbsolutePath().normalize();
                // 双保险：万一实例自己没被认出，这里再挡一次「导入到原地」
                if (normalized.equals(gameDir.resolve("saves").normalize())) {
                    continue;
                }
                // 同一个存档可能被多个候选目录扫到，按路径去重
                if (containsPath(result, normalized)) {
                    continue;
                }
                result.add(readSave(save, instanceName));
            }
        } catch (IOException ignored) {
            // saves 打不开就当作这个实例没有存档
        }
    }

    /** 结果里是否已经有这个存档目录（避免多个候选 versions 重复收集）。 */
    private static boolean containsPath(List<LegacySave> result, Path normalized) {
        for (LegacySave save : result) {
            if (save.directory().toAbsolutePath().normalize().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这个实例是不是 Verity 整合包。
     * <p>先看 mods 里的文件名，再看实例文件夹名 —— 有的启动器把模组
     * 放在 mods 的子目录里，也有整合包的 jar 被改名成不含 verity 的，
     * 而实例名通常仍带着版本标识，任一条命中就算。
     */
    private static boolean hasVerityMod(Path instance) {
        // 实例文件夹名本身带 verity 也算（如 versions/Verity 6.7/）
        if (instance.getFileName().toString().toLowerCase(Locale.ROOT)
                .contains(VERITY_KEYWORD)) {
            return true;
        }

        Path modsDir = instance.resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return false;
        }
        if (anyNameContains(modsDir, VERITY_KEYWORD)) {
            return true;
        }
        // 再看一层子目录，兼容按类别归档的 mods 文件夹
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(modsDir)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry) && anyNameContains(entry, VERITY_KEYWORD)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            // 读不了就当作没命中
        }
        return false;
    }

    /** 目录下是否有文件名（忽略大小写）包含关键词。 */
    private static boolean anyNameContains(Path dir, String keyword) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.contains(keyword)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    /**
     * 读取单个存档的信息。
     * <p>只要文件夹存在就算一个存档 —— 旧存档的 level.dat 可能因版本
     * 差异读不出来，那种情况下仍然应该让玩家看到并自行决定是否导入。
     */
    private static LegacySave readSave(Path saveDir, String instanceName) {
        String levelName = "";
        long lastPlayed = 0L;
        String gameVersion = "";

        Path levelDat = saveDir.resolve("level.dat");
        if (Files.isRegularFile(levelDat)) {
            try {
                CompoundTag tag = NbtIo.readCompressed(levelDat.toFile());
                if (tag != null) {
                    levelName = tag.getString("LevelName");
                    lastPlayed = tag.getLong("LastPlayed");
                    CompoundTag version = tag.getCompound("Version");
                    gameVersion = version.getString("Name");
                }
            } catch (Exception ignored) {
                // 读不出来就留空，界面会退回文件夹名
            }
        }

        // level.dat 里没有 LastPlayed 时，用文件夹的最后修改时间兜底，
        // 排序时不至于把旧存档全挤到一堆 0 里
        if (lastPlayed <= 0L) {
            try {
                lastPlayed = Files.getLastModifiedTime(saveDir).toMillis();
            } catch (IOException ignored) {
                // 拿不到就保持 0
            }
        }

        return new LegacySave(saveDir, levelName, lastPlayed, gameVersion, instanceName);
    }

    /**
     * 导入一个存档：复制到当前实例的 saves 下。
     * <p>用复制而不是移动 —— 源存档留着，万一导入后发现选错，
     * 或者旧实例还想继续玩，都还有退路。
     *
     * @param save           要导入的存档
     * @param currentGameDir 当前游戏的 .minecraft 目录
     * @return 导入结果，供界面显示
     */
    public static ImportResult importSave(LegacySave save, Path currentGameDir) {
        if (save == null || currentGameDir == null) {
            return new ImportResult(false, "没有可导入的存档", null);
        }

        Path savesDir = currentGameDir.resolve("saves");
        try {
            Files.createDirectories(savesDir);
        } catch (IOException e) {
            return new ImportResult(false, "无法创建存档目录：" + e.getMessage(), null);
        }

        // 重名就加后缀，绝不覆盖玩家已有的存档
        Path target = uniqueTarget(savesDir, save.directory().getFileName().toString());

        try {
            copyDirectory(save.directory(), target);
            return new ImportResult(true, "已导入到 " + target.getFileName(), target);
        } catch (Exception e) {
            // 中途失败留下半个文件夹会污染存档列表，清掉它
            deleteQuietly(target);
            return new ImportResult(false, "导入失败：" + e.getMessage(), null);
        }
    }

    /** 目标重名时依次尝试 name (2)、name (3)… 直到空位。 */
    private static Path uniqueTarget(Path savesDir, String baseName) {
        Path candidate = savesDir.resolve(baseName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        for (int i = 2; i < 1000; i++) {
            candidate = savesDir.resolve(baseName + " (" + i + ")");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        // 极端情况下退回带时间戳的名字，保证不会覆盖
        return savesDir.resolve(baseName + "-" + System.currentTimeMillis());
    }

    /** 递归复制整个存档目录。 */
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(source)) {
            for (Path entry : entries) {
                Path destination = target.resolve(entry.getFileName().toString());
                if (Files.isDirectory(entry)) {
                    copyDirectory(entry, destination);
                } else {
                    Files.copy(entry, destination);
                }
            }
        }
    }

    /** 删除复制失败留下的残留目录，删不掉就算了。 */
    private static void deleteQuietly(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // 单个文件删不掉不影响其余清理
                    }
                });
            }
        } catch (IOException ignored) {
            // 清理失败无需打扰玩家
        }
    }

    /**
     * 导入结果。
     *
     * @param success 是否成功
     * @param message 给玩家看的一句话
     * @param target  导入后的位置，失败时为 null
     */
    public record ImportResult(boolean success, String message, Path target) {
    }
}
