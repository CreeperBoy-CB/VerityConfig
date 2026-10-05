package com.cb2495.verityconfig.config;

import com.cb2495.verityconfig.util.ConfigIO;
import com.cb2495.verityconfig.util.Log;
import com.cb2495.verityconfig.util.PlatformUtils;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VerityConfigManager {

    /**
     * 配置文件路径。
     * <p>必须延迟解析：写成 static final 字段会在类加载时就去读
     * {@code Minecraft.getInstance().gameDirectory}，而类加载时机不可控
     * （可能早于 Minecraft 实例创建），从而拿到 null 或直接抛异常。
     * <p>{@code gameDirectory} 是 final 字段，且这三次路径解析在后台线程里
     * 调用也是安全的。
     */
    private static Path configFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/verity-common.toml");
    }

    private static Path translateConfig() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/simple_translate/simple_translate-client.json");
    }

    // 提供商的端点、API 格式、Key 地址与模型统一由 Providers 提供，
    // 这里只做转发，避免出现第二份提供商清单。
    // （原本 API_FORMATS 这个字段也一并删掉了：它只是 Providers 的副本。）

    public static String getEndpointForProvider(String provider) {
        return Providers.endpointFor(provider);
    }

    public static String inferProviderFromEndpoint(String endpoint, String defaultProvider) {
        return Providers.inferFromEndpoint(endpoint, defaultProvider);
    }

    public static String getChatCompletionsUrl(String provider, String baseUrl) {
        return Providers.chatUrlFor(provider, baseUrl);
    }

    public static String getApiFormatForProvider(String provider) {
        return Providers.apiFormatFor(provider);
    }

    // ========== 缓存与后台读写 ==========
    /**
     * 最近一次读取或保存的结果。
     * <p>界面需要在渲染线程上同步拿到配置来填充控件，而读盘不该发生在渲染
     * 线程上，所以用缓存隔开：启动时后台预读一次，之后读的是内存。
     */
    private static volatile ConfigData cached = null;
    /** 是否已提交过一次后台预读，避免重复排队。 */
    private static final AtomicBoolean preloadQueued = new AtomicBoolean(false);

    /** 启动时后台预读一次配置。 */
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

    // ========== 读取 verity-common.toml ==========
    /**
     * 取 AI 配置。
     * <p>缓存已就绪时直接返回副本；预读尚未完成时退回同步读一次
     * （只可能发生在极早期，之后都由缓存承担）。
     * <p>返回副本而不是缓存对象本身：调用方会就地修改 model 等字段，
     * 直接返回会让缓存被界面悄悄改掉。
     */
    public static ConfigData loadVerityConfig() {
        ConfigData data = cached;
        if (data == null) {
            data = readFromDisk();
            cached = data;
        }
        return data.copy();
    }

    private static ConfigData readFromDisk() {
        ConfigData data = new ConfigData();
        List<String> lines = ConfigFileUtils.readLines(configFile());
        for (String line : lines) {
            line = line.trim();
            if (ConfigFileUtils.lineMatchesKey(line, "apiKey")) data.apiKey = ConfigFileUtils.extractStringValue(line);
            else if (ConfigFileUtils.lineMatchesKey(line, "aiEndpoint")) data.endpoint = ConfigFileUtils.extractStringValue(line);
            else if (ConfigFileUtils.lineMatchesKey(line, "aiModel")) data.model = ConfigFileUtils.extractStringValue(line);
            else if (ConfigFileUtils.lineMatchesKey(line, "aiThink")) data.think = ConfigFileUtils.parseBoolean(line);
            else if (ConfigFileUtils.lineMatchesKey(line, "ttsProvider")) data.ttsProvider = ConfigFileUtils.extractStringValue(line);
            else if (ConfigFileUtils.lineMatchesKey(line, "useTTS")) data.useTTS = ConfigFileUtils.parseBoolean(line);
        }
        // 如果 API Key 为空，强制默认
        if (data.apiKey.isEmpty()) {
            data.think = false;
            // 语音默认开启。电脑用 Verity 本地模型（英文），
            // 手机在 FCL 下只有 NATIVE 能出声，所以默认用系统原生 TTS（中文）
            data.ttsProvider = PlatformUtils.isWindows() ? "LOCAL" : "NATIVE";
            data.useTTS = true;
        }
        return data;
    }

    // ========== 保存 ==========
    /**
     * 保存 AI 配置：{@code verity-common.toml} 与 simple_translate 的配置一起写。
     * <p>内存缓存<b>同步</b>更新，磁盘写入交给后台线程——界面重建控件时
     * （例如窗口缩放触发 {@code init()}）立刻就能读到刚保存的值。
     */
    public static void saveAll(ConfigData data, String provider) {
        ConfigData snapshot = data.copy();
        cached = snapshot;
        ConfigIO.run(() -> {
            writeVerityConfig(snapshot);
            writeSimpleTranslateConfig(provider, snapshot.apiKey, snapshot.endpoint, snapshot.model);
        });
    }

    private static void writeVerityConfig(ConfigData data) {
        Path file = configFile();
        List<String> lines = ConfigFileUtils.readLines(file);
        ConfigFileUtils.replaceOrAddString(lines, "apiKey", data.apiKey);
        ConfigFileUtils.replaceOrAddString(lines, "aiEndpoint", data.endpoint);
        ConfigFileUtils.replaceOrAddString(lines, "aiModel", data.model);
        ConfigFileUtils.replaceOrAddRaw(lines, "aiThink", String.valueOf(data.think));
        ConfigFileUtils.replaceOrAddRaw(lines, "useTTS", String.valueOf(data.useTTS));
        ConfigFileUtils.replaceOrAddString(lines, "ttsProvider", data.ttsProvider);
        ConfigFileUtils.replaceOrAddString(lines, "aiProvider", "OPENAI");
        ConfigFileUtils.writeLines(file, lines);
    }

    // ========== 同步到 simple_translate 配置 ==========
    private static void writeSimpleTranslateConfig(String provider, String apiKey, String endpoint, String model) {
        Path file = translateConfig();
        if (!Files.exists(file)) return;
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            JsonElement root;
            try {
                root = JsonParser.parseString(content);
            } catch (Exception e) {
                root = new JsonObject();
            }
            if (root == null || !root.isJsonObject()) root = new JsonObject();
            JsonObject json = root.getAsJsonObject();
            String apiUrl = getChatCompletionsUrl(provider, endpoint);
            json.addProperty("api.apiKey", apiKey);
            json.addProperty("api.model", model);
            json.addProperty("api.apiUrl", apiUrl);
            json.addProperty("api.format", getApiFormatForProvider(provider));
            json.addProperty("shortcuts.translateGui", "keyboard:262:0"); // 保留原键位
            // 必须显式指定 UTF-8：原本用 FileWriter，走的是平台默认编码
            // （中文 Windows 上是 GBK），而读取那边用的是 UTF-8，一旦文件里
            // 出现中文就会被写成另一种编码。
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (Exception e) {
            Log.error("无法写入 simple_translate 配置: " + e.getMessage());
        }
    }

    public static String getCurrentProvider() {
        ConfigData data = loadVerityConfig();
        return inferProviderFromEndpoint(data.endpoint, "DeepSeek");
    }

    // 数据容器
    public static class ConfigData {
        public String apiKey = "";
        public String endpoint = "";
        public String model = "";
        public boolean think = false;
        public boolean useTTS = true;
        public String ttsProvider = "LOCAL";

        /** 值副本；缓存与界面之间靠它隔离。 */
        public ConfigData copy() {
            ConfigData copy = new ConfigData();
            copy.apiKey = this.apiKey;
            copy.endpoint = this.endpoint;
            copy.model = this.model;
            copy.think = this.think;
            copy.useTTS = this.useTTS;
            copy.ttsProvider = this.ttsProvider;
            return copy;
        }
    }
}
