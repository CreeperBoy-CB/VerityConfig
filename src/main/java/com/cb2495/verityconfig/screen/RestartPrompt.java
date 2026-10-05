package com.cb2495.verityconfig.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * 「配置已保存，是否重启游戏？」确认框。
 * <p>这段代码原先在 {@code VerityConfigScreen}、{@code ModsListScreen} 和
 * {@code ModGuideReminderScreen} 里各写了一份，内容完全一致。
 */
public final class RestartPrompt {

    private RestartPrompt() {}

    /**
     * 弹出重启确认框。
     * <p>确认则直接结束游戏进程；取消时：在存档里就回到游戏，否则回主界面。
     */
    public static void show() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                mc.stop();
            } else if (mc.player != null) {
                mc.setScreen(null);
            } else {
                mc.setScreen(new TitleScreen());
            }
        },
                Component.literal("重启游戏以应用配置"),
                Component.literal("配置已保存，是否重启游戏？")));
    }
}
