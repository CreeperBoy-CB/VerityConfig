package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 把主界面最下面那排按钮（语言、选项、退出游戏、辅助功能）整体下移。
 *
 * <p><b>原版坐标。</b>看 {@code TitleScreen.init()}：
 * <pre>
 * 底部一排的 y = height/4 + 48 + 72 + 12
 *   语言     x = width/2 - 124, 宽 20
 *   选项     x = width/2 - 100, 宽 98
 *   退出游戏 x = width/2 + 2,   宽 98
 *   辅助功能 x = width/2 + 104, 宽 20
 * </pre>
 * 四个按钮的 y 完全相同，所以只要认出这一排、统一往下挪即可。
 *
 * <p><b>怎么认。</b>不靠文字：这四项的本地化键是 {@code narrator.button.language}、
 * {@code menu.options}、{@code menu.quit}、{@code narrator.button.accessibility}，
 * 而在中文环境下 {@code getString()} 返回的都是译文，认起来又脆又绕。
 * 改用<b>几何判据</b>——原版这一排的 y 恰好是 {@code height/4 + 132}。
 * 只挪 y 等于这个值的控件，主菜单中间那一堆按钮（y 从 {@code height/4+48} 起）
 * 不会被误伤。
 *
 * <p><b>为什么要用几何而不是"最底下那几个"：</b>窗口很矮的时候，主按钮组可能一直排到
 * 底部以后，单纯"取最下面四个"会挪错东西。认准原始 y 值最稳。
 */
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public final class TitleBottomRowShift {

    private TitleBottomRowShift() {}

    /**
     * 下移量（像素）。
     *
     * <p><b>12 这个数字是有来由的。</b>Verity 自己的署名按钮（「Mod by Varmite」）
     * 也是挂在主界面上的，它的 y 是 {@code height/4 + 48 + 72 = height/4 + 120}，
     * 高 20，占 {@code 120..140}；而原版底排的 y 是 {@code height/4 + 132}，
     * 占 {@code 132..152}。<b>两者本来就有 8 像素的重叠</b>。
     * <p>下移 12 之后，底排变成 {@code 144..164}，与署名按钮彻底分开；
     * 12 &gt; 8，正是「刚好够」的那个值，再多就是纯粹把底排往屏幕边缘推。
     */
    private static final int SHIFT = 12;

    /**
     * 界面初始化后把这一排挪下去。
     * <p>主界面每次打开都会重新 {@code init()}，坐标也会被重新设成原版值，
     * 所以这里每次都重算一遍，不需要记状态。
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof TitleScreen)) return;
        shift(screen);
    }

    /** 把 y 等于原版底部一排的控件整体下移。 */
    private static void shift(Screen screen) {
        List<? extends GuiEventListener> children = screen.children();
        if (children == null || children.isEmpty()) return;

        // 原版这一排的 y：height/4 + 48 + 72 + 12
        int rowY = screen.height / 4 + 48 + 72 + 12;

        for (GuiEventListener child : children) {
            if (!(child instanceof AbstractWidget widget)) continue;
            // 只认正好在原版这一排上的：别的按钮（主菜单中间那组）y 值不同，不会被挪
            if (widget.getY() != rowY) continue;
            widget.setY(widget.getY() + SHIFT);
        }
    }
}
