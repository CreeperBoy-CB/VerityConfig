package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.PlatformUtils;
import com.cb2495.verityconfig.util.ScrollableArea;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModsListScreen extends Screen {

    // 默认值直接取自 VerityConfig.MODPACK_VERSION，避免两处版本号不一致
    public static String modpackVersion = VerityConfig.MODPACK_VERSION;
    public static String verityVersion = "未知";
    public static String configModVersion = "未知";
    public static boolean firstTimeSetup = false;
    public static boolean returnToConfigScreen = false;

    private static final String[] CATEGORY_TABS = {"全部", "前置", "性能优化", "生存辅助", "视觉优化"};
    private int selectedCategory = 0;

    private boolean filterAll = true;
    private boolean filterEnabled = true;
    private boolean filterDisabled = true;

    private static final Set<String> HIDDEN_BRACKETS = new HashSet<>(Arrays.asList(
            "Verity",
            "Verity配置工具",
            "Geckolib",
            "Cloth Config API"
    ));

    private static final Set<String> AUTO_ENABLE_BRACKETS = new HashSet<>(Arrays.asList(
            "Verity",
            "Verity配置工具",
            "Cloth Config API",
            "Yet Another Config Lib",
            "Geckolib",
            "Sodium Options API",
            "BadOptimizations",
            "ImmediatelyFast",
            "Embeddium",
            "内存泄漏修复",
            "动态 FPS",
            "实体渲染机制优化",
            "星光",
            "现代化修复",
            "铁氧体磁芯",
            "铷 · 扩展",
            "简单翻译",
            "输入法冲突修复",
            "钠／Embeddium：动态光源",
            "角色名调整"
    ));

    /**
     * 仅 Windows 可用的模组：非 Windows 端仍然列出，但不允许启用。
     * <p>之所以不直接隐藏，是为了让用户知道这些模组的存在与不可用原因，
     * 而不是疑惑「整合包里到底有没有这个模组」。
     */
    private static final Set<String> WINDOWS_ONLY_BRACKETS = new HashSet<>(Arrays.asList(
            "输入法冲突修复",
            "遥远的地平线"
    ));

    /** 该模组在当前平台是否允许启用。 */
    private static boolean canEnableOnThisPlatform(String bracketText) {
        return PlatformUtils.isWindows() || !WINDOWS_ONLY_BRACKETS.contains(bracketText);
    }

    /** 条目右侧的教程按钮：显示文字 + 点击后打开的帮助主题。 */
    private record HelperButton(String label, String topic) {
    }

    /** 教程按钮的边框颜色：淡蓝色，比默认的灰色描边更醒目。 */
    private static final int HELPER_BORDER_COLOR = 0xFF88CCFF;

    /** 哪些模组在条目右侧显示教程按钮，以及各自的显示文字与帮助主题。 */
    private static final Map<String, HelperButton> MOD_HELPER_BUTTONS = new HashMap<>();
    static {
        MOD_HELPER_BUTTONS.put("Oculus",
                new HelperButton("如何安装光影", "shader_install"));
        MOD_HELPER_BUTTONS.put("触摸控制器",
                new HelperButton("使用教程", "tc_use"));
    }

    /** 按钮尺寸与右边距：绘制与点击检测共用，避免两处写死后不一致。 */
    private static final int HELPER_BUTTON_WIDTH = 90;
    private static final int HELPER_BUTTON_HEIGHT = 16;
    private static final int HELPER_BUTTON_MARGIN = 10;

    private static final Map<String, List<String>> MOD_DEPENDENCIES = new HashMap<>();
    static {
        MOD_DEPENDENCIES.put("Sodium Options API", Arrays.asList("Embeddium"));
        MOD_DEPENDENCIES.put("铷 · 扩展", Arrays.asList("Embeddium"));
        MOD_DEPENDENCIES.put("连锁破坏", Arrays.asList("FTB Library", "Architectury API"));
        MOD_DEPENDENCIES.put("钠／Embeddium：动态光源", Arrays.asList("Embeddium", "Sodium Options API"));
        MOD_DEPENDENCIES.put("一键背包整理Next", Arrays.asList("Kotlin For Forge", "libIPN"));
        MOD_DEPENDENCIES.put("Oculus", Arrays.asList("Embeddium"));
    }

    private static final Map<String, String> MOD_INTRODUCTIONS = new HashMap<>();
    static {
        MOD_INTRODUCTIONS.put("Architectury API", "一个旨在简化跨平台模组开发的中间 API");
        MOD_INTRODUCTIONS.put("FTB Library", "FTB 系列模组中所有 GUI 相关内容的通用代码");
        MOD_INTRODUCTIONS.put("Cloth Config API", "Minecraft 模组配置库");
        MOD_INTRODUCTIONS.put("Geckolib", "用于实体、方块、物品、盔甲等的3D动画库");
        MOD_INTRODUCTIONS.put("Kotlin For Forge", "添加了一个Kotlin语言加载器，并提供了一些可选的实用工具");
        MOD_INTRODUCTIONS.put("libIPN", "Inventory Profiles Next GUI/配置库");
        MOD_INTRODUCTIONS.put("Sodium Options API", "用于添加 钠/铷/π 选项的配置API,带有更好的分类菜单");
        MOD_INTRODUCTIONS.put("Yet Another Config Lib", "一个基于构建器(Builder)模式的 Minecraft 配置库");
        MOD_INTRODUCTIONS.put("BadOptimizations", "侧重于渲染以外内容的优化模组");
        MOD_INTRODUCTIONS.put("ImmediatelyFast", "加速 Minecraft 中的即时模式渲染");
        MOD_INTRODUCTIONS.put("Embeddium", "一款功能强大/对模组友好的 FOSS 客户端性能优化模组");
        MOD_INTRODUCTIONS.put("内存泄漏修复", "一款修复客户端与服务端随机内存泄漏的模组");
        MOD_INTRODUCTIONS.put("动态 FPS", "在 Minecraft 处于后台、 闲置或使用电池供电时减少资源消耗");
        MOD_INTRODUCTIONS.put("实体渲染机制优化", "通过异步路径追踪技术隐藏不可见的方块和实体");
        MOD_INTRODUCTIONS.put("星光", "重写光照引擎以修复光照性能和光照错误");
        MOD_INTRODUCTIONS.put("现代化修复", "全面提升性能的一体化模组，有效减少内存占用并修复诸多漏洞");
        MOD_INTRODUCTIONS.put("铁氧体磁芯", "内存使用优化");
        MOD_INTRODUCTIONS.put("铷 · 扩展", "钠 · 拓展 的 Forge 移植版本");
        MOD_INTRODUCTIONS.put("C键缩放", "一款强大、轻量且高度可定义的缩放模组");
        MOD_INTRODUCTIONS.put("JEI物品管理器", "查看物品与配方");
        MOD_INTRODUCTIONS.put("Xaero的世界地图", "添加了全屏世界地图功能,可以展示在存档中探索过的区域,与 Xaero的小地图 搭配使用效果更佳");
        MOD_INTRODUCTIONS.put("Xaero的小地图", "在屏幕角落显示附近地形、实体的小地图。允许玩家创建坐标点、传送");
        MOD_INTRODUCTIONS.put("更好的进度", "改善 Minecraft 1.12+ 在模组环境中进度系统的UI(用户界面)与UX(用户体验)");
        MOD_INTRODUCTIONS.put("玉 🔍", "显示你所注视的目标的相关信息");
        MOD_INTRODUCTIONS.put("简单翻译", "客户端实时翻译模组,支持翻译聊天，书籍，告示牌，HUD等等界面");
        MOD_INTRODUCTIONS.put("苹果皮", "饥饿/食物相关的 HUD 改进");
        MOD_INTRODUCTIONS.put("触摸控制器", "让Java版有类似基岩版的操作体验");
        MOD_INTRODUCTIONS.put("生物瓶子", "捕捉生物(比如 Verity )");
        MOD_INTRODUCTIONS.put("输入法冲突修复", "妈妈再也不用担心我玩MC卡输入法了");
        MOD_INTRODUCTIONS.put("连锁破坏", "就是你想的那个连锁采集");
        MOD_INTRODUCTIONS.put("钠／Embeddium：动态光源", "就是你想的那个动态光源");
        MOD_INTRODUCTIONS.put("鼠标手势", "通过为鼠标键增加多样功能优化背包管理体验");
        MOD_INTRODUCTIONS.put("物品栏HUD+", "将你的主副手、药水效果和装备信息显示在屏幕上(其实就是显示耐久模组)");
        MOD_INTRODUCTIONS.put("一键背包整理Next", "妈妈再也不用担心我的背包乱糟糟的了");
        MOD_INTRODUCTIONS.put("3D 皮肤层", "如果你有自定义皮肤，该模组会让你的皮肤第二层以3D形式呈现");
        MOD_INTRODUCTIONS.put("Oculus", "Iris 的Forge移植版本，用于加载光影");
        MOD_INTRODUCTIONS.put("物理声效重制版", "实现逼真的声音衰减、混响和吸收效果");
        MOD_INTRODUCTIONS.put("现代化 UI", "改善 Minecraft 的UI，使用更清晰的字体");
        MOD_INTRODUCTIONS.put("聊天头像", "在聊天栏显示发言玩家头像(如果你不联机加这个貌似没什么用)");
        MOD_INTRODUCTIONS.put("遥远的地平线", "大幅提升渲染距离而不影响性能");
        MOD_INTRODUCTIONS.put("角色名调整", "修复Java版1.18+无法使用中文名进入游戏的问题");
    }

    private static class ModEntry {
        final String bracketText;
        final String braceText;
        final String bareName;
        final boolean disabled;
        final String introduction;
        final File file;

        ModEntry(String bracketText, String braceText, String bareName, boolean disabled, File file) {
            this.bracketText = bracketText;
            this.braceText = braceText;
            this.bareName = bareName;
            this.disabled = disabled;
            this.introduction = MOD_INTRODUCTIONS.getOrDefault(bracketText, "");
            this.file = file;
        }
    }

    private final List<ModEntry> mods = new ArrayList<>();
    private static final int ITEM_HEIGHT = 20;
    private static final int CHECKBOX_SIZE = 12;
    private static final int FILTER_ROW_Y = 27;
    private static final int FILTER_CHECKBOX_SIZE = 10;
    // 顶部遮罩下边界：包住标题、按钮、版本信息与筛选行即可，
    // 之前直接用 LIST_TOP-1 会在筛选行下方留下一大块空白
    private static final int HEADER_HEIGHT = FILTER_ROW_Y + FILTER_CHECKBOX_SIZE + 3;
    // 列表从表头下方紧接着开始，与遮罩之间只留一点缝隙
    private static final int LIST_TOP = HEADER_HEIGHT + 2;

    /** 条目实际绘制高度：背景与红框都只画到 ITEM_HEIGHT - 2，预留 2px 行距。 */
    private static final int ITEM_VISUAL_HEIGHT = ITEM_HEIGHT - 2;

    /**
     * 勾选框在条目内的 y 偏移。
     * <p>垂直居中要基于「实际绘制高度」而非 ITEM_HEIGHT：
     * 用 ITEM_HEIGHT 会算成 (20-12)/2=4，导致上方留白 4px、下方仅 2px，视觉偏下一像素。
     * 渲染与点击检测共用本方法，避免两处公式不一致。
     */
    private static int checkboxOffsetY() {
        return (ITEM_VISUAL_HEIGHT - CHECKBOX_SIZE) / 2;
    }

    private Button doneButton;
    private Button moreModsButton;
    private final List<Button> tabButtons = new ArrayList<>();

    private ScrollableArea scrollableArea;
    private boolean draggingScrollbar = false;

    /** 前置依赖确认的配色：边框与文字用同一个蓝，文字更淡。 */
    private static final int DEP_BORDER_COLOR = 0xFF5599FF;
    private static final int DEP_TEXT_COLOR = 0xFFAACCFF;

    /**
     * 确认按钮的高度与内边距；宽度由文字长度决定。
     * <p>高度取 14px：条目实际只有 {@link #ITEM_VISUAL_HEIGHT}(18) 像素高，
     * 再高就没有上下留白，按钮会贴住条目边缘。
     */
    private static final int DEP_BUTTON_HEIGHT = 14;
    private static final int DEP_BUTTON_PADDING_X = 4;
    private static final int DEP_BUTTON_GAP = 4;
    private static final int DEP_BUTTON_MARGIN = 4;

    /** 按钮文案。 */
    private static final String DEP_LABEL_CONFIRM = "确认";
    private static final String DEP_LABEL_CANCEL = "取消";

    /**
     * 按钮配色：安全选项用绿、会改变现状的选项用红。
     * <p>绿色在两种提示里都表示「不造成破坏」：启用时是「启用」，
     * 禁用时是「取消」，因此两个方向的配色是相反的。
     */
    private static final int DEP_SAFE_BG = 0xFF2E7D32;
    private static final int DEP_SAFE_BORDER = 0xFF81C784;
    private static final int DEP_DANGER_BG = 0xFF8E2A2A;
    private static final int DEP_DANGER_BORDER = 0xFFE57373;

    /** 提示文字与勾选框之间的留白。 */
    private static final int DEP_TEXT_GAP = 6;

    /** 两行文字的淡入淡出时长（毫秒）。 */
    private static final long DEP_FADE_MS = 200;

    /** 第二行前置列表的滚动速度（像素/秒）与首尾重复之间的空档。 */
    private static final float DEP_SCROLL_SPEED = 20f;
    private static final int DEP_SCROLL_GAP = 24;

    /**
     * 第二行开始滚动前的停留时长（毫秒）。
     * <p>提示刚出现时文字还在淡入，立刻滚动会让用户看不清最前面那一项；
     * 先静止这么长时间，等淡入结束、首项读完之后再开始移动。
     */
    private static final long DEP_SCROLL_DELAY_MS = 1000;

    /**
     * 正在等待确认的模组名；null 表示当前没有待确认项。
     * <p>与 {@link #rejectedBracket} 完全独立：平台警告会自动过期，
     * 这里的确认必须由用户点按钮才会结束，两者不能共用状态。
     */
    private String depPendingBracket = null;

    /** 待确认模组缺少的前置（启用方向），或依赖它的已启用模组（禁用方向）。 */
    private final List<String> depPendingMissing = new ArrayList<>();

    /**
     * 当前提示的方向：true 表示「启用该模组及其前置」，false 表示「禁用该模组」。
     * <p>两个方向的文案、行数与按钮配色都相反，用同一个字段区分，
     * 避免再铺一套并行的状态。
     */
    private boolean depPromptEnabling = true;

    /** 确认提示出现的时间戳，用于淡入；-1 表示当前没有提示。 */
    private long depPendingAt = -1;

    /**
     * 当前渲染出的两个按钮范围，供点击检测与 tooltip 使用。
     * <p>之所以每帧记录而不是每帧重算：按钮位置依赖 scrollOffset 与条目在
     * 屏幕上的实际行号，在 {@link #mouseClicked} 里重新推导容易与渲染不一致。
     * <p>按钮宽度随文字变化，所以记录的是矩形而非单点。
     * <p>只要渲染在跑，这里就是当前帧的真实位置；按钮被销毁时会被清空，
     * 因此点击检测不会命中已经不存在的按钮。
     * <p>「确认」与「取消」各自记录自己的矩形：两个方向的左右顺序会翻转，
     * 按语义存而不是按左右存，后续再换布局时不必改动点击逻辑。
     */
    private int depConfirmX = -1;
    private int depConfirmW = 0;
    private int depCancelX = -1;
    private int depCancelW = 0;
    private int depButtonY = -1;
    private boolean depButtonsAlive = false;

    /** 平台不支持提示的显示时长与淡出时长（毫秒）。 */
    private static final long REJECT_HINT_HOLD_MS = 2500;
    private static final long REJECT_HINT_FADE_MS = 500;

    /** 晃动幅度（像素）与单次完整往复的周期（毫秒）。 */
    private static final float REJECT_SHAKE_AMPLITUDE = 6f;
    private static final long REJECT_SHAKE_PERIOD_MS = 180;

    /**
     * 晃动衰减到 0 所需的时间（毫秒）。
     * <p>幅度随时间<b>连续</b>线性衰减，而不是每 100ms 硬减 1px。
     * <p>阶梯写法（{@code 6f - elapsed/100 * 1f}）会在每个 100ms 边界
     * 让幅度瞬间掉 1px；该边界与 180ms 的正弦周期不同步，相位对齐时
     * 合成位移会在相邻两帧间突跳 3px，肉眼即「画面抖了一下」。
     * 连续衰减没有这个不连续点，因此不会出现偶发跳变。
     */
    private static final long REJECT_SHAKE_DURATION_MS = 600;

    /** 被拒绝启用的模组名，以及拒绝发生的时间戳；-1 表示当前没有提示。 */
    private String rejectedBracket = null;
    private long rejectedAt = -1;

    /** 记录一次「因平台限制无法启用」，让对应条目边框变红并在右侧显示原因。 */
    private void showPlatformReject(String bracketText) {
        // 报错后的 REJECT_IGNORE_CLICK_MS 内所有点击都会被忽略（见 mouseClicked），
        // 正常不会走到这里；若真被调用，只刷新起始时间，不叠加幅度或时长。
        this.rejectedBracket = bracketText;
        this.rejectedAt = System.currentTimeMillis();
    }

    /**
     * 忽略点击的总时长（毫秒）：从报错那一刻起，
     * 这段时间内<b>被警告的那一项</b>的点击不生效。
     * <p>必须<b>大于</b> {@link #REJECT_HINT_HOLD_MS} + {@link #REJECT_HINT_FADE_MS}，
     * 并覆盖到 {@link #clearRejectIfExpired()} 真正清空状态的时刻。
     * <p>否则会出现漏洞窗口：淡出结束后点击被警告项会走到
     * {@link #showPlatformReject} 重置计时，提示一直不消失。
     */
    private static final long REJECT_IGNORE_CLICK_MS = 3250;

    /**
     * 当前是否处于「忽略点击」窗口内，用于判断某个条目的点击要不要吞掉。
     * <p>只依据时间戳判断，不要求 {@code rejectedBracket} 仍非空，
     * 因此状态清除前后都连续生效，没有边界缝隙。
     * <p>调用方必须再比对条目名，否则会连其他模组一起锁住。
     */
    private boolean rejectClicksIgnored() {
        return rejectedAt >= 0
                && System.currentTimeMillis() - rejectedAt < REJECT_IGNORE_CLICK_MS;
    }

    /** 提示是否仍在显示（含淡出阶段）。 */
    private boolean rejectHintVisible() {
        return rejectedBracket != null && rejectedAt >= 0
                && System.currentTimeMillis() - rejectedAt
                        < REJECT_HINT_HOLD_MS + REJECT_HINT_FADE_MS;
    }

    /**
     * 当前应显示的拒绝提示不透明度（0~1）；没有提示或已超过显示时长时返回 0。
     * <p>最后一段做线性淡出，避免提示突然消失。
     * <p>本方法<b>只读</b>，不修改任何状态。之前版本在超时后顺手清空
     * {@code rejectedBracket}/{@code rejectedAt}，导致同一帧内
     * {@link #rejectShakeOffset()} 读到的值与这里不一致；
     * 清除统一交给 {@link #clearRejectIfExpired()}。
     */
    private float rejectHintAlpha() {
        if (rejectedBracket == null || rejectedAt < 0) return 0f;
        long elapsed = System.currentTimeMillis() - rejectedAt;
        if (elapsed >= REJECT_HINT_HOLD_MS + REJECT_HINT_FADE_MS) return 0f;
        if (elapsed <= REJECT_HINT_HOLD_MS) return 1f;
        return 1f - (float) (elapsed - REJECT_HINT_HOLD_MS) / REJECT_HINT_FADE_MS;
    }

    /** 状态清除的宽限期：淡出到 0 之后再多留一会儿，避免临界帧反复切换。 */
    private static final long REJECT_CLEAR_GRACE_MS = 200;

    /** 提示彻底结束后清除状态，避免残留影响后续点击。 */
    private void clearRejectIfExpired() {
        if (rejectedAt >= 0
                && System.currentTimeMillis() - rejectedAt
                        >= REJECT_HINT_HOLD_MS + REJECT_HINT_FADE_MS + REJECT_CLEAR_GRACE_MS) {
            rejectedBracket = null;
            rejectedAt = -1;
        }
    }

    /**
     * 进入「等待确认」状态。
     * <p>若已经在等待同一个模组、且方向相同，只刷新列表内容
     * （用户可能在此期间手动改动了其他模组），不重置时间戳以避免重新淡入；
     * 目标或方向不同则切换过去并重新淡入。
     *
     * @param bracketText 等待确认的模组
     * @param related     启用方向为缺失的前置；禁用方向为依赖它的已启用模组
     * @param enabling    true 表示启用该模组及其前置，false 表示禁用它
     */
    private void showDependencyPrompt(String bracketText, List<String> related, boolean enabling) {
        boolean same = bracketText.equals(depPendingBracket)
                && enabling == depPromptEnabling
                && depPendingAt >= 0;
        this.depPendingBracket = bracketText;
        this.depPromptEnabling = enabling;
        this.depPendingMissing.clear();
        this.depPendingMissing.addAll(related);
        if (!same) {
            this.depPendingAt = System.currentTimeMillis();
        }
    }

    /** 结束等待状态，并销毁按钮，避免留下可点击的幽灵按钮。 */
    private void clearDependencyPrompt() {
        this.depPendingBracket = null;
        this.depPendingMissing.clear();
        this.depPendingAt = -1;
        this.depButtonsAlive = false;
        this.depConfirmX = -1;
        this.depConfirmW = 0;
        this.depCancelX = -1;
        this.depCancelW = 0;
        this.depButtonY = -1;
    }

    /** 前置确认提示的不透明度（0~1）：出现时短暂淡入，之后保持不透明。 */
    private float depPromptAlpha() {
        if (depPendingAt < 0) return 0f;
        long elapsed = System.currentTimeMillis() - depPendingAt;
        if (elapsed >= DEP_FADE_MS) return 1f;
        return (float) elapsed / DEP_FADE_MS;
    }

    /**
     * 第二行前置列表的水平滚动偏移。
     * <p>文字宽度不足可用宽度时返回 0（不滚动）；否则先生成
     * {@link #DEP_SCROLL_DELAY_MS} 的静止期，再按 {@link #DEP_SCROLL_SPEED}
     * 匀速左移，滚过一个「文本 + 空档」的周期后回到起点。
     */
    private int depScrollOffset(String text, int availableWidth) {
        int textWidth = this.font.width(text);
        int cycle = textWidth + DEP_SCROLL_GAP;
        if (textWidth <= availableWidth || cycle <= 0) return 0;
        long elapsed = System.currentTimeMillis() - depPendingAt - DEP_SCROLL_DELAY_MS;
        if (elapsed <= 0) return 0;
        return (int) ((elapsed / 1000f * DEP_SCROLL_SPEED) % cycle);
    }

    /** 把 0~1 的不透明度应用到 ARGB 颜色上。 */
    private static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 提示期间的水平晃动偏移（像素）。
     * <p>用正弦函数产生左右往复，幅度在 {@link #REJECT_SHAKE_DURATION_MS}
     * 内<b>连续</b>线性衰减到 0。
     * <p>与文字淡出相互独立：抖动先停，文字后消失。
     * <p>计时基准是 {@link #rejectedAt}，与提示文字同源，因此两者不会失步。
     */
    private float rejectShakeOffset() {
        if (rejectedAt < 0) return 0f;
        long elapsed = System.currentTimeMillis() - rejectedAt;
        if (elapsed >= REJECT_SHAKE_DURATION_MS) return 0f;

        // 连续衰减：每帧的变化量都很小，不会出现阶梯写法那种
        // 「某一帧幅度突然少 1px」的不连续点
        float amplitude = REJECT_SHAKE_AMPLITUDE
                * (1f - (float) elapsed / REJECT_SHAKE_DURATION_MS);
        double phase = (double) elapsed / REJECT_SHAKE_PERIOD_MS * Math.PI * 2;
        return (float) Math.sin(phase) * amplitude;
    }

    /** 平台不支持时显示在条目右侧的说明文字。 */
    private static Component platformRejectHint() {
        return Component.literal("安卓设备暂不支持该模组");
    }

    public ModsListScreen() {
        super(Component.literal("模组管理"));
    }

    private void loadModList() {
        mods.clear();

        Path modsDir = FMLPaths.MODSDIR.get();
        File dir = modsDir.toFile();
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        Pattern bracketPattern = Pattern.compile("\\[(.*?)\\]");
        Pattern bracePattern = Pattern.compile("\\{(.*?)\\}");

        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();

            boolean disabled = name.endsWith(".jar.disabled");
            String baseName = disabled ? name.substring(0, name.length() - ".disabled".length()) : name;
            if (!baseName.endsWith(".jar")) continue;

            String modName = baseName.substring(0, baseName.length() - 4);

            String bracketText = "";
            String braceText = "";
            String bareName = modName;

            Matcher bracketMatcher = bracketPattern.matcher(modName);
            if (bracketMatcher.find()) {
                bracketText = bracketMatcher.group(1);
                bareName = bracketMatcher.replaceFirst("").trim();
            }

            Matcher braceMatcher = bracePattern.matcher(bareName);
            if (braceMatcher.find()) {
                braceText = braceMatcher.group(1);
                bareName = braceMatcher.replaceFirst("").trim();
            }

            if (HIDDEN_BRACKETS.contains(bracketText)) continue;

            ModEntry entry = new ModEntry(bracketText, braceText, bareName, disabled, file);
            mods.add(entry);
        }
    }

    private List<ModEntry> getFilteredMods() {
        List<ModEntry> result = new ArrayList<>();

        if (filterAll) {
            result.addAll(mods);
        } else {
            for (ModEntry entry : mods) {
                if (filterEnabled && !entry.disabled) result.add(entry);
                else if (filterDisabled && entry.disabled) result.add(entry);
            }
        }

        if (selectedCategory != 0) {
            String targetCategory = CATEGORY_TABS[selectedCategory];
            result.removeIf(entry -> !targetCategory.equals(entry.braceText));
        }
        return result;
    }

    private List<ModEntry> getCategoryMods() {
        if (selectedCategory == 0) {
            return new ArrayList<>(mods);
        }
        String targetCategory = CATEGORY_TABS[selectedCategory];
        List<ModEntry> result = new ArrayList<>();
        for (ModEntry entry : mods) {
            if (targetCategory.equals(entry.braceText)) {
                result.add(entry);
            }
        }
        return result;
    }

    private int[] getModCounts() {
        List<ModEntry> categoryMods = getCategoryMods();
        int enabled = 0, disabled = 0;
        for (ModEntry entry : categoryMods) {
            if (entry.disabled) disabled++;
            else enabled++;
        }
        return new int[]{enabled, disabled};
    }

    private boolean isMouseOverCheckbox(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX <= x + FILTER_CHECKBOX_SIZE && mouseY >= y && mouseY <= y + FILTER_CHECKBOX_SIZE;
    }

    @Override
    protected void init() {
        VerityConfig.loadVersions();

        // 只在「首次启动配置流程」里自动启用模组
        // （WelcomeScreen -> VerityConfigScreen 的「完成」 -> 这里）。
        // 从 /vc modls、配置页的「配置模组」按钮等入口进来时不应改动用户的模组开关。
        if (firstTimeSetup) {
            enableAutoMods();
            firstTimeSetup = false; // 只执行一次，避免后续进入重复触发
        }
        loadModList();

        // resize 会重新 init，而按钮坐标依赖上一帧的渲染结果。
        // 若不清除，会残留指向旧位置的幽灵按钮，点击到空白处也会触发确认。
        // 同时把等待状态一并收起：尺寸变了，提示本就该重新来过。
        clearDependencyPrompt();

        this.scrollableArea = new ScrollableArea(LIST_TOP, this.height - 10);
        this.scrollableArea.setItemHeight(ITEM_HEIGHT);

        int rightX = this.width - 80;
        this.doneButton = Button.builder(Component.literal("完成"), btn -> {
            if (returnToConfigScreen) {
                returnToConfigScreen = false;
                Minecraft.getInstance().setScreen(new VerityConfigScreen());
            } else {
                showRestartConfirm();
            }
        }).pos(rightX, 5).size(70, 20).build();
        this.addRenderableWidget(this.doneButton);

        tabButtons.clear();
        int tabX = 80;
        int tabY = 5;
        for (int i = 0; i < CATEGORY_TABS.length; i++) {
            final int tabIndex = i;
            int tabWidth = this.font.width(CATEGORY_TABS[i]) + 12;
            Button tabButton = Button.builder(Component.literal(CATEGORY_TABS[i]), btn -> {
                selectedCategory = tabIndex;
                updateTabStyles();
                scrollableArea.setScrollOffset(0);
            }).pos(tabX, tabY).size(tabWidth, 20).build();
            this.addRenderableWidget(tabButton);
            tabButtons.add(tabButton);
            tabX += tabWidth;
        }

        int totalTabWidth = 0;
        for (String tab : CATEGORY_TABS) {
            totalTabWidth += this.font.width(tab) + 12;
        }
        int moreBtnX = 80 + totalTabWidth + 2;
        int moreBtnWidth = this.font.width("更多模组") + 12;
        if (moreBtnX + moreBtnWidth > this.width - 80) {
            moreBtnWidth = this.width - 80 - moreBtnX - 5;
            if (moreBtnWidth < 30) moreBtnWidth = 30;
        }
        this.moreModsButton = Button.builder(Component.literal("更多模组"), btn -> {
            Minecraft.getInstance().setScreen(new HelperScreen("mod_install", this));
        }).pos(moreBtnX, 5).size(moreBtnWidth, 20).build();
        this.addRenderableWidget(this.moreModsButton);

        updateTabStyles();
        scrollableArea.setScrollOffset(0);
        scrollableArea.clampScroll(getFilteredMods().size() * ITEM_HEIGHT);
    }

    private void updateTabStyles() {
        for (int i = 0; i < tabButtons.size(); i++) {
            ChatFormatting color = (i == selectedCategory) ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            tabButtons.get(i).setMessage(Component.literal(CATEGORY_TABS[i]).withStyle(color));
        }
    }

    private void enableAutoMods() {
        Path modsDir = FMLPaths.MODSDIR.get();
        File dir = modsDir.toFile();
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".jar.disabled"));
        if (files == null || files.length == 0) {
            System.out.println("已启用共0个模组:[]");
            return;
        }

        Pattern bracketPattern = Pattern.compile("\\[(.*?)\\]");
        List<String> enabledNames = new ArrayList<>();

        for (File file : files) {
            String name = file.getName();
            Matcher bracketMatcher = bracketPattern.matcher(name);
            if (!bracketMatcher.find()) continue;
            String bracketText = bracketMatcher.group(1);

            if (!canEnableOnThisPlatform(bracketText)) {
                continue;
            }

            if (AUTO_ENABLE_BRACKETS.contains(bracketText)) {
                String newName = name.substring(0, name.length() - ".disabled".length());
                File target = new File(dir, newName);
                if (target.exists()) target.delete();
                boolean success = file.renameTo(target);
                if (success) {
                    enabledNames.add(newName);
                }
            }
        }

        System.out.println("已启用共" + enabledNames.size() + "个模组:[" + String.join(",", enabledNames) + "]");
    }

    private ModEntry findModByBracket(String bracketText) {
        for (ModEntry entry : mods) {
            if (entry.bracketText.equals(bracketText)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 找出依赖该模组、且当前处于<b>启用</b>状态的模组名。
     * <p>只列出启用中的模组：被禁用的依赖者本来就没在跑，
     * 禁用前置不会让它们「无法使用」，列出来只会干扰判断。
     */
    private List<String> findEnabledDependents(String bracketText) {
        List<String> dependents = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : MOD_DEPENDENCIES.entrySet()) {
            if (!entry.getValue().contains(bracketText)) continue;
            ModEntry dependent = findModByBracket(entry.getKey());
            // 找不到条目时保守跳过：列表里没有它，就不该出现在提示中
            if (dependent != null && !dependent.disabled) {
                dependents.add(dependent.bracketText);
            }
        }
        return dependents;
    }

    /**
     * 找出该模组缺少的、当前处于禁用状态的前置模组名。
     * <p>返回空列表表示前置齐全，可以正常启用。
     */
    private List<String> findMissingDependencies(String bracketText) {
        List<String> missing = new ArrayList<>();
        List<String> deps = MOD_DEPENDENCIES.get(bracketText);
        if (deps == null || deps.isEmpty()) return missing;
        for (String depName : deps) {
            ModEntry dep = findModByBracket(depName);
            if (dep != null && dep.disabled) {
                missing.add(dep.bracketText);
            }
        }
        return missing;
    }

    private void toggleMod(ModEntry entry) {
        File source = entry.file;
        String name = source.getName();
        if (entry.disabled) {
            // 缺前置时不弹全屏确认，改为在卡片上就地显示提示与按钮，
            // 由 mouseClicked 里的按钮检测决定后续动作
            List<String> missing = findMissingDependencies(entry.bracketText);
            if (!missing.isEmpty()) {
                showDependencyPrompt(entry.bracketText, missing, true);
                return;
            }
            clearDependencyPrompt();
            if (!canEnableOnThisPlatform(entry.bracketText)) {
                showPlatformReject(entry.bracketText);
                return;
            }
            File target = new File(source.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
            if (target.exists()) target.delete();
            source.renameTo(target);
        } else {
            // 只有「当前已启用且依赖它」的模组才会被影响；
            // 一个都没有时直接禁用，不做无谓的确认打扰
            List<String> affected = findEnabledDependents(entry.bracketText);
            if (!affected.isEmpty()) {
                showDependencyPrompt(entry.bracketText, affected, false);
                return;
            }
            clearDependencyPrompt();
            File target = new File(source.getParentFile(), name + ".disabled");
            if (target.exists()) target.delete();
            source.renameTo(target);
        }
        loadModList();
        scrollableArea.clampScroll(getFilteredMods().size() * ITEM_HEIGHT);
    }

    /**
     * 用户点了确认按钮。
     * <p>启用方向：先启用全部缺失前置，再启用本体（顺序与原先一致）。
     * <p>禁用方向：先禁用依赖它的全部已启用模组，再禁用本体。
     * 与启用方向对称 —— 两个方向的确认按钮都会连带处理提示里列出的那一组，
     * 否则第一行列出的「将会导致以下模组无法使用」就只是告知而不解决问题。
     */
    private void confirmDependencyPrompt() {
        if (depPendingBracket == null) return;
        ModEntry target = findModByBracket(depPendingBracket);
        if (target == null) {
            clearDependencyPrompt();
            return;
        }
        if (depPromptEnabling) {
            for (String depName : new ArrayList<>(depPendingMissing)) {
                ModEntry dep = findModByBracket(depName);
                if (dep != null && dep.disabled) {
                    enableMod(dep);
                }
            }
            if (target.disabled && canEnableOnThisPlatform(target.bracketText)) {
                enableMod(target);
            }
        } else {
            for (String depName : new ArrayList<>(depPendingMissing)) {
                ModEntry dep = findModByBracket(depName);
                if (dep != null && !dep.disabled) {
                    disableMod(dep);
                }
            }
            if (!target.disabled) {
                disableMod(target);
            }
        }
        clearDependencyPrompt();
        loadModList();
        scrollableArea.clampScroll(getFilteredMods().size() * ITEM_HEIGHT);
    }

    /**
     * 用户点了取消：什么都不做，仅收起提示。
     * <p>两个方向的语义一致 —— 既不启用任何模组，也不禁用任何模组。
     */
    private void cancelDependencyPrompt() {
        clearDependencyPrompt();
    }

    private void enableMod(ModEntry entry) {
        File source = entry.file;
        String name = source.getName();
        if (name.endsWith(".jar.disabled")) {
            String newName = name.substring(0, name.length() - ".disabled".length());
            File target = new File(source.getParentFile(), newName);
            if (target.exists()) target.delete();
            source.renameTo(target);
        }
    }

    private void disableMod(ModEntry entry) {
        File source = entry.file;
        String name = source.getName();
        if (name.endsWith(".jar")) {
            File target = new File(source.getParentFile(), name + ".disabled");
            if (target.exists()) target.delete();
            source.renameTo(target);
        }
    }

    private void showRestartConfirm() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                mc.stop();
            } else {
                if (mc.player != null) {
                    mc.setScreen(null);
                } else {
                    mc.setScreen(new TitleScreen());
                }
            }
        },
                Component.literal("重启游戏以应用配置"),
                Component.literal("配置已保存，是否重启游戏？")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int left = 2;
        int right = this.width - 2;
        int listTop = LIST_TOP;
        int listBottom = this.height - 10;
        int visibleHeight = listBottom - listTop;

        List<ModEntry> filteredMods = getFilteredMods();
        int totalContentHeight = filteredMods.size() * ITEM_HEIGHT;
        scrollableArea.clampScroll(totalContentHeight);
        int scrollOffset = scrollableArea.getScrollOffset();
        int maxVisible = scrollableArea.getMaxVisible();

        // 先清理已结束的提示，再取值：rejectHintAlpha() 只读，状态清除单独进行，
        // 否则同一帧内后续读取到的值与判断依据会不一致。
        // 每帧只求值一次并缓存，保证同一帧内所有条目用同一个 alpha
        clearRejectIfExpired();
        float rejectAlpha = rejectHintAlpha();
        String rejectedName = rejectedBracket;
        // 前置确认同样每帧只求值一次；按钮坐标也在本帧渲染时刷新
        float depAlpha = depPromptAlpha();
        String depName = depPendingBracket;
        // 每帧开始时先标记按钮不存在：只有本轮真正画出来的才是活的，
        // 目标条目因滚动离开视野或状态被清除时，按钮不会留下幽灵坐标
        depButtonsAlive = false;

        for (int i = 0; i < maxVisible && scrollOffset / ITEM_HEIGHT + i < filteredMods.size(); i++) {
            int index = scrollOffset / ITEM_HEIGHT + i;
            ModEntry entry = filteredMods.get(index);
            int y = listTop + i * ITEM_HEIGHT;

            int bgColor = (i % 2 == 0) ? 0x80000000 : 0x88000000;
            graphics.fill(left, y, right, y + ITEM_VISUAL_HEIGHT, bgColor);

            // 平台不支持时该条目边框变红，并在右侧显示原因，提示会自动淡出
            boolean rejected = rejectAlpha > 0f
                    && entry.bracketText.equals(rejectedName);
            // 该条目正在等待前置确认
            boolean depPending = depAlpha > 0f && entry.bracketText.equals(depName);
            if (rejected) {
                graphics.renderOutline(left, y, right - left, ITEM_VISUAL_HEIGHT,
                        withAlpha(0xFFFF5555, rejectAlpha));
            } else if (depPending) {
                // 蓝框：与红色警告互斥，警告优先
                graphics.renderOutline(left, y, right - left, ITEM_VISUAL_HEIGHT,
                        withAlpha(DEP_BORDER_COLOR, depAlpha));
            }

            int cbX = left + 4;
            int cbY = y + checkboxOffsetY();
            graphics.renderOutline(cbX, cbY, CHECKBOX_SIZE, CHECKBOX_SIZE, 0xFFAAAAAA);
            if (!entry.disabled) {
                graphics.fill(cbX + 2, cbY + 2, cbX + CHECKBOX_SIZE - 2, cbY + CHECKBOX_SIZE - 2, 0xFFFFFFFF);
            }

            // 被拒绝/待确认的条目：提示淡出或保持，原信息同步淡入淡出。
            // 关键：normalAlpha 只在当前条目命中提示时才降低，
            // 若写成「全屏共用的 1 - alpha」，提示满不透明时
            // normalAlpha 会变成 0，导致所有正常条目被一起跳过、整页消失。
            float normalAlpha = (rejected || depPending) ? 1f - Math.max(rejectAlpha, depAlpha) : 1f;

            if (rejected) {
                Component hint = platformRejectHint();
                float shake = rejectShakeOffset();
                int hintX = cbX + CHECKBOX_SIZE + 6 + (int) shake;
                int hintY = y + (ITEM_VISUAL_HEIGHT - 8) / 2;
                graphics.drawString(this.font, hint, hintX, hintY,
                        withAlpha(0xFFFF5555, rejectAlpha), false);
            } else if (depPending) {
                renderDependencyPrompt(graphics, entry, left, right, y, cbX, depAlpha);
            }

            // 原信息：完全透明时才跳过，避免无谓绘制。
            // 交叉淡入保证淡出期间整行始终有内容，不会闪空白
            if (normalAlpha <= 0f) continue;

            int textX = left + 20;
            int firstLineY = y + 1;
            int x = textX;

            if (!entry.bracketText.isEmpty()) {
                Style bracketStyle = Style.EMPTY
                        .withColor(entry.disabled ? 0xFF888888 : 0xFFFFFFFF)
                        .withStrikethrough(entry.disabled);
                Component bracketComp = Component.literal(entry.bracketText).setStyle(bracketStyle);
                graphics.drawString(this.font, bracketComp, x, firstLineY,
                        withAlpha(0xFFFFFFFF, normalAlpha), false);
                x += this.font.width(entry.bracketText) + 4;
            }

            if (!entry.bareName.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(x, firstLineY + 2, 0);
                graphics.pose().scale(0.5f, 0.5f, 1.0f);
                Component bareComp = Component.literal(entry.bareName).setStyle(Style.EMPTY.withColor(0xFFAAAAAA));
                graphics.drawString(this.font, bareComp, 0, 0, withAlpha(0xFFAAAAAA, normalAlpha), false);
                graphics.pose().popPose();
            }

            String secondLineText = entry.braceText;
            if (!entry.introduction.isEmpty()) {
                secondLineText += " | " + entry.introduction;
            }
            if (!secondLineText.isEmpty()) {
                Component secondLineComp = Component.literal(secondLineText).setStyle(Style.EMPTY.withColor(0xFFAAAAAA));
                graphics.drawString(this.font, secondLineComp, textX, y + 9,
                        withAlpha(0xFFAAAAAA, normalAlpha), false);
            }

            // 教程按钮：尺寸与位置由 renderHelperButton 统一决定
            HelperButton helper = MOD_HELPER_BUTTONS.get(entry.bracketText);
            if (helper != null) {
                renderHelperButton(graphics, helper, y);
            }
        }

        // 顶部遮罩只包住表头内容，下方直接露出列表（列表会滚动到遮罩下面）
        graphics.fill(0, 0, this.width, HEADER_HEIGHT - 1, 0xBF000000);
        graphics.fill(0, HEADER_HEIGHT - 1, this.width, HEADER_HEIGHT, 0xFFAAAAAA);

        graphics.drawString(this.font, "模组管理 (" + mods.size() + ")", 5, 4, 0xFFFFFF, false);

        graphics.pose().pushPose();
        graphics.pose().translate(5, 16, 0);
        graphics.pose().scale(0.5f, 0.5f, 1.0f);
        graphics.drawString(this.font, "Verity Modpack v" + modpackVersion, 0, 0, 0xFFAAAAAA, false);
        graphics.drawString(this.font, "Verity JE v" + verityVersion, 0, 10, 0xFFAAAAAA, false);
        graphics.drawString(this.font, "Verity Config v" + configModVersion, 0, 20, 0xFFAAAAAA, false);
        graphics.pose().popPose();

        int[] counts = getModCounts();
        int enabledCount = counts[0];
        int disabledCount = counts[1];

        int filterX = 82;
        int filterY = FILTER_ROW_Y;
        String labelAll = "全部 (" + getFilteredMods().size() + ")";
        drawFilterCheckbox(graphics, filterX, filterY, filterAll, labelAll);
        filterX += this.font.width(labelAll) + 20;

        String labelEnabled = "已启用 (" + enabledCount + ")";
        drawFilterCheckbox(graphics, filterX, filterY, filterEnabled, labelEnabled);
        filterX += this.font.width(labelEnabled) + 20;

        String labelDisabled = "已禁用 (" + disabledCount + ")";
        drawFilterCheckbox(graphics, filterX, filterY, filterDisabled, labelDisabled);

        scrollableArea.renderScrollbar(graphics, this.width, totalContentHeight);

        super.render(graphics, mouseX, mouseY, partialTick);

        // tooltip 放在最后画，避免被后续的条目与遮罩盖住。
        // 文案随提示方向变化：确认按钮在两个方向上的含义相反。
        if (depButtonsAlive && depButtonY >= 0) {
            if (isOverDepButton(mouseX, mouseY, depConfirmX, depConfirmW)) {
                String hint = depPromptEnabling ? "启用该模组与其前置" : "禁用该模组及依赖它的模组";
                graphics.renderTooltip(this.font, Component.literal(hint), (int) mouseX, (int) mouseY);
            } else if (isOverDepButton(mouseX, mouseY, depCancelX, depCancelW)) {
                String hint = depPromptEnabling ? "不启用任何模组" : "不禁用任何模组";
                graphics.renderTooltip(this.font, Component.literal(hint), (int) mouseX, (int) mouseY);
            }
        }
    }

    private void drawFilterCheckbox(GuiGraphics graphics, int x, int y, boolean checked, String label) {
        graphics.renderOutline(x, y, FILTER_CHECKBOX_SIZE, FILTER_CHECKBOX_SIZE, 0xFFAAAAAA);
        if (checked) {
            graphics.fill(x + 2, y + 2, x + FILTER_CHECKBOX_SIZE - 2, y + FILTER_CHECKBOX_SIZE - 2, 0xFFFFFFFF);
        }
        graphics.drawString(this.font, label, x + FILTER_CHECKBOX_SIZE + 4, y + 1, 0xFFFFFF, false);
    }

    /**
     * 绘制确认提示：蓝框内的两行文字，以及「确认」「取消」两个按钮。
     * <p>文案与按钮配色按 {@link #depPromptEnabling} 分两个方向：
     * 启用方向问「是否启用该模组与其前置」，禁用方向问「是否禁用」。
     * <p>两个方向的按钮<b>顺序与配色相反</b>：启用方向是左绿确认、右红取消，
     * 禁用方向是左绿取消、右红确认。这样最右侧的按钮始终是「会改变现状」
     * 的那一个，而绿色永远代表「不造成破坏」的选项。
     * <p>按钮形状跟随文字长度，因此位置需要按实际宽度计算。
     * <p>窗口过窄放不下时，自动回退为贴在右边缘的布局。
     */
    private void renderDependencyPrompt(GuiGraphics graphics, ModEntry entry,
            int left, int right, int y, int cbX, float alpha) {
        boolean enabling = depPromptEnabling;

        String line1 = enabling
                ? "该模组必须启用以下模组作为前置，是否启用该模组与其前置？"
                : "禁用该模组后将会导致以下模组无法使用，是否禁用？";
        String line2 = String.join("、", depPendingMissing);

        // 最右侧的按钮永远是「改变现状」的那个
        String dangerLabel = enabling ? DEP_LABEL_CANCEL : DEP_LABEL_CONFIRM;
        String safeLabel = enabling ? DEP_LABEL_CONFIRM : DEP_LABEL_CANCEL;

        int safeWidth = depButtonWidth(safeLabel);
        int dangerWidth = depButtonWidth(dangerLabel);
        int buttonsWidth = safeWidth + DEP_BUTTON_GAP + dangerWidth;

        int textLeft = cbX + CHECKBOX_SIZE + DEP_TEXT_GAP;
        int maxRight = right - DEP_BUTTON_MARGIN;

        // 第一行文字 + 按钮是否放得下；放不下就回退到右边缘布局
        boolean inlineButtons = textLeft + this.font.width(line1)
                + DEP_TEXT_GAP + buttonsWidth <= maxRight;

        int safeX;
        int dangerX;
        int textRight;

        if (inlineButtons) {
            // 紧随第一行文字之后，左安全右危险
            int startX = textLeft + this.font.width(line1) + DEP_TEXT_GAP;
            safeX = startX;
            dangerX = startX + safeWidth + DEP_BUTTON_GAP;
        } else {
            // 回退：贴在右边缘，两行文字在按钮左侧结束
            dangerX = maxRight - dangerWidth;
            safeX = dangerX - DEP_BUTTON_GAP - safeWidth;
        }

        // 第二行文字的右边界必须让开按钮，取两者中靠左的那个。
        // 不能直接用 safeX：禁用方向下 safeX 是右侧的「取消」，
        // 而左边那个是「确认」，用 safeX 会让文字压在确认按钮下面。
        textRight = Math.min(safeX, dangerX) - DEP_TEXT_GAP;

        // 按钮在条目内垂直居中：条目高 18px、按钮高 14px，上下各留 2px
        int buttonY = y + (ITEM_VISUAL_HEIGHT - DEP_BUTTON_HEIGHT) / 2;

        // 坐标按语义记录：确认按钮与取消按钮各自的位置与宽度
        if (enabling) {
            this.depConfirmX = safeX;
            this.depConfirmW = safeWidth;
            this.depCancelX = dangerX;
            this.depCancelW = dangerWidth;
        } else {
            this.depConfirmX = dangerX;
            this.depConfirmW = dangerWidth;
            this.depCancelX = safeX;
            this.depCancelW = safeWidth;
        }
        this.depButtonY = buttonY;
        this.depButtonsAlive = true;

        int availableWidth = textRight - textLeft;
        if (availableWidth <= 0) return;

        int color = withAlpha(DEP_TEXT_COLOR, alpha);

        // 第一行：内联时宽度只到文字末尾，避免与按钮重叠
        int line1Width = inlineButtons ? this.font.width(line1) : availableWidth;
        graphics.drawString(this.font, trimToWidth(line1, line1Width), textLeft, y + 1, color, false);

        // 第二行：相关模组列表，空间不足时左右循环滚动
        int line2Width = textRight - textLeft;
        int scroll = depScrollOffset(line2, line2Width);
        if (scroll == 0) {
            graphics.drawString(this.font, trimToWidth(line2, line2Width), textLeft, y + 10, color, false);
        } else {
            // 用裁剪区限制滚动文字不侵入按钮区域
            graphics.enableScissor(textLeft, y + 9, textRight, y + 19);
            int w = this.font.width(line2);
            graphics.drawString(this.font, line2, textLeft - scroll, y + 10, color, false);
            // 尾接首，形成无缝循环
            graphics.drawString(this.font, line2, textLeft - scroll + w + DEP_SCROLL_GAP, y + 10, color, false);
            graphics.disableScissor();
        }

        // 先画安全按钮再画危险按钮，顺序与位置无关，
        // 因为两者不重叠；这里按「左到右」的顺序绘制便于阅读
        renderDepButton(graphics, safeX, buttonY, safeWidth, safeLabel,
                DEP_SAFE_BG, DEP_SAFE_BORDER, alpha);
        renderDepButton(graphics, dangerX, buttonY, dangerWidth, dangerLabel,
                DEP_DANGER_BG, DEP_DANGER_BORDER, alpha);
    }

    /** 按钮宽度：文字宽度加左右内边距。 */
    private int depButtonWidth(String label) {
        return this.font.width(label) + DEP_BUTTON_PADDING_X * 2;
    }

    /**
     * 画一个确认/取消按钮：实色底 + 亮色边框 + 白色文字。
     * <p>不画最外层黑色描边：在两个按钮相邻、背景又是深色条目时，
     * 黑边会让按钮显得脏；改用亮色边框自身提供轮廓。
     */
    private void renderDepButton(GuiGraphics graphics, int x, int y, int width,
            String label, int bg, int border, float alpha) {
        graphics.fill(x, y, x + width, y + DEP_BUTTON_HEIGHT, withAlpha(bg, alpha));
        graphics.renderOutline(x, y, width, DEP_BUTTON_HEIGHT, withAlpha(border, alpha));
        graphics.drawCenteredString(this.font, label, x + width / 2,
                y + (DEP_BUTTON_HEIGHT - 8) / 2, withAlpha(0xFFFFFFFF, alpha));
    }

    /**
     * 按像素宽度截断字符串，超出部分丢弃。
     * <p>直接委托给 {@link Font#plainSubstrByWidth(String, int)}：
     * 它内部按每个码点的实际字宽累加，能正确处理中文与代理对，
     * 自己按 char 逐个累加会在中文与 emoji 上算错。
     */
    private String trimToWidth(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (this.font.width(text) <= maxWidth) return text;
        return this.font.plainSubstrByWidth(text, maxWidth);
    }

    /** 点是否落在一个矩形按钮内。 */
    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width) {
        return x >= 0 && y >= 0 && width > 0
                && mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + DEP_BUTTON_HEIGHT;
    }

    /** 鼠标是否悬停在某个确认/取消按钮上，用于决定画哪个 tooltip。 */
    private boolean isOverDepButton(double mouseX, double mouseY, int x, int width) {
        return depButtonsAlive && isInside(mouseX, mouseY, x, depButtonY, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 确认提示的按钮优先于其他一切操作：
        // 它们是「必须做出选择」的状态，被滚动条或勾选框抢走点击会让人困惑。
        // depButtonsAlive 保证只命中本帧真正画出来的按钮，避免幽灵坐标。
        if (button == 0 && depButtonsAlive && depButtonY >= 0) {
            if (isInside(mouseX, mouseY, depConfirmX, depButtonY, depConfirmW)) {
                confirmDependencyPrompt();
                return true;
            }
            if (isInside(mouseX, mouseY, depCancelX, depButtonY, depCancelW)) {
                cancelDependencyPrompt();
                return true;
            }
        }

        if (button == 0 && scrollableArea.isMouseOverScrollbar(mouseX, mouseY, this.width, getFilteredMods().size() * ITEM_HEIGHT)) {
            draggingScrollbar = true;
            scrollableArea.updateScrollFromMouse(mouseY, this.width, getFilteredMods().size() * ITEM_HEIGHT);
            return true;
        }

        int[] counts = getModCounts();
        int enabledCount = counts[0];
        int disabledCount = counts[1];

        int filterX = 82;
        int filterY = FILTER_ROW_Y;
        String labelAll = "全部 (" + getFilteredMods().size() + ")";
        if (isMouseOverCheckbox(mouseX, mouseY, filterX, filterY)) {
            filterAll = !filterAll;
            if (!filterAll) {
                filterEnabled = true;
                filterDisabled = false;
            } else {
                filterEnabled = true;
                filterDisabled = true;
            }
            scrollableArea.setScrollOffset(0);
            return true;
        }
        filterX += this.font.width(labelAll) + 20;

        String labelEnabled = "已启用 (" + enabledCount + ")";
        if (isMouseOverCheckbox(mouseX, mouseY, filterX, filterY)) {
            filterEnabled = !filterEnabled;
            if (filterEnabled && filterDisabled) {
                filterAll = true;
                filterEnabled = true;
                filterDisabled = true;
            } else {
                filterAll = false;
            }
            scrollableArea.setScrollOffset(0);
            return true;
        }
        filterX += this.font.width(labelEnabled) + 20;

        String labelDisabled = "已禁用 (" + disabledCount + ")";
        if (isMouseOverCheckbox(mouseX, mouseY, filterX, filterY)) {
            filterDisabled = !filterDisabled;
            if (filterEnabled && filterDisabled) {
                filterAll = true;
                filterEnabled = true;
                filterDisabled = true;
            } else {
                filterAll = false;
            }
            scrollableArea.setScrollOffset(0);
            return true;
        }

        if (button == 0) {
            HelperButton hit = findClickedHelperButton(mouseX, mouseY);
            if (hit != null) {
                Minecraft.getInstance().setScreen(new HelperScreen(hit.topic(), this));
                return true;
            }
        }

        if (button == 0) {
            List<ModEntry> filteredMods = getFilteredMods();
            int scrollOffset = scrollableArea.getScrollOffset();
            int maxVisible = scrollableArea.getMaxVisible();
            for (int i = 0; i < maxVisible && scrollOffset / ITEM_HEIGHT + i < filteredMods.size(); i++) {
                int index = scrollOffset / ITEM_HEIGHT + i;
                ModEntry entry = filteredMods.get(index);
                int y = LIST_TOP + i * ITEM_HEIGHT;
                int cbX = 2 + 4;
                int cbY = y + checkboxOffsetY();
                if (mouseX >= cbX && mouseX <= cbX + CHECKBOX_SIZE &&
                        mouseY >= cbY && mouseY <= cbY + CHECKBOX_SIZE) {
                    // 只忽略「正在报错的那一项」的点击：重复点它会重置 rejectedAt，
                    // 让计时从头开始、提示一直不消失。
                    // 其他模组、过滤器、标签页、滚动条都不受影响。
                    if (rejectClicksIgnored() && entry.bracketText.equals(rejectedBracket)) {
                        return true;
                    }
                    toggleMod(entry);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 教程按钮的左上角 x 坐标（贴右边缘，留出 HELPER_BUTTON_MARGIN 的边距）。 */
    private int helperButtonX() {
        return this.width - HELPER_BUTTON_MARGIN - HELPER_BUTTON_WIDTH;
    }

    /** 教程按钮相对条目顶部的 y 偏移：条目高 18px、按钮高 16px，上下各留 1px。 */
    private static int helperButtonOffsetY() {
        return (ITEM_VISUAL_HEIGHT - HELPER_BUTTON_HEIGHT) / 2;
    }

    /** 画一个教程按钮：深灰底 + 淡蓝色边框 + 白色居中文字。 */
    private void renderHelperButton(GuiGraphics graphics, HelperButton helper, int itemY) {
        int btnX = helperButtonX();
        int btnY = itemY + helperButtonOffsetY();
        graphics.fill(btnX, btnY, btnX + HELPER_BUTTON_WIDTH, btnY + HELPER_BUTTON_HEIGHT, 0xFF555555);
        graphics.renderOutline(btnX, btnY, HELPER_BUTTON_WIDTH, HELPER_BUTTON_HEIGHT, HELPER_BORDER_COLOR);
        graphics.drawCenteredString(this.font, helper.label(),
                btnX + HELPER_BUTTON_WIDTH / 2, btnY + (HELPER_BUTTON_HEIGHT - 8) / 2, 0xFFFFFF);
    }

    /**
     * 找出鼠标正下方的教程按钮。
     * <p>只遍历当前可见行，因此滚出视野或被过滤掉的条目不会留下可点击的幽灵按钮。
     */
    private HelperButton findClickedHelperButton(double mouseX, double mouseY) {
        List<ModEntry> filteredMods = getFilteredMods();
        int scrollOffset = scrollableArea.getScrollOffset();
        int maxVisible = scrollableArea.getMaxVisible();
        int btnX = helperButtonX();
        for (int i = 0; i < maxVisible && scrollOffset / ITEM_HEIGHT + i < filteredMods.size(); i++) {
            int index = scrollOffset / ITEM_HEIGHT + i;
            ModEntry entry = filteredMods.get(index);
            HelperButton helper = MOD_HELPER_BUTTONS.get(entry.bracketText);
            if (helper == null) continue;
            int btnY = LIST_TOP + i * ITEM_HEIGHT + helperButtonOffsetY();
            if (mouseX >= btnX && mouseX <= btnX + HELPER_BUTTON_WIDTH
                    && mouseY >= btnY && mouseY <= btnY + HELPER_BUTTON_HEIGHT) {
                return helper;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean scrolled = scrollableArea.mouseScrolled(mouseX, mouseY, delta, getFilteredMods().size() * ITEM_HEIGHT);
        return scrolled || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollableArea.updateScrollFromMouse(mouseY, this.width, getFilteredMods().size() * ITEM_HEIGHT);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (returnToConfigScreen) {
            returnToConfigScreen = false;
            Minecraft.getInstance().setScreen(new VerityConfigScreen());
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }
}