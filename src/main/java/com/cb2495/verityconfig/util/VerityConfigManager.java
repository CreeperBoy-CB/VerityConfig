package com.cb2495.verityconfig.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

public class VerityConfigManager {
    private static final Path CONFIG_FILE = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config/verity-common.toml");
    private static final Path TRANSLATE_CONFIG = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config/simple_translate/simple_translate-client.json");

    // 提供商 → 端点映射
    private static final Map<String, String> PROVIDER_ENDPOINTS = new LinkedHashMap<>();
    static {
        PROVIDER_ENDPOINTS.put("DeepSeek", "https://api.deepseek.com/v1");
        PROVIDER_ENDPOINTS.put("智谱 (GLM)", "https://open.bigmodel.cn/api/paas/v4");
        PROVIDER_ENDPOINTS.put("阿里云百炼 (通义千问)", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        PROVIDER_ENDPOINTS.put("Kimi", "https://api.moonshot.cn/v1");
        PROVIDER_ENDPOINTS.put("自定义", "");
    }

    // 提供商 → API格式
    private static final Map<String, String> API_FORMATS = new HashMap<>();
    static {
        API_FORMATS.put("DeepSeek", "DEEPSEEK_CHAT");
        API_FORMATS.put("智谱 (GLM)", "OPENAI_CHAT_COMPAT");
        API_FORMATS.put("阿里云百炼 (通义千问)", "OPENAI_CHAT_COMPAT");
        API_FORMATS.put("Kimi", "OPENAI_CHAT_COMPAT");
        API_FORMATS.put("自定义", "OPENAI_CHAT_COMPAT");
    }

    public static List<String> getProviderNames() {
        return new ArrayList<>(PROVIDER_ENDPOINTS.keySet());
    }

    public static String getEndpointForProvider(String provider) {
        return PROVIDER_ENDPOINTS.getOrDefault(provider, "");
    }

    public static String inferProviderFromEndpoint(String endpoint, String defaultProvider) {
        if (endpoint.isEmpty()) return defaultProvider;
        for (Map.Entry<String, String> entry : PROVIDER_ENDPOINTS.entrySet()) {
            if (entry.getValue().equals(endpoint)) {
                return entry.getKey();
            }
        }
        return "自定义";
    }

    public static String getChatCompletionsUrl(String provider, String baseUrl) {
        // 预定义提供商直接使用固定 URL（即使 baseUrl 可能被修改，这里保持原逻辑）
        switch (provider) {
            case "DeepSeek": return "https://api.deepseek.com/chat/completions";
            case "智谱 (GLM)": return "https://open.bigmodel.cn/api/paas/v4/chat/completions";
            case "阿里云百炼 (通义千问)": return "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            case "Kimi": return "https://api.moonshot.cn/v1/chat/completions";
            default:
                if (baseUrl.endsWith("/chat/completions")) return baseUrl;
                if (baseUrl.endsWith("/")) return baseUrl + "chat/completions";
                return baseUrl + "/chat/completions";
        }
    }

    public static String getApiFormatForProvider(String provider) {
        return API_FORMATS.getOrDefault(provider, "OPENAI_CHAT_COMPAT");
    }

    // ========== 读取 verity-common.toml ==========
    public static ConfigData loadVerityConfig() {
        ConfigData data = new ConfigData();
        List<String> lines = ConfigFileUtils.readLines(CONFIG_FILE);
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
            data.ttsProvider = "LOCAL";
            data.useTTS = PlatformUtils.isWindows(); // 只有 Windows 才开启语音
        }
        return data;
    }

    // ========== 保存 verity-common.toml ==========
    public static void saveVerityConfig(ConfigData data) {
        List<String> lines = ConfigFileUtils.readLines(CONFIG_FILE);
        ConfigFileUtils.replaceOrAddString(lines, "apiKey", data.apiKey);
        ConfigFileUtils.replaceOrAddString(lines, "aiEndpoint", data.endpoint);
        ConfigFileUtils.replaceOrAddString(lines, "aiModel", data.model);
        ConfigFileUtils.replaceOrAddRaw(lines, "aiThink", String.valueOf(data.think));
        ConfigFileUtils.replaceOrAddRaw(lines, "useTTS", String.valueOf(data.useTTS));
        ConfigFileUtils.replaceOrAddString(lines, "ttsProvider", data.ttsProvider);
        ConfigFileUtils.replaceOrAddString(lines, "aiProvider", "OPENAI");
        ConfigFileUtils.writeLines(CONFIG_FILE, lines);
    }

    // ========== 同步到 simple_translate 配置 ==========
    public static void saveSimpleTranslateConfig(String provider, String apiKey, String endpoint, String model) {
        if (!java.nio.file.Files.exists(TRANSLATE_CONFIG)) return;
        try {
            String content = java.nio.file.Files.readString(TRANSLATE_CONFIG).trim();
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
            try (FileWriter writer = new FileWriter(TRANSLATE_CONFIG.toFile())) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (Exception e) {
            System.err.println("[VerityConfig] 无法写入 simple_translate 配置: " + e.getMessage());
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
    }
}