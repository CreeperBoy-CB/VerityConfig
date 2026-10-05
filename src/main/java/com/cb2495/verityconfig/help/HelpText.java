package com.cb2495.verityconfig.help;

import com.cb2495.verityconfig.util.Log;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * 帮助文档 / 常见问题文本里的行内标记解析。
 * <p>支持三种标记：
 * <ul>
 *   <li>{@code /warn/文字/warn/} —— 红色告警</li>
 *   <li>{@code /hint/文字/hint/} —— 黄色提示</li>
 *   <li>{@code /url/显示文字|https://.../url/} —— 可点击链接</li>
 * </ul>
 * <p>这段逻辑原先在 {@code HelperScreen} 与 {@code QandAScreen} 里各有一份，
 * 而 Q&A 那份没有实现 {@code /url/} —— 也就是说往 {@code qanda.txt} 里写链接标记
 * 会被当成普通文字原样显示。现在两处共用同一份实现。
 */
public final class HelpText {

    private HelpText() {}

    private static final String WARN_TAG = "/warn/";
    private static final String HINT_TAG = "/hint/";
    private static final String URL_TAG = "/url/";

    /**
     * 解析一行文本里的标记。
     * <p>不带标记的普通文字使用 {@code baseStyle}；若 {@code baseStyle} 没有指定颜色，
     * 调用方 {@code drawString} 传入的颜色才会生效——Q&A 就是靠这一点让正文保持灰色，
     * 所以那里应传 {@link Style#EMPTY}。
     */
    public static Component parse(String text, Style baseStyle) {
        MutableComponent result = Component.literal("");
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith(WARN_TAG, i)) {
                int end = text.indexOf(WARN_TAG, i + WARN_TAG.length());
                if (end != -1) {
                    result.append(Component.literal(text.substring(i + WARN_TAG.length(), end))
                            .setStyle(baseStyle.withColor(ChatFormatting.RED)));
                    i = end + WARN_TAG.length();
                    continue;
                }
            }
            if (text.startsWith(HINT_TAG, i)) {
                int end = text.indexOf(HINT_TAG, i + HINT_TAG.length());
                if (end != -1) {
                    result.append(Component.literal(text.substring(i + HINT_TAG.length(), end))
                            .setStyle(baseStyle.withColor(ChatFormatting.YELLOW)));
                    i = end + HINT_TAG.length();
                    continue;
                }
            }
            if (text.startsWith(URL_TAG, i)) {
                int end = text.indexOf(URL_TAG, i + URL_TAG.length());
                if (end != -1) {
                    int close = end + URL_TAG.length();
                    String body = text.substring(i + URL_TAG.length(), end);
                    int bar = body.indexOf('|');
                    Component link = bar == -1
                            ? null
                            : buildUrlLink(body.substring(0, bar), body.substring(bar + 1), baseStyle);
                    if (link != null) {
                        result.append(link);
                    } else {
                        // 写法不合法（缺 | 或网址不是 http/https）：整段连同标记原样当普通文字输出
                        result.append(Component.literal(text.substring(i, close)).setStyle(baseStyle));
                    }
                    i = close;
                    continue;
                }
            }

            // 普通文字：一直取到下一个标记之前。
            // 必须从 i+1 开始找：若标记就在当前位置却没被上面的分支接受
            // （例如缺少闭合标记的 "/warn/"），从 i 开始找会得到 next == i，
            // 于是 substring(i, i) 为空、i 原地不动，这里就变成死循环 —— 整个
            // 界面会卡在 loadText() 里再也出不来。
            int next = nextTagIndex(text, i + 1);
            result.append(Component.literal(text.substring(i, next)).setStyle(baseStyle));
            i = next;
        }
        return result;
    }

    /** 从 {@code from} 起第一个标记的位置；一个都没有时返回文本长度。 */
    private static int nextTagIndex(String text, int from) {
        int next = text.length();
        for (String tag : new String[]{WARN_TAG, HINT_TAG, URL_TAG}) {
            int at = text.indexOf(tag, from);
            if (at != -1 && at < next) next = at;
        }
        return next;
    }

    /**
     * 构造一个可点击的网址链接组件。
     * <p>网址不合法时返回 {@code null}，由调用方退化为普通文本。
     */
    private static Component buildUrlLink(String label, String url, Style baseStyle) {
        String trimmedUrl = url.trim();
        if (!isHttpUrl(trimmedUrl)) {
            Log.warn("帮助文档中的链接格式不正确，已忽略: " + url);
            return null;
        }
        String text = label.trim();
        if (text.isEmpty()) text = trimmedUrl;

        return Component.literal(text).setStyle(baseStyle
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, trimmedUrl))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("点击打开：").withStyle(ChatFormatting.WHITE)
                                .append(Component.literal(trimmedUrl).withStyle(ChatFormatting.GRAY)))));
    }

    /** 只接受 http/https，避免把本地文件路径之类的字符串当链接打开。 */
    private static boolean isHttpUrl(String url) {
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
