package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.util.Log;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 把主界面上的「Mod by Varmite」按钮换成中文。
 * <p><b>为什么不走 {@link VerityTranslator} 的翻译表：</b>那个钩子换的是
 * {@code Component#getString()}，只对聊天消息有效。按钮绘制走的是
 * {@code AbstractWidget#renderScrollingString}，它拿到 {@code Component} 后
 * 直接按 {@code FormattedCharSequence} 分段画，<b>全程不调用 {@code getString()}</b>，
 * 所以翻译表对按钮文字不起作用。
 * <p><b>做法：</b>界面初始化之后，把按钮手上的 {@code Component} 整个换成中文。
 * {@code setMessage} 是 {@code AbstractWidget} 的公开方法，改动只影响显示，点击行为不变。
 * <p>也正因为换的是对象，译文<b>不受长度限制</b>——原版按钮超宽时会自动滚动。
 */
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public final class TitleButtonTranslator {

    private TitleButtonTranslator() {}

    /** 原文，用于认出那个按钮。 */
    private static final String EN_SIGNATURE = "Mod by Varmite";
    /** 译文，取自 hardcode_ts.txt 的 TitleScreenMixin.java 一节。 */
    private static final String ZH_SIGNATURE = "制作 Varmite | 翻译 CB2495";

    private static boolean warned;
    private static boolean logged;
    /** 控件清单只在第一次扫描时打一次。 */
    private static boolean dumped;

    /**
     * 界面初始化完成后扫一遍它的子控件。
     * <p>主界面每次打开（包括从游戏里返回）都会重新 init，
     * 所以这个钩子每次都能重新接上。
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof TitleScreen)) return;
        try {
            translateButtons(screen);
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    /**
     * 绘制前再扫一遍。
     * <p><b>为什么光靠 Init 不够：</b>Verity 用 Mixin 注入 {@code init()} 的末尾来添加这枚按钮，
     * 而 Forge 的 {@code ScreenEvent.Init.Post} 与它谁先执行取决于两者的注入顺序，
     * 实测我们的替换会被它之后创建的按钮盖回去。这里在每帧绘制前补一次，
     * 无论哪一方后创建，画出来的都是中文。
     * <p>只在标题界面跑，且一旦认出来就不再重复扫描，开销可以忽略。
     */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof TitleScreen)) return;
        try {
            translateButtons(screen);
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    private static void warn(RuntimeException e) {
        if (warned) return;
        warned = true;
        Log.warn("替换主界面署名按钮时出错: " + e);
    }

    /** 遍历控件，文字对得上的就换掉，返回换了几个。 */
    private static int translateButtons(Screen screen) {
        List<? extends GuiEventListener> children = screen.children();
        if (children == null || children.isEmpty()) return 0;

        // 首次扫描时把控件文字记下来，确认这枚按钮到底在不在里面
        if (!dumped) {
            dumped = true;
            StringBuilder sb = new StringBuilder("主界面控件文字: ");
            for (GuiEventListener c : children) {
                if (c instanceof AbstractWidget w) {
                    sb.append('[').append(w.getMessage().getString()).append(']');
                }
            }
            Log.info(sb.toString());
        }

        int changed = 0;
        for (GuiEventListener child : children) {
            if (!(child instanceof AbstractWidget widget)) continue;

            // 翻译表里没有这条，getString() 拿到的就是原文
            String text = widget.getMessage().getString();
            if (ZH_SIGNATURE.equals(text)) continue;
            if (!EN_SIGNATURE.equals(text)) continue;

            widget.setMessage(Component.literal(ZH_SIGNATURE));
            changed++;
            if (!logged) {
                logged = true;
                Log.info("已把主界面的署名按钮换成中文");
            }
        }
        return changed;
    }
}
