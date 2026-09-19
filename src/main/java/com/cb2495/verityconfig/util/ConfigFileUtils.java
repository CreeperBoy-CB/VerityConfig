package com.cb2495.verityconfig.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfigFileUtils {
    private ConfigFileUtils() {}

    public static List<String> readLines(Path file) {
        try {
            if (Files.exists(file)) {
                return new ArrayList<>(Files.readAllLines(file));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static void writeLines(Path file, List<String> lines) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 替换或添加键值对。值不加引号，用于布尔和数字。
     */
    public static void replaceOrAddRaw(List<String> lines, String key, String value) {
        replaceOrAdd(lines, key, key + " = " + value, null);
    }

    /**
     * 替换或添加布尔/数字键值对，缺失时插入到指定 TOML 段落内。
     *
     * @param section 目标段落头（含方括号，如 {@code [GeneralSettings]}）；为 null 时追加到文件末尾
     */
    public static void replaceOrAddRaw(List<String> lines, String key, String value, String section) {
        replaceOrAdd(lines, key, key + " = " + value, section);
    }

    /**
     * 替换或添加字符串键值对（值加引号）。
     */
    public static void replaceOrAddString(List<String> lines, String key, String value) {
        replaceOrAdd(lines, key, key + " = \"" + value + "\"", null);
    }

    /**
     * 替换或添加键值对。
     * <p>已有键就地替换；缺失时若指定了 {@code section}，则插入到该段落内，
     * 避免把属于某个段落的键追加到文件末尾而改变其归属。
     */
    private static void replaceOrAdd(List<String> lines, String key, String newLine, String section) {
        for (int i = 0; i < lines.size(); i++) {
            if (lineMatchesKey(lines.get(i), key)) {
                lines.set(i, newLine);
                return;
            }
        }
        insertIntoSection(lines, newLine, section);
    }

    /** 插入新键：优先放入指定段落，找不到段落则追加到文件末尾。 */
    private static void insertIntoSection(List<String> lines, String newLine, String section) {
        int sectionStart = -1;
        if (section != null) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals(section)) {
                    sectionStart = i;
                    break;
                }
            }
        }

        if (sectionStart < 0) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
                lines.add("");
            }
            lines.add(newLine);
            return;
        }

        // 段落范围内最后一个非空行的位置（不越过下一个段落头）
        int insertAt = sectionStart + 1;
        for (int i = sectionStart + 1; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("[")) break;
            if (!trimmed.isEmpty()) insertAt = i + 1;
        }
        lines.add(insertAt, newLine);
    }

    public static String extractStringValue(String line) {
        int start = line.indexOf('"');
        int end = line.lastIndexOf('"');
        if (start >= 0 && end > start) {
            return line.substring(start + 1, end);
        }
        return "";
    }

    public static boolean parseBoolean(String line) {
        return line.contains("true");
    }

    /**
     * 判断一行是否为 {@code key = value} 形式且键名精确等于 key。
     * <p>必须是精确匹配：早期实现只比较前缀，导致 {@code aiModel} 会误配
     * {@code aiModelPreset} 这一行，进而把模型名写成预设值（SMART/FAST）。
     */
    public static boolean lineMatchesKey(String line, String key) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '[') return false;
        if (!trimmed.startsWith(key)) return false;

        int index = key.length();
        // 跳过键名与分隔符之间的空白
        while (index < trimmed.length() && (trimmed.charAt(index) == ' ' || trimmed.charAt(index) == '\t')) {
            index++;
        }
        return index < trimmed.length() && trimmed.charAt(index) == '=';
    }

    /**
     * 取出一行中 {@code =} 之后的值（已 trim）。解析失败时返回空串。
     */
    public static String getValue(String line) {
        int idx = line.indexOf('=');
        return idx == -1 ? "" : line.substring(idx + 1).trim();
    }

    public static int parseInt(String line, int defaultVal) {
        try {
            return Integer.parseInt(getValue(line));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}