package com.cb2495.verityconfig.util;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 统一的日志出口。
 * <p>原本各处混用 {@code System.out} / {@code System.err} / {@code printStackTrace}：
 * 这些输出绕过了 Forge 使用的 SLF4J，既不受日志级别控制，也不会写进日志文件，
 * 玩家反馈问题时经常在日志里找不到线索。
 * <p>logger 名字由 {@link LogUtils#getLogger()} 按调用类生成，所以消息里不必再带
 * 「[VerityConfig]」前缀。
 * <p>用法上只推荐两种形式，避免 SLF4J 的占位符歧义：
 * <ul>
 *   <li>{@code Log.error("读取失败: " + file, e)} —— 带异常的固定消息</li>
 *   <li>{@code Log.warn("读取 {} 失败", file)} —— 带占位符、不带异常</li>
 * </ul>
 * 不要写「既带占位符又把异常放在最后」的形式，那种写法占位符是否被替换取决于
 * 参数个数，很容易踩坑。
 */
public final class Log {

    private Log() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void info(String format, Object... args) {
        LOGGER.info(format, args);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void warn(String format, Object... args) {
        LOGGER.warn(format, args);
    }

    public static void error(String message) {
        LOGGER.error(message);
    }

    public static void error(String format, Object... args) {
        LOGGER.error(format, args);
    }

    /** 带异常的 error；消息里不要再放占位符。 */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    /** 带异常的 warn；消息里不要再放占位符。 */
    public static void warn(String message, Throwable throwable) {
        LOGGER.warn(message, throwable);
    }

    /** 是否输出调试日志（由日志级别决定）。 */
    public static boolean isDebugEnabled() {
        return LOGGER.isDebugEnabled();
    }

    public static void debug(String message) {
        LOGGER.debug(message);
    }

    public static void debug(String format, Object... args) {
        LOGGER.debug(format, args);
    }
}
