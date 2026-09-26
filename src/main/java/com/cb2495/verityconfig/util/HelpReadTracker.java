package com.cb2495.verityconfig.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 记录用户读过哪些帮助文档。
 * <p>用于在用户启用某些模组却没看过对应教程时给出提醒。
 * <p>记录两类主题：真正打开过的帮助文档，以及在提醒界面点过
 * 「我已了解，继续」的那些——后者表示用户自认不需要看，不该再提醒。
 * <p>数据存在 {@code .cache/verity_config/read_help_message.json}。
 */
public final class HelpReadTracker {
    private HelpReadTracker() {}

    private static Path readFile() {
        return FMLPaths.GAMEDIR.get()
                .resolve(".cache/verity_config/read_help_message.json");
    }

    /** 已读主题集合；null 表示尚未从磁盘加载。 */
    private static Set<String> readTopics = null;

    private static Set<String> topics() {
        if (readTopics == null) {
            readTopics = load();
        }
        return readTopics;
    }

    private static Set<String> load() {
        Set<String> result = new LinkedHashSet<>();
        Path file = readFile();
        if (!Files.exists(file)) return result;
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) return result;
            JsonElement root = JsonParser.parseString(content);
            if (!root.isJsonObject()) return result;
            JsonElement topics = root.getAsJsonObject().get("readTopics");
            if (topics == null || !topics.isJsonArray()) return result;
            JsonArray array = topics.getAsJsonArray();
            for (JsonElement element : array) {
                if (element != null && element.isJsonPrimitive()) {
                    result.add(element.getAsString());
                }
            }
        } catch (Exception e) {
            // 文件损坏时按「没读过」处理，而不是让界面崩溃
            System.err.println("[VerityConfig] 读取已读帮助记录失败，按未读处理: " + e.getMessage());
        }
        return result;
    }

    /** 该帮助主题是否已被阅读过。 */
    public static boolean hasRead(String topic) {
        if (topic == null || topic.isEmpty()) return false;
        return topics().contains(topic);
    }

    /**
     * 标记某帮助主题为已读并写回磁盘。
     * <p>已读过时直接返回，避免每次打开教程都重写文件。
     */
    public static void markRead(String topic) {
        if (topic == null || topic.isEmpty()) return;
        if (!topics().add(topic)) return;
        save();
    }

    /**
     * 批量标记为已读。
     * <p>用于在提醒界面点「我已了解，继续」时，把本次提醒到的模组
     * 一次性记为已读——用户既然选择继续，就不该在下次启动时又被拦一次。
     * <p>只写一次磁盘，而不是逐个调用 {@link #markRead}。
     */
    public static void markAllRead(Collection<String> topicList) {
        if (topicList == null || topicList.isEmpty()) return;
        boolean changed = false;
        for (String topic : topicList) {
            if (topic == null || topic.isEmpty()) continue;
            if (topics().add(topic)) changed = true;
        }
        if (changed) save();
    }

    private static void save() {
        Path file = readFile();
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            for (String topic : topics()) {
                array.add(topic);
            }
            root.add("readTopics", array);
            Files.writeString(file,
                    new GsonBuilder().setPrettyPrinting().create().toJson(root),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[VerityConfig] 保存已读帮助记录失败: " + e.getMessage());
        }
    }
}
