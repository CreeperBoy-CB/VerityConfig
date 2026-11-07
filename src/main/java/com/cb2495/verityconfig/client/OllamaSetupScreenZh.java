package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.util.Log;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 中文版的「本地 AI 设置（Ollama）」界面。
 * <p><b>布局严格照搬 Verity 原版</b> {@code varmite.verity.client.gui.OllamaSetupScreen}：
 * <ul>
 *   <li>顶部一排五个按钮：安装 / 刷新 / 取消下载 / 进度面板 / 卸载，y=32，高 20，间距 4，
 *       每个宽 {@code min(100, (width-20-16)/5)}</li>
 *   <li>模型列表占 {@code y=72} 到 {@code height-34}，行高 26，滚动条在右侧</li>
 *   <li>标题在 {@code (width/2, 8)}，状态行在 {@code (width/2, 20)}</li>
 *   <li>底部居中「完成」按钮，宽 200，高 20</li>
 * </ul>
 * <p><b>为什么自写而不是翻译原版：</b>原版界面的文字在构造时就写死了，
 * 而且 {@code render()} 每帧都会重拼英文；标题下那行状态是拼出来的，
 * 根本没有字段可改。自己画一份，文字从一开始就是中文。
 * <p><b>下载逻辑全部用 Verity 自己的</b>：本类只负责显示与转发点击，
 * 进度与模型清单都读自 {@code OllamaSetupManager} / {@code OllamaModels}。
 */
public final class OllamaSetupScreenZh extends Screen {

    // ============================================================
    // 原版布局常量（照抄，改动会破坏与原版的一致性）
    // ============================================================

    /** 列表起始 y。 */
    private static final int LIST_TOP = 72;
    /** 列表底部留白（给「完成」按钮）。 */
    private static final int LIST_BOTTOM_GAP = 34;
    /** 行高。 */
    private static final int ROW_HEIGHT = 26;
    /** 顶部按钮的 y。 */
    private static final int BUTTON_Y = 32;
    /** 按钮高度。 */
    private static final int BUTTON_HEIGHT = 20;
    /** 按钮之间的间距。 */
    private static final int BUTTON_GAP = 4;
    /** 顶部按钮个数。 */
    private static final int BUTTON_COUNT = 5;
    /** 顶部按钮最大宽度。 */
    private static final int BUTTON_MAX_WIDTH = 100;

    // ============================================================
    // 配色（与原版一致）
    // ============================================================

    private static final int COLOR_BAR_TRACK = -13882318;
    private static final int COLOR_ACCENT = -8913033;
    private static final int COLOR_PROGRESS = -8054;
    private static final int COLOR_FAILED = -36752;

    // ============================================================
    // 用到的 Verity 类名
    // ============================================================

    private static final String MANAGER = "varmite.verity.ollama.OllamaSetupManager";
    private static final String MODELS = "varmite.verity.ollama.OllamaModels";
    private static final String RUNTIME = "varmite.verity.ollama.OllamaRuntime";
    private static final String API = "varmite.verity.ollama.OllamaApi";
    private static final String HUD = "varmite.verity.client.gui.OllamaProgressHud";

    /** 反射类缓存。 */
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /** 返回上一个界面。 */
    private final Supplier<Screen> back;

    private OllamaSetupRows.ModelList list;
    private Button installButton;
    private Button cancelButton;
    private Button hudButton;
    private Button removeButton;

    /** 当前 Ollama 状态，null 表示还在检查。 */
    private volatile OllamaSetupRows.Status status;
    /** 已安装模型。 */
    private volatile List<OllamaSetupRows.InstalledModel> installed = List.of();
    /** 模型能力缓存。 */
    private volatile Map<String, Set<String>> capabilities = Map.of();

    private volatile boolean dataChanged;
    private volatile boolean refreshing;
    private int ticks;
    private Object lastPhase;
    private boolean logged;

    private OllamaSetupScreenZh(Supplier<Screen> back) {
        super(Component.literal("本地 AI 设置 (Ollama)"));
        this.back = back;
    }

    /**
     * 打开中文版本地 AI 设置。
     *
     * @param back 点「完成」时回到的界面
     * @return 新界面；Verity 没装时返回 null
     */
    public static Screen create(Screen back) {
        if (!ModList.get().isLoaded("verity")) return null;
        try {
            Class.forName(MANAGER);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
        return new OllamaSetupScreenZh(() -> back);
    }

    // ============================================================
    // 初始化（坐标照抄原版 init()）
    // ============================================================

    @Override
    protected void init() {
        list = new OllamaSetupRows.ModelList(minecraft, width, height, LIST_TOP,
                height - LIST_BOTTOM_GAP, ROW_HEIGHT);
        addRenderableWidget(list);

        int gap = BUTTON_GAP;
        int buttonWidth = Math.min(BUTTON_MAX_WIDTH, (width - 20 - gap * 4) / BUTTON_COUNT);
        int buttonX = (width - buttonWidth * BUTTON_COUNT - gap * 4) / 2;
        int y = BUTTON_Y;

        installButton = addRenderableWidget(Button.builder(
                        Component.literal("安装 Ollama"), b -> onInstall())
                .bounds(buttonX, y, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal(
                        "从 github.com/ollama 下载官方 Ollama 发行版并启动。"
                                + "无需管理员权限或安装窗口。模型将存储在您常规的 Ollama 文件夹中。"
                                + "\n\n文件源来自境外，可能需要加速器。")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("刷新"), b -> refresh())
                .bounds(buttonX + buttonWidth + gap, y, buttonWidth, BUTTON_HEIGHT)
                .build());

        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消下载"), b -> cancelDownload())
                .bounds(buttonX + (buttonWidth + gap) * 2, y, buttonWidth, BUTTON_HEIGHT)
                .build());

        hudButton = addRenderableWidget(Button.builder(hudLabel(), b -> toggleHud())
                .bounds(buttonX + (buttonWidth + gap) * 3, y, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal(
                        "在游戏中和菜单上显示或隐藏下载进度面板。按键绑定："
                                + hudKeyName() + " (可在 控制 > Verity Mod 中更改)。")))
                .build());

        removeButton = addRenderableWidget(Button.builder(
                        Component.literal("卸载 Ollama"), b -> onRemove())
                .bounds(buttonX + (buttonWidth + gap) * 4, y, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal(
                        "移除 Verity 安装的 Ollama 副本，可选择保留或删除您的模型。"
                                + "您自己安装的 Ollama 不会被修改，若需要卸载请使用其自带的卸载程序。")))
                .build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20)
                .build());

        rebuildList();
        refresh();
    }

    // ============================================================
    // 数据刷新
    // ============================================================

    /** 后台查询 Ollama 状态与模型清单，查完标脏等下一帧重建列表。 */
    private void refresh() {
        if (refreshing) return;
        refreshing = true;
        Thread thread = new Thread(() -> {
            try {
                fetchData();
            } catch (RuntimeException e) {
                // 查询失败按「未安装」显示，不要崩掉界面
            } finally {
                dataChanged = true;
                refreshing = false;
            }
        }, "verityconfig-ollama-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    /** 在后台线程里跑：只读 Verity 的数据，不碰界面对象。 */
    private void fetchData() {
        String endpoint = endpoint();
        Object version = queryVersion(endpoint);

        boolean local = isLocalEndpoint(endpoint);
        boolean exe = local && runtimeHasExecutable();
        boolean managed = local && runtimeHasManagedInstall();

        OllamaSetupRows.Status found = new OllamaSetupRows.Status(
                version != null, version == null ? "" : String.valueOf(version),
                exe, local, managed, endpoint);

        List<OllamaSetupRows.InstalledModel> models =
                version != null ? queryModels(endpoint) : List.of();
        Map<String, Set<String>> caps = new HashMap<>();
        for (OllamaSetupRows.InstalledModel model : models) {
            caps.put(model.name(), queryCapabilities(endpoint, model.name()));
        }

        // 一次性赋值，避免界面读到半更新的状态
        this.status = found;
        this.installed = models;
        this.capabilities = caps;
    }

    // ============================================================
    // 列表内容（照抄原版 rebuildList()）
    // ============================================================

    private void rebuildList() {
        if (list == null) return;
        double scroll = list.getScrollAmount();
        list.clear();

        OllamaSetupRows.Status current = status;
        list.add(new OllamaSetupRows.HeaderRow(
                Component.literal("已安装在此电脑上").withStyle(ChatFormatting.YELLOW)));

        if (current == null) {
            list.add(new OllamaSetupRows.InfoRow("检查中..."));
        } else if (!current.running()) {
            if (current.local()) {
                list.add(new OllamaSetupRows.InfoRow(current.installed()
                        ? "Ollama 未运行。请按上方的“启动 Ollama”。"
                        : "Ollama 尚未安装。请按上方的“安装 Ollama”，或者直接在下方选择一个模型。"));
            } else {
                list.add(new OllamaSetupRows.InfoRow(
                        "在 " + current.endpoint() + " 无 Ollama 服务器响应"));
            }
        } else if (installed.isEmpty()) {
            list.add(new OllamaSetupRows.InfoRow("暂无模型 - 请在下方下载一个。"));
        } else {
            for (OllamaSetupRows.InstalledModel model : installed) {
                Set<String> caps = capabilities.getOrDefault(model.name(), Set.of());
                // 纯向量模型不能对话，原版也是直接跳过
                if (caps.contains("embedding") && !caps.contains("completion")) continue;

                String detail = String.join(" ", nonEmpty(
                        model.parameterSize(), model.quantization(), bytes(model.sizeBytes())));

                String note;
                boolean warn;
                if (caps.isEmpty()) {
                    note = "";
                    warn = false;
                } else if (caps.contains("tools")) {
                    // 这里标的是模型能力，指它「会不会深度思考」，不是正在思考
                    note = caps.contains("thinking") ? "工具 & 深度思考" : "工具";
                    warn = false;
                } else {
                    note = "不支持工具 - Verity 只能与其聊天";
                    warn = true;
                }

                list.add(new OllamaSetupRows.ModelRow(this, model.name(), detail, note,
                        warn, model.sizeBytes()));
            }
        }

        list.add(new OllamaSetupRows.HeaderRow(Component.literal(
                "下载一个小模型 (低于 12B，均可使用 Verity 的工具)").withStyle(ChatFormatting.YELLOW)));

        for (OllamaSetupRows.Recommended model : recommendedModels()) {
            // 「thinking」在这里是模型的属性（该模型是否支持深度思考），不是进行时
            String detail = model.params() + " " + bytes(model.downloadBytes())
                    + (model.thinking() ? "  深度思考" : "");
            list.add(new OllamaSetupRows.ModelRow(this, model.tag(), detail,
                    translateBlurb(model.blurb()), false, -1L));
        }

        list.setScrollAmount(scroll);
    }

    /** 每 tick：状态变化或每 60 tick 刷新；数据变了就重建列表。 */
    @Override
    public void tick() {
        ticks++;
        Object phase = snapshotPhase();
        if (!Objects.equals(phase, lastPhase)) {
            lastPhase = phase;
            refresh();
        } else if (ticks % 60 == 0) {
            refresh();
        }
        if (dataChanged) {
            dataChanged = false;
            rebuildList();
        }
    }

    // ============================================================
    // 按钮状态（照抄原版 updateButtons()）
    // ============================================================

    private void updateButtons() {
        OllamaSetupRows.Status current = status;
        boolean busy = isBusy();

        if (busy) {
            installButton.setMessage(Component.literal("处理中..."));
            installButton.active = false;
        } else if (current == null) {
            installButton.setMessage(Component.literal("检查中..."));
            installButton.active = false;
        } else if (current.running()) {
            installButton.setMessage(Component.literal("Ollama 已就绪").withStyle(ChatFormatting.GREEN));
            installButton.active = false;
        } else if (!current.local()) {
            installButton.setMessage(Component.literal("服务器离线"));
            installButton.active = false;
        } else {
            installButton.setMessage(Component.literal(
                    current.installed() ? "启动 Ollama" : "安装 Ollama"));
            installButton.active = true;
        }

        cancelButton.active = cancellable();
        removeButton.active = !busy && current != null && current.local() && current.managed();
        hudButton.setMessage(hudLabel());
    }

    // ============================================================
    // 绘制
    // ============================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        renderBackground(graphics);
        list.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(font, statusLine(), width / 2, 20, 0xFFFFFF);

        renderProgressBar(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 标题下方那条进度，位置与配色同原版。 */
    private void renderProgressBar(GuiGraphics graphics) {
        Object snapshot = snapshot();
        if (snapshot == null || !progressVisible(snapshot)) return;

        int barWidth = Math.min(360, width - 20);
        int barX = (width - barWidth) / 2;
        String text = progressText(snapshot);
        int color = progressColor(snapshot);
        graphics.drawString(font, OllamaSetupRows.ellipsize(font, text, barWidth),
                barX, 56, color, false);
        graphics.fill(barX, 66, barX + barWidth, 69, COLOR_BAR_TRACK);

        float fraction = "DONE".equals(String.valueOf(field(snapshot, "phase")))
                ? 1.0F : fractionOf(snapshot);
        if (fraction > 0) {
            graphics.fill(barX, 66, barX + Math.round(barWidth * fraction), 69, color);
        }
    }

    /** 标题下那行状态：原版是拼出来的，这里直接给中文。 */
    private Component statusLine() {
        OllamaSetupRows.Status current = status;
        Component suffix = Component.literal("   ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Verity 使用中：").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(currentProvider()).withStyle(ChatFormatting.WHITE));

        Component base;
        if (current == null) {
            base = Component.literal("正在寻找 Ollama...").withStyle(ChatFormatting.GRAY);
        } else if (current.running()) {
            base = Component.literal("Ollama " + current.version() + " 正在运行")
                    .withStyle(ChatFormatting.GREEN);
        } else if (!current.local()) {
            base = Component.literal("在 " + current.endpoint() + " 无 Ollama 服务器响应")
                    .withStyle(ChatFormatting.RED);
        } else if (current.installed()) {
            base = Component.literal("Ollama 已安装但未运行").withStyle(ChatFormatting.GOLD);
        } else {
            base = Component.literal("Ollama 未安装").withStyle(ChatFormatting.RED);
        }
        return base.copy().append(suffix);
    }

    /** Verity 当前用的 AI 模型名，未选时显示「自动」。 */
    private static String currentProvider() {
        try {
            String provider = String.valueOf(VeritySpec.get("AI_PROVIDER"));
            if (!"OLLAMA".equalsIgnoreCase(provider)) return provider;
            Object model = VeritySpec.get("AI_MODEL");
            String text = model == null ? "" : String.valueOf(model);
            return text.isBlank() ? "自动" : text;
        } catch (RuntimeException e) {
            return "自动";
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back.get());
    }

    // ============================================================
    // 按钮动作
    // ============================================================

    private void onInstall() {
        invokeStatic(MANAGER, "installOllama", new Class<?>[0]);
    }

    private void cancelDownload() {
        invokeStatic(MANAGER, "cancel", new Class<?>[0]);
    }

    private void toggleHud() {
        OllamaHudTranslator.toggleHud();
        hudButton.setMessage(hudLabel());
    }

    /** 卸载走 Verity 自己的确认界面，由它处理「保留模型 / 删除模型」。 */
    private void onRemove() {
        try {
            Class<?> cls = verityClass("varmite.verity.client.gui.OllamaUninstallScreen");
            if (cls == null) return;
            @SuppressWarnings("unchecked")
            Consumer<Boolean> callback = chosen -> {
                invokeStatic(MANAGER, "uninstallOllama",
                        new Class<?>[]{boolean.class}, Boolean.TRUE.equals(chosen));
                minecraft.setScreen(this);
            };
            Screen screen = (Screen) cls.getConstructor(Screen.class, Consumer.class)
                    .newInstance(this, callback);
            minecraft.setScreen(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.warn("打不开 Ollama 卸载确认界面: " + e);
        }
    }

    /** 删除单个模型：先弹确认框，确认后交给 Verity 删。 */
    void confirmDelete(String tag, long size) {
        StringBuilder body = new StringBuilder("从此电脑删除 ").append(tag);
        if (size > 0) body.append(" 并释放 ").append(bytes(size));
        body.append("。");
        if (isCurrent(tag)) {
            body.append(" Verity 正在使用它，因此它会自动选择另一个已安装的模型。");
        }
        minecraft.setScreen(new ConfirmScreen(
                accepted -> {
                    if (accepted) deleteModel(tag);
                    minecraft.setScreen(this);
                },
                Component.literal("删除 " + tag + "？"),
                Component.literal(body.toString()),
                Component.literal("删除").withStyle(ChatFormatting.RED),
                CommonComponents.GUI_CANCEL));
    }
    // ============================================================
    // 反射工具
    // ============================================================

    private static Class<?> verityClass(String name) {
        return CLASS_CACHE.computeIfAbsent(name, key -> {
            try {
                return Class.forName(key);
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
        });
    }

    private static Object invokeStatic(String className, String method, Class<?>[] types,
                                       Object... args) {
        try {
            Class<?> cls = verityClass(className);
            if (cls == null) return null;
            return cls.getMethod(method, types).invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** 读 record 的字段（record 的访问器就是同名方法）。 */
    static Object field(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // ============================================================
    // 供 OllamaSetupRows 使用的静态访问器
    // ============================================================

    static Object snapshotStatic() {
        return invokeStatic(MANAGER, "snapshot", new Class<?>[0]);
    }

    private static Object snapshot() {
        return snapshotStatic();
    }

    private static Object snapshotPhase() {
        return field(snapshotStatic(), "phase");
    }

    static boolean isBusyStatic() {
        return invokeStatic(MANAGER, "isBusy", new Class<?>[0]) instanceof Boolean b && b;
    }

    private static boolean isBusy() {
        return isBusyStatic();
    }

    static float fractionOfStatic(Object snapshot) {
        return fractionOf(snapshot);
    }

    private static float fractionOf(Object snapshot) {
        Object result = field(snapshot, "fraction");
        return result instanceof Float f ? f : 0.0F;
    }

    static String statsStatic(Object snapshot) {
        Object result = invokeStatic(HUD, "stats", new Class<?>[]{snapshot.getClass()}, snapshot);
        return result == null ? "" : String.valueOf(result);
    }

    static String activeModelStatic() {
        try {
            Object result = invokeStatic(MANAGER, "activeModel", new Class<?>[0]);
            if (result instanceof Optional<?> opt) {
                return opt.isPresent() ? String.valueOf(opt.get()) : "";
            }
        } catch (RuntimeException e) {
            // 忽略
        }
        return "";
    }

    static String withTagStatic(String name) {
        return withTag(name);
    }

    static boolean isCurrentStatic(String tag) {
        return isCurrent(tag);
    }

    static void selectModelStatic(String tag) {
        selectModel(tag);
    }

    static void downloadModelStatic(String tag) {
        downloadModel(tag);
    }

    /** 让行能读到当前状态。 */
    OllamaSetupRows.Status statusStatic() {
        return status;
    }

    /**
     * 这个模型是否已经在已安装清单里。
     * <p>直接查预取好的 {@link #installed}，不调 Verity 的 {@code isInstalled}——
     * 后者虽然只读缓存，但每帧每行都反射调用一次没有必要。
     */
    boolean isInstalledInList(String tag) {
        String wanted = withTag(tag);
        for (OllamaSetupRows.InstalledModel model : installed) {
            if (withTag(model.name()).equalsIgnoreCase(wanted)) return true;
        }
        return false;
    }

    static String formatBytes(long value) {
        return bytes(value);
    }

    // ============================================================
    // Verity 数据访问
    // ============================================================

    private static String endpoint() {
        Object result = invokeStatic(MANAGER, "endpoint", new Class<?>[0]);
        return result == null ? "" : String.valueOf(result);
    }

    private static boolean runtimeHasExecutable() {
        Object result = invokeStatic(RUNTIME, "findExecutable", new Class<?>[0]);
        return result instanceof Optional<?> opt && opt.isPresent();
    }

    private static boolean runtimeHasManagedInstall() {
        return invokeStatic(RUNTIME, "hasManagedInstall", new Class<?>[0]) instanceof Boolean b && b;
    }

    private static boolean isLocalEndpoint(String endpoint) {
        Object result = invokeStatic(RUNTIME, "isLocalEndpoint",
                new Class<?>[]{String.class}, endpoint);
        return !(result instanceof Boolean b) || b;
    }

    /** 查 Ollama 版本，查不到就当成没运行。 */
    private static Object queryVersion(String endpoint) {
        Object result = invokeStatic(API, "version",
                new Class<?>[]{String.class, java.time.Duration.class},
                endpoint, java.time.Duration.ofSeconds(2));
        if (result instanceof Optional<?> opt) return opt.orElse(null);
        return null;
    }

    private static List<OllamaSetupRows.InstalledModel> queryModels(String endpoint) {
        Object result = invokeStatic(API, "installed",
                new Class<?>[]{String.class, boolean.class}, endpoint, true);
        if (!(result instanceof List<?> raw)) return List.of();
        List<OllamaSetupRows.InstalledModel> models = new ArrayList<>();
        for (Object item : raw) {
            if (item == null) continue;
            models.add(new OllamaSetupRows.InstalledModel(
                    String.valueOf(field(item, "name")),
                    asLong(field(item, "sizeBytes")),
                    String.valueOf(field(item, "parameterSize")),
                    String.valueOf(field(item, "quantization"))));
        }
        return models;
    }

    private static Set<String> queryCapabilities(String endpoint, String name) {
        Object result = invokeStatic(MODELS, "capabilities",
                new Class<?>[]{String.class, String.class}, endpoint, name);
        if (result instanceof Set<?> raw) {
            Set<String> caps = new HashSet<>();
            for (Object cap : raw) if (cap != null) caps.add(String.valueOf(cap));
            return Collections.unmodifiableSet(caps);
        }
        return Set.of();
    }

    /** Verity 内置的推荐下载清单。 */
    private static List<OllamaSetupRows.Recommended> recommendedModels() {
        try {
            Class<?> cls = verityClass(MODELS);
            if (cls == null) return List.of();
            Object raw = cls.getField("RECOMMENDED").get(null);
            if (!(raw instanceof List<?> list)) return List.of();
            List<OllamaSetupRows.Recommended> out = new ArrayList<>();
            for (Object item : list) {
                if (item == null) continue;
                out.add(new OllamaSetupRows.Recommended(
                        String.valueOf(field(item, "tag")),
                        String.valueOf(field(item, "params")),
                        asLong(field(item, "downloadBytes")),
                        Boolean.TRUE.equals(field(item, "thinking")),
                        String.valueOf(field(item, "blurb"))));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    /**
     * 模型说明的英文原文。
     * <p>来源是 {@code OllamaModels.RECOMMENDED} 里每个 {@code Recommended.blurb}，
     * 译文严格取自 {@code hardcode_ts.txt} 的 {@code OllamaModels.java} 一节。
     */
    private static final List<String[]> BLURBS = List.of(
            new String[]{"Best all-rounder. Smart and quick on most gaming PCs (6 GB+ VRAM).",
                    "最佳全能选手。在大多数游戏电脑上运行聪明且迅速 (6 GB+ 显存)。"},
            new String[]{"Fastest replies - no thinking step. Good tool use. 4 GB+ VRAM.",
                    "回复最快 - 无思考过程。工具调用能力良好。4 GB+ 显存。"},
            new String[]{"Very light. For laptops and older GPUs, or CPU only.",
                    "非常轻量。适用于笔记本电脑、较旧的 GPU 或仅使用 CPU。"},
            new String[]{"Tiny model built for tool calling. Runs almost anywhere.",
                    "专为工具调用构建的微型模型。几乎可以在任何设备上运行。"},
            new String[]{"Accurate tool use with Thinking on. Needs 8 GB VRAM.",
                    "开启思考模式后工具调用精准。需要 8 GB 显存。"},
            new String[]{"Mistral's edge model. Strong chat, no thinking step. 8 GB+ VRAM.",
                    "Mistral 的边缘模型。强大的聊天能力，无思考过程。8 GB+ 显存。"},
            new String[]{"Smartest under 12B. Wants 10 GB+ VRAM next to Minecraft.",
                    "12B 以下最聪明的模型。与 Minecraft 同时运行需要 10 GB+ 显存。"});

    /**
     * 把模型说明换成中文。
     * <p>原文里含 {@code '} 之类的字符，可能因版本微调而不完全一致，
     * 所以先精确匹配，再退回到按前缀匹配；都不中时原样返回，不吞掉说明。
     */
    private static String translateBlurb(String blurb) {
        if (blurb == null || blurb.isBlank() || "null".equals(blurb)) return "";
        for (String[] pair : BLURBS) {
            if (pair[0].equals(blurb)) return pair[1];
        }
        // 前缀兜底：只比较前 24 个字符，容忍句尾标点差异
        for (String[] pair : BLURBS) {
            String a = pair[0];
            String prefix = a.substring(0, Math.min(24, a.length()));
            if (blurb.startsWith(prefix)) return pair[1];
        }
        return blurb;
    }

    private static void deleteModel(String tag) {
        if (invokeStatic(MANAGER, "deleteModel", new Class<?>[]{String.class}, tag) == null) {
            // 方法无返回值，调用失败时日志已经由 invokeStatic 吞掉，这里不额外处理
        }
    }

    private static void selectModel(String tag) {
        invokeStatic(MANAGER, "selectModel", new Class<?>[]{String.class}, tag);
        // 选了就顺手预热，避免第一次对话卡住
        invokeStatic("varmite.verity.entity.llm.AiAPI", "warmUpOllamaAsync", new Class<?>[0]);
    }

    private static void downloadModel(String tag) {
        invokeStatic(MANAGER, "downloadModel", new Class<?>[]{String.class}, tag);
    }

    /** 当前 AI 服务商是不是这个 Ollama 模型。 */
    private static boolean isCurrent(String tag) {
        try {
            String provider = String.valueOf(VeritySpec.get("AI_PROVIDER"));
            if (!"OLLAMA".equalsIgnoreCase(provider)) return false;
            Object model = VeritySpec.get("AI_MODEL");
            String text = model == null ? "" : String.valueOf(model);
            return withTag(text).equalsIgnoreCase(withTag(tag));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String withTag(String name) {
        if (name == null || name.isBlank()) return "";
        return name.contains(":") ? name : name + ":latest";
    }

    /** 当前状态是否允许取消下载（删模型时不给取消）。 */
    private static boolean cancellable() {
        Object snapshot = snapshot();
        if (snapshot == null) return false;
        if (!"RUNNING".equals(String.valueOf(field(snapshot, "phase")))) return false;
        String title = String.valueOf(field(snapshot, "title"));
        return !title.startsWith("Deleting") && !title.startsWith("Removing");
    }

    // ============================================================
    // HUD 相关
    // ============================================================

    /** 进度面板开关按钮的文字。 */
    private static Component hudLabel() {
        boolean on = OllamaHudTranslator.isHudEnabled();
        return Component.literal("进度面板：" + (on ? "开启" : "关闭"));
    }

    private static String hudKeyName() {
        Object result = invokeStatic(HUD, "keyName", new Class<?>[0]);
        return result == null ? "" : String.valueOf(result);
    }

    private static boolean progressVisible(Object snapshot) {
        Object result = invokeStatic(HUD, "shouldShow",
                new Class<?>[]{snapshot.getClass()}, snapshot);
        return result instanceof Boolean b && b;
    }

    private static String progressText(Object snapshot) {
        String title = String.valueOf(field(snapshot, "title"));
        boolean running = "RUNNING".equals(String.valueOf(field(snapshot, "phase")));
        String extra = running ? statsStatic(snapshot) : String.valueOf(field(snapshot, "detail"));
        return title + "  " + extra;
    }

    private static int progressColor(Object snapshot) {
        return switch (String.valueOf(field(snapshot, "phase"))) {
            case "FAILED" -> COLOR_FAILED;
            case "DONE" -> COLOR_ACCENT;
            default -> COLOR_PROGRESS;
        };
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /** Verity 的字节格式化；取不到时退回本地实现。 */
    private static String bytes(long value) {
        if (value < 0) return "";
        Object result = invokeStatic(HUD, "bytes", new Class<?>[]{long.class}, value);
        if (result != null) return String.valueOf(result);
        if (value < 1024L * 1024L) return String.format(Locale.ROOT, "%.0f KB", value / 1024.0);
        if (value < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", value / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.1f GB", value / (1024.0 * 1024.0 * 1024.0));
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : -1L;
    }

    /** 去掉空串，其余原样返回。 */
    private static List<String> nonEmpty(String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equals(value)) out.add(value);
        }
        return out;
    }
}
