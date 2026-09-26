package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.PlatformUtils;
import com.cb2495.verityconfig.util.ScrollableArea;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

    private List<String> findDependents(String bracketText) {
        List<String> dependents = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : MOD_DEPENDENCIES.entrySet()) {
            if (entry.getValue().contains(bracketText)) {
                dependents.add(entry.getKey());
            }
        }
        return dependents;
    }

    private void toggleMod(ModEntry entry) {
        File source = entry.file;
        String name = source.getName();
        if (entry.disabled) {
            List<String> deps = MOD_DEPENDENCIES.get(entry.bracketText);
            if (deps != null && !deps.isEmpty()) {
                List<ModEntry> disabledDeps = new ArrayList<>();
                for (String depName : deps) {
                    ModEntry dep = findModByBracket(depName);
                    if (dep != null && dep.disabled) {
                        disabledDeps.add(dep);
                    }
                }
                if (!disabledDeps.isEmpty()) {
                    showDependencyConfirm(entry, disabledDeps);
                    return;
                }
            }
            if (!canEnableOnThisPlatform(entry.bracketText)) {
                showPlatformReject(entry.bracketText);
                return;
            }
            File target = new File(source.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
            if (target.exists()) target.delete();
            source.renameTo(target);
        } else {
            List<String> dependents = findDependents(entry.bracketText);
            if (!dependents.isEmpty()) {
                showDisableDependentsConfirm(entry, dependents);
                return;
            }
            File target = new File(source.getParentFile(), name + ".disabled");
            if (target.exists()) target.delete();
            source.renameTo(target);
        }
        loadModList();
        scrollableArea.clampScroll(getFilteredMods().size() * ITEM_HEIGHT);
    }

    private void showDependencyConfirm(ModEntry target, List<ModEntry> disabledDeps) {
        Minecraft mc = Minecraft.getInstance();
        List<String> depNames = new ArrayList<>();
        for (ModEntry dep : disabledDeps) {
            depNames.add(dep.bracketText);
        }
        String depList = String.join("、", depNames);
        mc.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                for (ModEntry dep : disabledDeps) {
                    enableMod(dep);
                }
                enableMod(target);
                loadModList();
            }
            Minecraft.getInstance().setScreen(new ModsListScreen());
        },
                Component.literal("启用模组需要前置"),
                Component.literal("该模组需要以下前置模组才能使用：\n" + depList + "\n是否同时启用它们？")));
    }

    private void showDisableDependentsConfirm(ModEntry target, List<String> dependents) {
        Minecraft mc = Minecraft.getInstance();
        String depList = String.join("、", dependents);
        mc.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                disableMod(target);
                loadModList();
            }
            Minecraft.getInstance().setScreen(new ModsListScreen());
        },
                Component.literal("禁用模组警告"),
                Component.literal("以下模组依赖于此模组，禁用后可能导致无法启动：\n" + depList + "\n是否继续禁用？")));
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

        for (int i = 0; i < maxVisible && scrollOffset / ITEM_HEIGHT + i < filteredMods.size(); i++) {
            int index = scrollOffset / ITEM_HEIGHT + i;
            ModEntry entry = filteredMods.get(index);
            int y = listTop + i * ITEM_HEIGHT;

            int bgColor = (i % 2 == 0) ? 0x80000000 : 0x88000000;
            graphics.fill(left, y, right, y + ITEM_VISUAL_HEIGHT, bgColor);

            // 平台不支持时该条目边框变红，并在右侧显示原因，提示会自动淡出
            boolean rejected = rejectAlpha > 0f
                    && entry.bracketText.equals(rejectedName);
            if (rejected) {
                graphics.renderOutline(left, y, right - left, ITEM_VISUAL_HEIGHT,
                        withAlpha(0xFFFF5555, rejectAlpha));
            }

            int cbX = left + 4;
            int cbY = y + checkboxOffsetY();
            graphics.renderOutline(cbX, cbY, CHECKBOX_SIZE, CHECKBOX_SIZE, 0xFFAAAAAA);
            if (!entry.disabled) {
                graphics.fill(cbX + 2, cbY + 2, cbX + CHECKBOX_SIZE - 2, cbY + CHECKBOX_SIZE - 2, 0xFFFFFFFF);
            }

            // 被拒绝的条目：红色警告淡出，原信息同步淡入。
            // 关键：normalAlpha 只在 rejected 为真时才降低，
            // 若写成「全屏共用的 1 - rejectAlpha」，警告满不透明时
            // normalAlpha 会变成 0，导致所有正常条目被一起跳过、整页消失。
            float normalAlpha = rejected ? 1f - rejectAlpha : 1f;

            if (rejected) {
                Component hint = platformRejectHint();
                float shake = rejectShakeOffset();
                int hintX = cbX + CHECKBOX_SIZE + 6 + (int) shake;
                int hintY = y + (ITEM_VISUAL_HEIGHT - 8) / 2;
                graphics.drawString(this.font, hint, hintX, hintY,
                        withAlpha(0xFFFF5555, rejectAlpha), false);
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

            if (entry.bracketText.equals("Oculus")) {
                int btnX = this.width - 100;
                int btnY = y + 2;
                int btnWidth = 90;
                int btnHeight = 16;
                graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, 0xFF555555);
                graphics.renderOutline(btnX, btnY, btnWidth, btnHeight, 0xFFAAAAAA);
                graphics.drawCenteredString(this.font, "如何安装光影", btnX + btnWidth / 2, btnY + (btnHeight - 8) / 2, 0xFFFFFF);
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
    }

    private void drawFilterCheckbox(GuiGraphics graphics, int x, int y, boolean checked, String label) {
        graphics.renderOutline(x, y, FILTER_CHECKBOX_SIZE, FILTER_CHECKBOX_SIZE, 0xFFAAAAAA);
        if (checked) {
            graphics.fill(x + 2, y + 2, x + FILTER_CHECKBOX_SIZE - 2, y + FILTER_CHECKBOX_SIZE - 2, 0xFFFFFFFF);
        }
        graphics.drawString(this.font, label, x + FILTER_CHECKBOX_SIZE + 4, y + 1, 0xFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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

        if (button == 0 && isMouseOverOculusButton(mouseX, mouseY)) {
            Minecraft.getInstance().setScreen(new HelperScreen("shader_install", this));
            return true;
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

    private boolean isMouseOverOculusButton(double mouseX, double mouseY) {
        List<ModEntry> filteredMods = getFilteredMods();
        int scrollOffset = scrollableArea.getScrollOffset();
        int maxVisible = scrollableArea.getMaxVisible();
        for (int i = 0; i < maxVisible && scrollOffset / ITEM_HEIGHT + i < filteredMods.size(); i++) {
            int index = scrollOffset / ITEM_HEIGHT + i;
            ModEntry entry = filteredMods.get(index);
            if (entry.bracketText.equals("Oculus")) {
                int y = LIST_TOP + i * ITEM_HEIGHT;
                int btnX = this.width - 100;
                int btnY = y + 2;
                int btnWidth = 90;
                int btnHeight = 16;
                return mouseX >= btnX && mouseX <= btnX + btnWidth &&
                        mouseY >= btnY && mouseY <= btnY + btnHeight;
            }
        }
        return false;
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