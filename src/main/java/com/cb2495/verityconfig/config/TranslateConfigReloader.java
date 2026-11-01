package com.cb2495.verityconfig.config;

import com.cb2495.verityconfig.util.Log;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 让简单翻译模组立刻重新读取 {@code simple_translate-client.json}，无需重启游戏。
 * <p>简单翻译没有用 Forge 的配置系统，而是自己写了一套 JSON 读写器
 * （{@code com.yourname.simpletranslate.config.ModConfig}），既不挂文件监视器、
 * 也不发 {@code ModConfigEvent}。它只提供了一个公开的静态入口：
 * <pre>{@code
 * public static synchronized void load()
 * }</pre>
 * 这个方法会重新读盘、覆盖内存里的全部配置值，正是这里需要的。
 * <p><b>为什么用反射而不是直接 import：</b>那是另一个模组的内部实现，
 * 不应该成为本模组的编译期依赖；而且它的包名还带着占位符 {@code yourname}，
 * 随时可能改名。用一个延迟解析的反射调用，类缺失时最多是重载失效，
 * 不会让本模组加载失败。
 * <p>失败一律返回 {@code false}，调用方据此退回「提示玩家重启游戏」——
 * 最坏情况与没有这个功能时相同。
 */
final class TranslateConfigReloader {

    private TranslateConfigReloader() {}

    /** 简单翻译的配置类；不存在时说明没有安装该模组。 */
    private static final String CONFIG_CLASS = "com.yourname.simpletranslate.config.ModConfig";
    /** 简单翻译的 modId；用于在重载前判断它有没有被加载。 */
    private static final String MOD_ID = "simple_translate";

    /**
     * 让简单翻译重新读取配置文件。
     * <p>简单翻译没装或被禁用时直接算成功：没有它就没有「需要重启才生效」的配置，
     * 不该因此拦住玩家去重启游戏。
     *
     * @param configFile     刚写好的配置文件
     * @param expectedApiKey 刚写进去的 Key；用于确认重载真的把值读进去了
     * @return {@code true} 表示已生效（含未安装）；{@code false} 表示装了但没能重载，
     *         调用方应当退回「提示玩家重启游戏」
     */
    static boolean reloadTranslateConfig(Path configFile, String expectedApiKey) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            // 没装/被禁用：没有需要生效的东西，不算失败
            Log.info("简单翻译未加载，跳过重载");
            return true;
        }
        try {
            if (!Files.exists(configFile)) {
                // 文件不存在时它自己的 load() 会写一份默认值，那不是我们想要的时机
                Log.warn("重载跳过：简单翻译配置不存在 " + configFile);
                return false;
            }
            Class<?> configClass = Class.forName(CONFIG_CLASS);
            Method load = configClass.getMethod("load");
            load.invoke(null);
            // load() 在未初始化时会直接返回、什么也不做，此时报成功会误导调用方。
            // 读一次它的 apiKey 字段，和刚写进去的值比对，才能真正确认重载生效。
            if (!apiKeyMatches(configClass, expectedApiKey)) {
                Log.warn("简单翻译未接受新配置（可能尚未初始化），建议重启");
                return false;
            }
            Log.info("已重载简单翻译配置：" + configFile.getFileName());
            return true;
        } catch (ClassNotFoundException e) {
            Log.warn("未找到简单翻译的配置类，跳过重载（可能未安装该模组）");
            return false;
        } catch (NoSuchMethodException e) {
            // 作者改了方法名：属于可预期的情况，只警告不报错
            Log.warn("简单翻译的 ModConfig.load() 不存在，跳过重载");
            return false;
        } catch (Exception e) {
            // 反射调用本身抛出的异常（被调方法内部出错）都包在 InvocationTargetException 里
            Log.error("重载简单翻译配置失败: " + e.getMessage());
            return false;
        }
    }

    /** 检查简单翻译内存里的 apiKey 是否已是期望值。 */
    private static boolean apiKeyMatches(Class<?> configClass, String expected) {
        try {
            Field field = configClass.getField("DEEPSEEK_API_KEY");
            Object value = field.get(null);
            if (value == null) return false;
            Method get = value.getClass().getMethod("get");
            Object actual = get.invoke(value);
            return expected.equals(actual);
        } catch (Exception e) {
            // 拿不到就不下结论，交给调用方按失败处理
            Log.warn("无法读取简单翻译的 apiKey 以校验重载结果: " + e.getMessage());
            return false;
        }
    }
}
