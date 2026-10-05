package com.cb2495.verityconfig.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * 聊天栏里的「可点击填入指令」文本。
 * <p>这段构造原先在 {@code ClientCommands}、{@code ErrorChatHandler}、
 * {@code DeepSeekHintHandler} 里各写了一份，三处完全一样。
 */
public final class ChatLinks {

    private ChatLinks() {}

    /** 默认的悬停提示。 */
    public static final String CLICK_TO_FILL = "点击填入命令";

    /**
     * 一段青色文字，点击后把 {@code command} 填进输入框（不直接执行）。
     *
     * @param hover 悬停时显示的说明
     */
    public static Component suggestCmd(String command, String hover) {
        return Component.literal(command)
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover)))
                );
    }

    /** 指令列表里的一行：可点击的指令 + 灰色说明。 */
    public static Component suggestLine(String command, String description) {
        return suggestCmd(command, CLICK_TO_FILL)
                .copy()
                .append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY));
    }

    /** 一段普通文字，前面挂上模组名前缀（黄色），正文自定颜色。 */
    public static Component prefixed(String text, ChatFormatting textColor) {
        return Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(text).withStyle(textColor));
    }
}
