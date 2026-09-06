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
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith(key)) {
                lines.set(i, key + " = " + value);
                found = true;
                break;
            }
        }
        if (!found) {
            lines.add(key + " = " + value);
        }
    }

    /**
     * 替换或添加字符串键值对（值加引号）。
     */
    public static void replaceOrAddString(List<String> lines, String key, String value) {
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith(key)) {
                lines.set(i, key + " = \"" + value + "\"");
                found = true;
                break;
            }
        }
        if (!found) {
            lines.add(key + " = \"" + value + "\"");
        }
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

    public static int parseInt(String line, int defaultVal) {
        int idx = line.indexOf('=');
        if (idx == -1) return defaultVal;
        String val = line.substring(idx + 1).trim();
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}