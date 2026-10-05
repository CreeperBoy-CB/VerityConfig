package com.cb2495.verityconfig.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供商注册表：端点、对话补全地址、API 格式、Key 获取地址与可选模型。
 * <p>这些信息原本分在两处维护：{@code VerityConfigScreen} 里有一份「提供商 →
 * 模型 / Key 地址」，{@code VerityConfigManager} 里另有一份「提供商 → 端点 /
 * API 格式」。两边各写一遍提供商清单，往一边加提供商而忘了另一边，会以
 * 「下拉框里没有它」或者「Base URL 被清空」这种静默方式出错。
 * <p>这里合并成唯一来源，两边的读取接口都改成查这张表。
 */
public final class Providers {

    private Providers() {}

    /** 一个可选模型。 */
    public record Model(String name, String desc) {}

    /**
     * 一个提供商。
     *
     * @param name      显示名，同时也是各处的键
     * @param endpoint  默认 Base URL
     * @param chatUrl   对话补全的固定地址；为空表示由用户填的 Base URL 推导（自定义提供商）
     * @param apiFormat Verity 侧的 API 格式标识
     * @param keyUrl    申请 API Key 的页面；为空表示没有预设链接
     * @param models    可选模型；空列表表示需要用户自己填
     */
    public record Provider(String name, String endpoint, String chatUrl,
                           String apiFormat, String keyUrl, List<Model> models) {}

    /** 「自定义」的固定名字：多处逻辑依赖这个名字做判断。 */
    public static final String CUSTOM = "自定义";

    private static final Map<String, Provider> REGISTRY = new LinkedHashMap<>();

    private static void put(Provider provider) {
        REGISTRY.put(provider.name(), provider);
    }

    static {
        put(new Provider("DeepSeek",
                "https://api.deepseek.com/v1",
                "https://api.deepseek.com/chat/completions",
                "DEEPSEEK_CHAT",
                "https://platform.deepseek.com/api_keys",
                List.of(
                        new Model("deepseek-flash", "低价、快速"),
                        new Model("deepseek-v4-pro", "深度思考，旗舰"))));

        put(new Provider("智谱 (GLM)",
                "https://open.bigmodel.cn/api/paas/v4",
                "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                "OPENAI_CHAT_COMPAT",
                "https://open.bigmodel.cn/console/overview",
                List.of(
                        new Model("glm-4-flash", "免费、文本生成"),
                        new Model("glm-4.7-flash", "免费、深度思考"),
                        new Model("glm-4-flash-250414", "免费、文本生成"),
                        new Model("glm-5.3-flash", "低价、深度思考"),
                        new Model("glm-4.7-flash-x", "付费、深度思考"),
                        new Model("glm-4.7", "付费、深度思考"),
                        new Model("glm-4.5-air", "付费、深度思考"),
                        new Model("glm-4.6", "付费、深度思考"),
                        new Model("glm-5.2", "付费、深度思考"),
                        new Model("glm-5.3", "付费、旗舰"))));

        put(new Provider("阿里云百炼 (通义千问)",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                "OPENAI_CHAT_COMPAT",
                "https://bailian.console.aliyun.com/?apiKey=1",
                List.of(
                        new Model("qwen3.6-flash", "低成本、轻量极速"),
                        new Model("qwen3-turbo", "低成本、轻量极速"),
                        new Model("qwen3.7-plus", "付费、均衡主力"),
                        new Model("qwen3.8-max", "付费、旗舰"),
                        new Model("qwen3.7-max", "付费、旗舰"))));

        put(new Provider("Kimi",
                "https://api.moonshot.cn/v1",
                "https://api.moonshot.cn/v1/chat/completions",
                "OPENAI_CHAT_COMPAT",
                "https://platform.moonshot.cn/console/api-keys",
                List.of(
                        new Model("kimi-k2.6", "付费、思考/非思考"),
                        new Model("kimi-k2.5", "付费、思考/非思考"),
                        new Model("kimi-k3", "付费、旗舰"),
                        new Model("moonshot-v1-128k", "付费、纯文本"),
                        new Model("moonshot-v1-32k", "付费、纯文本"),
                        new Model("moonshot-v1-8k", "付费、纯文本"))));

        put(new Provider(CUSTOM, "", "", "OPENAI_CHAT_COMPAT", "", List.of()));
    }

    /** 全部提供商显示名，顺序与注册顺序一致。 */
    public static List<String> names() {
        return new ArrayList<>(REGISTRY.keySet());
    }

    /** 全部提供商；下拉框直接用它渲染。 */
    public static List<Provider> all() {
        return new ArrayList<>(REGISTRY.values());
    }

    public static Provider byName(String name) {
        return REGISTRY.get(name);
    }

    /** 该提供商的可选模型；未知提供商或自定义返回空列表。 */
    public static List<Model> modelsFor(String name) {
        Provider provider = REGISTRY.get(name);
        return provider == null ? Collections.emptyList() : provider.models();
    }

    public static String endpointFor(String name) {
        Provider provider = REGISTRY.get(name);
        return provider == null ? "" : provider.endpoint();
    }

    public static String keyUrlFor(String name) {
        Provider provider = REGISTRY.get(name);
        return provider == null ? "" : provider.keyUrl();
    }

    public static String apiFormatFor(String name) {
        Provider provider = REGISTRY.get(name);
        return provider == null ? "OPENAI_CHAT_COMPAT" : provider.apiFormat();
    }

    /**
     * 对话补全的完整地址。
     * <p>预定义提供商直接用注册表里的固定地址（即使 Base URL 被改过也保持原逻辑）；
     * 自定义提供商则从用户填的 Base URL 推导。
     */
    public static String chatUrlFor(String name, String baseUrl) {
        Provider provider = REGISTRY.get(name);
        if (provider != null && !provider.chatUrl().isEmpty()) {
            return provider.chatUrl();
        }
        if (baseUrl.endsWith("/chat/completions")) return baseUrl;
        if (baseUrl.endsWith("/")) return baseUrl + "chat/completions";
        return baseUrl + "/chat/completions";
    }

    /** 由 Base URL 反查提供商；没匹配上任何预定义端点时算「自定义」。 */
    public static String inferFromEndpoint(String endpoint, String fallback) {
        if (endpoint == null || endpoint.isEmpty()) return fallback;
        for (Provider provider : REGISTRY.values()) {
            if (endpoint.equals(provider.endpoint())) {
                return provider.name();
            }
        }
        return CUSTOM;
    }
}
