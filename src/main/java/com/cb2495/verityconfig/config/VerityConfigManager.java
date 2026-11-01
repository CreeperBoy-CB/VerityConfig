package com.cb2495.verityconfig.config;

import com.cb2495.verityconfig.util.ConfigIO;
import com.cb2495.verityconfig.util.Log;
import com.cb2495.verityconfig.util.PlatformUtils;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
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

    // ========== 首次启动时铺模板配置 ==========
    /** 模组内置的模板配置目录（打进 jar：resources/assets/verityconfig/config）。 */
    private static final String TEMPLATE_DIR = "/assets/verityconfig/config/";
    /** 模板文件名：与游戏目录中的相对路径一一对应。 */
    private static final String TEMPLATE_VERITY = "verity-common.toml";
    /** 手机端专用模板：语音默认关闭、Provider 用 NATIVE，其余与电脑版一致。 */
    private static final String TEMPLATE_VERITY_MOBILE = "verity-common-m.toml";
    private static final String TEMPLATE_TRANSLATE = "simple_translate/simple_translate-client.json";

    /**
     * 两份配置文件任意一份缺失时，把内置模板铺到游戏目录的 config 下。
     * <p>按「任意一个不存在就两份都复制」处理：这两份是一套联动的配置
     * （API Key、Base URL、模型名在两边必须一致），只补其中一份会留下
     * 一个「有 Key 但没地址」的半套配置。
     * <p>已经存在的文件一律不动，所以玩家改过的配置不会被冲掉。
     * <p>必须在 {@link #preload()} 之前同步执行：预读会立刻读盘，晚了就读到空配置。
     */
    public static void copyTemplatesIfMissing() {
        Path verityFile = configFile();
        Path translateFile = translateConfig();
        if (Files.exists(verityFile) && Files.exists(translateFile)) return;

        String variant = PlatformUtils.isWindows() ? TEMPLATE_VERITY : TEMPLATE_VERITY_MOBILE;
        copyTemplate(variant, verityFile);
        copyTemplate(TEMPLATE_TRANSLATE, translateFile);
    }

    /** 读模板并写到目标位置；目标已存在时绝不覆盖，模板缺失也不影响启动。 */
    private static void copyTemplate(String name, Path target) {
        try (InputStream in = VerityConfigManager.class.getResourceAsStream(TEMPLATE_DIR + name)) {
            if (in == null) {
                Log.warn("模板配置不存在: " + TEMPLATE_DIR + name);
                return;
            }
            Files.createDirectories(target.getParent());
            // 不写 REPLACE_EXISTING：本方法唯一的用途是补缺失文件，
            // 覆盖会让玩家已有的 Key 与设置全部丢失。
            Files.copy(in, target);
            Log.info("已从内置模板生成配置: " + target);
        } catch (IOException e) {
            Log.error("生成配置失败: " + target, e);
        }
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
        // 这里刻意不再按平台覆写默认值：配置内容的唯一来源是文件本身，
        // 平台差异（手机默认关语音等）在生成模板时就已写进 verity-common[-m].toml。
        // 在读取端再判一次会让「文件里写的」和「界面显示的」不一致。
        // 文件里缺少某一项时，该项取 ConfigData 的字段初值（不再按平台推断）。
        return data;
    }

    // ========== 保存 ==========
    /**
     * 保存 AI 配置：{@code verity-common.toml} 与 simple_translate 的配置一起写。
     * <p>内存缓存<b>同步</b>更新，磁盘写入交给后台线程——界面重建控件时
     * （例如窗口缩放触发 {@code init()}）立刻就能读到刚保存的值。
     * <p>写完之后立刻尝试让两个模组各自重载配置，成功后都无需重启即可使用新值。
     */
    public static void saveAll(ConfigData data, String provider) {
        ConfigData snapshot = data.copy();
        cached = snapshot;
        ConfigIO.run(() -> writeAllNow(snapshot, provider));
    }

    /**
     * 同步写盘，并让 Verity 与简单翻译立刻重载配置，返回两者是否都已即时生效。
     * <p>用于「完成」这种需要当场生效的操作：异步写盘的话，调用方返回时
     * 重载还没发生，两边也就都还是旧配置。
     * <p>本方法会阻塞调用线程做磁盘 IO，不要在每帧都会执行的路径上调用。
     *
     * @return {@code true} 表示两边都已生效，调用方不必再提示重启
     */
    public static boolean saveAllAndReload(ConfigData data, String provider) {
        ConfigData snapshot = data.copy();
        cached = snapshot;
        return writeAllNow(snapshot, provider);
    }

    /** 写两份配置并让两边各自重载；返回是否全部即时生效。 */
    private static boolean writeAllNow(ConfigData snapshot, String provider) {
        writeVerityConfig(snapshot);
        writeSimpleTranslateConfig(provider, snapshot.apiKey, snapshot.endpoint, snapshot.model);
        // 必须排在写盘之后：重载读的是刚写好的文件。
        // 两个都算上了才算成功，任何一个没能即时生效都要退回「提示重启」。
        boolean verityOk = VerityConfigReloader.reloadVerityConfig(configFile());
        boolean translateOk = TranslateConfigReloader.reloadTranslateConfig(
                translateConfig(), snapshot.apiKey);
        return verityOk && translateOk;
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
            JsonObject json = readTranslateJson(file);
            applyTranslateSettings(json, provider, apiKey, endpoint, model);
            writeTranslateJson(file, json);
        } catch (Exception e) {
            Log.error("无法写入 simple_translate 配置: " + e.getMessage());
        }
    }

    /**
     * 读 simple_translate 配置。
     * <p>内容损坏时退回空对象，而不是直接放弃：这个文件是 Verity 要用的联动配置，
     * 保留住其它键的机会比「因为解析失败就整份不写」更有价值。
     */
    private static JsonObject readTranslateJson(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8).trim();
        JsonElement root;
        try {
            root = JsonParser.parseString(content);
        } catch (Exception e) {
            root = new JsonObject();
        }
        if (root == null || !root.isJsonObject()) root = new JsonObject();
        return root.getAsJsonObject();
    }

    /** 把 Verity 侧的连接参数写进简单翻译的 JSON 对象（不落盘）。 */
    private static void applyTranslateSettings(JsonObject json, String provider,
                                               String apiKey, String endpoint, String model) {
        json.addProperty("api.apiKey", apiKey);
        json.addProperty("api.model", model);
        json.addProperty("api.apiUrl", getChatCompletionsUrl(provider, endpoint));
        json.addProperty("api.format", getApiFormatForProvider(provider));
        json.addProperty("shortcuts.translateGui", "keyboard:262:0"); // 保留原键位
    }

    /** 写回 simple_translate 配置。 */
    private static void writeTranslateJson(Path file, JsonObject json) throws IOException {
        // 必须显式指定 UTF-8：原本用 FileWriter，走的是平台默认编码
        // （中文 Windows 上是 GBK），而读取那边用的是 UTF-8，一旦文件里
        // 出现中文就会被写成另一种编码。
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        }
    }

    // ========== 启动时自检：简单翻译的 Key 为空但 Verity 有 ==========
    /** 读取 simple_translate 配置里的 {@code api.apiKey}；文件缺失或解析失败时返回空串。 */
    private static String readTranslateApiKey() {
        Path file = translateConfig();
        if (!Files.exists(file)) return "";
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8).trim());
            if (root == null || !root.isJsonObject()) return "";
            JsonElement key = root.getAsJsonObject().get("api.apiKey");
            return key == null || key.isJsonNull() ? "" : key.getAsString();
        } catch (Exception e) {
            Log.warn("读取 simple_translate 的 api.apiKey 失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 简单翻译的配置不可用时，把 Verity 的配置重新灌进去。
     * <p>参与判断的是两种情况：{@code api.apiKey} 为空，或者整个
     * {@code simple_translate-client.json} 不存在。两份配置是联动的：
     * 在界面上填 Key 会同时写两边，但玩家手动删掉了 Key 或整个文件、
     * 或者该文件是旧版留下的，就会出现「Verity 能用、简单翻译不能用」。
     * <p>文件不存在时先从内置模板铺一份再写，而不是凭空造一个只有几个键的
     * JSON —— 模板里有几十项界面与功能开关，缺了它们简单翻译会以默认值运行。
     * <p>要在 {@link #preload()} 之后调用；返回 {@code true} 表示确实改写过文件，
     * 调用方应当提示玩家重启游戏才能生效。
     */
    public static boolean resyncTranslateIfKeyMissing() {
        ConfigData data = loadVerityConfig();
        if (data.apiKey.trim().isEmpty()) return false;

        Path file = translateConfig();
        boolean fileMissing = !Files.exists(file);
        // 文件在的话，已经有 Key 就不必动它
        if (!fileMissing && !readTranslateApiKey().trim().isEmpty()) return false;

        // 直接落盘（同步），保证提示出现时文件已经是修好的状态
        try {
            if (fileMissing) {
                // 先铺模板；copyTemplate 不覆盖已有文件，这里目标本就缺失
                copyTemplate(TEMPLATE_TRANSLATE, file);
                if (!Files.exists(file)) {
                    Log.warn("简单翻译配置缺失且内置模板不可用，跳过补写: " + file);
                    return false;
                }
            }
            JsonObject json = readTranslateJson(file);
            applyTranslateSettings(json, getCurrentProvider(), data.apiKey, data.endpoint, data.model);
            writeTranslateJson(file, json);
            Log.info((fileMissing ? "简单翻译配置缺失，已用模板重建并写入 Verity 配置: "
                    : "简单翻译的 API Key 为空，已按 Verity 配置补写: ") + file);
            return true;
        } catch (Exception e) {
            Log.error("补写 simple_translate 配置失败: " + e.getMessage());
            return false;
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
