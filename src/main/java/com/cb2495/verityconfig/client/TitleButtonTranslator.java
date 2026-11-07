package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.util.Log;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 把主界面上的「Mod by Varmite」按钮拆成两枚：作者署名 + 汉化署名。
 *
 * <p><b>为什么要拆。</b>原按钮是 Verity 的版权署名，点击跳转作者的 YouTube 频道。
 * 汉化版之前在它后面追加了「翻译 CB2495」，于是整枚按钮承担了两个身份，
 * 点击却只通向作者——汉化者没有自己的入口。拆成两枚之后，各自跳各自的主页。
 *
 * <p><b>尺寸怎么分。</b>原按钮固定在 {@code x = width/2 - 100, y = height/4 + 48 + 72,
 * 宽 200, 高 20}（见 Verity 的 {@code TitleScreenMixin.addVarmiteButton}）。
 * 拆成两枚各占一半，中间留 4 像素间隔：
 * <pre>
 * 左：[width/2-100, width/2-2)          右：[width/2+2, width/2+100)
 * </pre>
 * 两枚合计仍然占满原来那 200 像素的横向范围。
 *
 * <p><b>为什么不走 {@link VerityTranslator} 的翻译表：</b>那个钩子换的是
 * {@code Component#getString()}，只对聊天消息有效。按钮绘制走的是
 * {@code AbstractWidget#renderScrollingString}，拿到 {@code Component} 后直接按
 * {@code FormattedCharSequence} 分段画，<b>全程不调用 {@code getString()}</b>。
 *
 * <p><b>左半边只改文字和宽度</b>，点击行为完全不动，所以就算 Verity 哪天改了链接，
 * 作者那一半仍然正确。<b>右半边是我们自己 new 的按钮。</b>
 */
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public final class TitleButtonTranslator {

    private TitleButtonTranslator() {}

    /** 原文，用于认出那个按钮。 */
    private static final String EN_SIGNATURE = "Mod by Varmite";
    /** 左半边的新文字：只剩作者署名。 */
    private static final String ZH_AUTHOR = "制作 Varmite";
    /** 右半边的新文字：汉化署名。 */
    private static final String ZH_TRANSLATOR = "CB2495 汉化";

    /** 汉化者的主页。 */
    private static final String TRANSLATOR_URL = "https://v.douyin.com/dPBYSn3elb0/";

    /** 两枚按钮之间的间隔。 */
    private static final int GAP = 4;

    /**
     * 原署名按钮的原始宽度。
     *
     * <p>拆完之后左按钮自己只剩一半，如果每帧都拿它的宽度去算「一半的一半」，
     * 会一路缩到看不见。所以第一次见到时记下真实宽度，之后都用这个值。
     * <p>用 {@code Integer} 而不是 {@code int}：{@code null} 表示「这个界面还没见过」。
     */
    private static Integer originalWidth;

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
            // 界面重建时 Verity 会重新 new 一个完整宽度的按钮，这里跟着重置记忆
            originalWidth = null;
            splitSignature(screen);
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    /**
     * 绘制前再扫一遍。
     * <p><b>为什么光靠 Init 不够：</b>Verity 用 Mixin 注入 {@code init()} 的末尾来添加这枚按钮，
     * 而 Forge 的 {@code ScreenEvent.Init.Post} 与它谁先执行取决于两者的注入顺序，
     * 实测我们的替换会被它之后创建的按钮盖回去。这里在每帧绘制前补一次。
     * <p>只在标题界面跑，两枚按钮都就位后就不再改动，开销可以忽略。
     */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof TitleScreen)) return;
        try {
            splitSignature(screen);
        } catch (RuntimeException e) {
            warn(e);
        }
    }

    private static void warn(RuntimeException e) {
        if (warned) return;
        warned = true;
        Log.warn("拆分主界面署名按钮时出错: " + e);
    }

    /** 遍历控件，把署名按钮拆成两枚；返回这一轮做了改动的数量。 */
    private static int splitSignature(Screen screen) {
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

        // 先找原按钮：可能还是英文原文，也可能是上一轮已经改过的左半边
        Button author = null;
        boolean translatorPresent = false;
        for (GuiEventListener child : children) {
            if (!(child instanceof Button button)) continue;
            String text = button.getMessage().getString();
            if (EN_SIGNATURE.equals(text) || ZH_AUTHOR.equals(text)) {
                author = button;
            } else if (ZH_TRANSLATOR.equals(text)) {
                translatorPresent = true;
            }
        }
        if (author == null) return 0;

        int changed = 0;

        // 原按钮的完整宽度：一旦拆过，左按钮自身的宽度就只剩一半，
        // 所以这里不能拿 author.getWidth() 反复算（会越算越窄），要记住最初那 200。
        Integer worked = originalWidth;
        if (worked == null || worked <= 0) {
            worked = author.getWidth();
            originalWidth = worked;
        }
        int fullX = author.getX();
        int halfWidth = (worked - GAP) / 2;

        // 右半边先挂上去——挂成功了才动左半边。
        //
        // 顺序不能反：如果先把左按钮压成一半、右按钮却因为反射失败没挂上，
        // 玩家看到的就是一枚孤零零的窄按钮，文字被挤在中间，右边空一大块。
        // （上一版就是这样，日志里一串 NoSuchFieldException: children。）
        if (!translatorPresent) {
            Button translator = Button.builder(
                            Component.literal(ZH_TRANSLATOR).withStyle(ChatFormatting.BOLD),
                            b -> Util.getPlatform().openUri(TRANSLATOR_URL))
                    .bounds(fullX + halfWidth + GAP, author.getY(), halfWidth, author.getHeight())
                    .build();
            if (!addWidget(screen, translator)) {
                // 加不上就原样保留：宁可看到一根完整长度的英文按钮，也不要半个空的
                return changed;
            }
            changed++;
            if (!logged) {
                logged = true;
                Log.info("已把主界面的署名按钮拆成「" + ZH_AUTHOR + "」与「" + ZH_TRANSLATOR + "」");
            }
        }

        // 左半边：换文字、缩宽度。点击行为原样保留，不动它的回调。
        if (!ZH_AUTHOR.equals(author.getMessage().getString())
                || author.getWidth() != halfWidth) {
            author.setMessage(Component.literal(ZH_AUTHOR).withStyle(ChatFormatting.BOLD));
            author.setWidth(halfWidth);
            changed++;
        }

        return changed;
    }

    /**
     * 把控件挂到界面上。
     *
     * <p>{@link Screen} 的 {@code addRenderableWidget} / {@code addWidget} 都是
     * {@code protected}，外部类调不到，所以这里按它内部的顺序自己写一遍：
     * <ol>
     *   <li>{@code renderables}——绘制走的是这个列表；</li>
     *   <li>{@code children}——鼠标拾取、焦点、键盘事件走这个；</li>
     *   <li>{@code narratables}——朗读（无障碍）走这个。</li>
     * </ol>
     * 三个都填上，新按钮才会既画得出来、点得动、读屏也读得到。
     *
     * <p><b>字段名要两套都试。</b>Minecraft 的类在两个环境下名字不一样：
     * <ul>
     *   <li>开发环境（{@code runClient}）经 {@code fg.deobf} 重映射，是 official 名
     *       ——{@code children}、{@code narratables}、{@code renderables}；</li>
     *   <li>正式发行时游戏里跑的是 SRG 名——{@code f_96540_}、{@code f_169368_}、
     *       {@code f_169369_}。</li>
     * </ul>
     * 只写一套的话，另一套环境下就是 {@code NoSuchFieldException}，
     * 按钮干脆加不进去（既不显示也点不动）。所以这里按「official 名 → SRG 名」的顺序试。
     * 注意 {@code renderables}（{@code f_169369_}）是 public 的，直接访问即可。
     */
    @SuppressWarnings("unchecked")
    private static boolean addWidget(Screen screen, Button widget) {
        try {
            ((List<Object>) list(screen, "renderables", "f_169369_")).add(widget);
            ((List<Object>) list(screen, "children", "f_96540_")).add(widget);
            ((List<Object>) list(screen, "narratables", "f_169368_")).add(widget);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.warn("往主界面添加汉化署名按钮失败: " + e);
            return false;
        }
    }

    /** 缓存反射到的字段，避免每帧都去 {@code getDeclaredField}。 */
    private static final java.util.Map<String, Field> FIELDS = new java.util.HashMap<>();

    /**
     * 按「official 名 → SRG 名」的顺序找字段，返回它的值。
     *
     * @param official 开发环境下的字段名
     * @param srg      正式发行时游戏里跑的字段名
     */
    private static Object list(Screen screen, String official, String srg)
            throws ReflectiveOperationException {
        Field field = field(official, srg);
        return field.get(screen);
    }

    /** 取 {@link Screen} 的字段，两种命名都试，取到就缓存。 */
    private static Field field(String official, String srg) throws NoSuchFieldException {
        String cacheKey = official + "|" + srg;
        Field cached = FIELDS.get(cacheKey);
        if (cached != null) return cached;

        Field field;
        try {
            field = Screen.class.getDeclaredField(official);
        } catch (NoSuchFieldException notOfficial) {
            // 正式发行环境：只有 SRG 名
            field = Screen.class.getDeclaredField(srg);
        }
        field.setAccessible(true);
        FIELDS.put(cacheKey, field);
        return field;
    }
}
