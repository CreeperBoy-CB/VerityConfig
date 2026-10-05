package com.cb2495.verityconfig.screen;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.config.ModConfig;
import com.cb2495.verityconfig.config.Providers;
import com.cb2495.verityconfig.config.VerityConfigManager;
import com.cb2495.verityconfig.util.DebouncedTask;
import com.cb2495.verityconfig.util.Log;
import com.cb2495.verityconfig.util.PlatformUtils;
import com.cb2495.verityconfig.widget.AutoSaveCheckbox;
import com.cb2495.verityconfig.widget.DropdownWidget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.util.*;

@SuppressWarnings("removal")
public class VerityConfigScreen extends Screen {

    // ---------- 提供商与模型数据 ----------
    // 提供商清单（端点、API 格式、Key 地址、可选模型）统一在 Providers 里维护，
    // 这里不再自己存一份，避免两处清单不一致。

    // ---------- 布局常量 ----------
    /** 控件区首行 Y 的下限（屏幕很矮时）。 */
    private static final int MIN_START_Y = 30;
    /** 控件区首行 Y 的上限（屏幕很高时）。 */
    private static final int MAX_START_Y = 48;
    /** 首行 Y 达到下限 MIN_START_Y 的窗口高度。 */
    private static final int START_Y_RAMP_FROM = 262;
    /** 首行 Y 达到上限 MAX_START_Y 的窗口高度。 */
    private static final int START_Y_RAMP_TO = 540;
    /** 底部留白至少要占屏幕高的这个分母之一（即 ≥ 屏幕高 / 9，向下取整）。 */
    private static final int BOTTOM_MARGIN_DIVISOR = 9;
    /** 底部留白约束无法满足时，首行改用这个固定值。 */
    private static final int FALLBACK_START_Y = 32;
    /** 控件区总高（首行顶部 → 完成按钮底部）。 */
    private static final int CONTENT_HEIGHT = 200;
    /** 各行相对首行的偏移，行高统一 20（提供商行偏移为 0，直接用首行 Y）。 */
    private static final int ROW_API_KEY = 30;
    private static final int ROW_ENDPOINT = 60;
    private static final int ROW_MODEL = 90;
    /** 语音朗读行（在深度思考之上）。 */
    private static final int ROW_TTS = 120;
    /** 深度思考行。 */
    private static final int ROW_THINK = 150;
    private static final int ROW_DONE = 180;
    /** 模型选「自定义」时，下拉框收缩到的宽度；余下空间给输入框。 */
    private static final int MODEL_DROPDOWN_NARROW_WIDTH = 56;
    /** 模型下拉框完整宽度（展开列表也用这个宽度，避免长选项被截断）。 */
    private static final int MODEL_DROPDOWN_WIDTH = 204;
    /**
     * 自定义模型输入框相对模型行的微调：下移、左移、以及宽度的收窄量。
     * <p>
     * 宽度 = 行右边缘 - {@link #modelFieldX}，而左边缘已含 {@link #MODEL_FIELD_X_NUDGE}，
     * 左移 1px 会让宽度自然 +1。要得到净 -1 的宽度变化，这里必须多减 1。
     */
    private static final int MODEL_FIELD_Y_NUDGE = 1;
    private static final int MODEL_FIELD_X_NUDGE = -1;
    private static final int MODEL_FIELD_WIDTH_NUDGE = -2;
    /**
     * 语音下拉框左边缘相对窗口中心（w/2）的偏移。
     * 不用 font.width("语音朗读") 计算，避免随字体文件漂移。
     * -30 时与勾选框文字「语音朗读」右侧留约 2px 间隙；数值每 +1 即右移 1px。
     */
    private static final int TTS_DROPDOWN_OFFSET = -30;

    // ---------- 平台限制（手机端选 Verity™） ----------
    /** 手机端不可选中的语音模型值。 */
    private static final String TTS_VALUE_LOCAL = "LOCAL";

    // ---------- 控件 ----------
    private EditBox apiKeyField, endpointField, customModelField;
    private Button apiKeyActionButton;
    private Checkbox thinkCheckbox;
    private Checkbox ttsCheckbox;
    private DropdownWidget<String> ttsProviderDropdown;
    private DropdownWidget<String> providerDropdown;
    private DropdownWidget<Providers.Model> modelDropdown;

    private String selectedProvider = Providers.names().get(0);
    private VerityConfigManager.ConfigData loadedConfig;

    /**
     * 去抖保存：输入框停止输入 0.7 秒后才真正写盘。
     * <p>原本每个按键都会走一次「读-改-写」两个配置文件，这里把连续输入
     * 合并成一次；保存本身交给后台线程，渲染线程不再碰磁盘。
     */
    private final DebouncedTask pendingSave = new DebouncedTask(this::saveConfig);

    // ---------- 赞助相关 ----------
    private ResourceLocation sponsorTexture;
    private int sponsorTexWidth, sponsorTexHeight;
    private boolean sponsorTextureLoaded = false;
    private boolean sponsorExpanded = false;
    private Button sponsorButton;

    public VerityConfigScreen() {
        super(Component.literal("Verity AI 配置"));
    }

    @Override
    protected void init() {
        super.init();
        int w = this.width;
        int startY = contentStartY();

        loadSponsorTexture();
        // 由配置决定是否显示：ShowSponsor 为 false 时永不展开，即使空间足够
        sponsorExpanded = ModConfig.isShowSponsor() && isSponsorSpaceSufficient();

        loadedConfig = VerityConfigManager.loadVerityConfig();
        selectedProvider = VerityConfigManager.inferProviderFromEndpoint(loadedConfig.endpoint, selectedProvider);

        // 1. 提供商下拉框 + 获取Key按钮
        this.providerDropdown = new DropdownWidget<>(
                w / 2 - 101, startY, 156, 22,
                Component.literal("提供商"),
                Providers.names(),
                selectedProvider,
                value -> Component.literal(value),
                value -> {
                    selectedProvider = value;
                    String endpoint = VerityConfigManager.getEndpointForProvider(value);
                    if (!endpoint.isEmpty()) endpointField.setValue(endpoint);
                    List<Providers.Model> models = Providers.modelsFor(value);
                    if (!models.isEmpty()) loadedConfig.model = models.get(0).name();
                    else loadedConfig.model = "";
                    rebuildModelWidgets();
                    // 选提供商是离散操作，立即保存；runNow() 同时取消已排队的去抖
                    pendingSave.runNow();
                }
        );
        this.addRenderableWidget(this.providerDropdown);

        this.addRenderableWidget(Button.builder(Component.literal("获取 Key"), btn -> {
            String url = Providers.keyUrlFor(selectedProvider);
            if (url != null && !url.isEmpty()) {
                Minecraft.getInstance().setScreen(new ConfirmLinkScreen(
                        confirmed -> {
                            if (confirmed) Util.getPlatform().openUri(url);
                            Minecraft.getInstance().setScreen(this);
                        },
                        url, false
                ));
            } else {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("该提供商暂无预设链接，请自行获取"), false);
                } else {
                    Minecraft.getInstance().gui.setOverlayMessage(
                            Component.literal("该提供商暂无预设链接，请自行获取"), false);
                }
            }
        }).pos(w / 2 + 55, startY).size(48, 22).build());

        // 2. API Key 输入框
        this.apiKeyField = new EditBox(this.font, w / 2 - 100, startY + ROW_API_KEY, 180, 20, Component.literal("API Key"));
        this.apiKeyField.setMaxLength(512);
        this.apiKeyField.setValue(loadedConfig.apiKey);
        this.apiKeyField.setHint(Component.literal("请输入 API Key")
                .withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC));
        this.addRenderableWidget(this.apiKeyField);
        // 输入过程只安排一次去抖保存，停止输入 0.7s 后才写盘
        this.apiKeyField.setResponder(s -> {
            updateApiKeyActionButton();
            pendingSave.schedule();
        });

        this.apiKeyActionButton = Button.builder(Component.literal(""), btn -> {
            if (apiKeyField.getValue().isEmpty()) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (!clipboard.isEmpty()) apiKeyField.setValue(clipboard);
            } else {
                apiKeyField.setValue("");
            }
            updateApiKeyActionButton();
            // 粘贴/清除是「点一下就该生效」的操作，不走去抖，立即保存
            pendingSave.runNow();
        }).pos(apiKeyField.getX() + apiKeyField.getWidth() + 1, apiKeyField.getY() - 1).size(22, 22).build();
        this.addRenderableWidget(this.apiKeyActionButton);
        updateApiKeyActionButton();

        // 3. Base URL
        this.endpointField = new EditBox(this.font, w / 2 - 100, startY + ROW_ENDPOINT, 202, 22, Component.literal("Base URL"));
        this.endpointField.setMaxLength(128);
        this.endpointField.setValue(loadedConfig.endpoint.isEmpty()
                ? VerityConfigManager.getEndpointForProvider(selectedProvider)
                : loadedConfig.endpoint);
        this.endpointField.setHint(Component.literal("请输入 Base URL")
                .withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC));
        this.addRenderableWidget(this.endpointField);
        // Base URL 同样是打字场景：停止输入 0.7s 后才保存
        this.endpointField.setResponder(s -> pendingSave.schedule());

        // 4. 自定义模型输入框（先创建；初始宽度按「下拉框已收缩」布局，由 rebuildModelWidgets 校正）
        this.customModelField = new EditBox(this.font, modelFieldX(w),
                startY + ROW_MODEL + MODEL_FIELD_Y_NUDGE,
                modelFieldWidth(w), 20, Component.literal("自定义模型"));
        this.customModelField.setMaxLength(128);
        this.customModelField.setHint(Component.literal("自定义模型名称")
                .withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC));
        this.customModelField.setVisible(false);
        this.addRenderableWidget(this.customModelField);

        // 5. 模型下拉框
        rebuildModelWidgets();

        // 6. 语音朗读：勾选框 + 语音模型下拉框
        this.ttsCheckbox = new AutoSaveCheckbox(
                w / 2 - 101, startY + ROW_TTS, 20, 20,
                Component.literal("语音朗读"), loadedConfig.useTTS, this::onTtsToggled);
        this.addRenderableWidget(this.ttsCheckbox);

        // 语音下拉框：左边缘用固定偏移，不用 font.width() —— 字体宽度由字体文件决定，
        // 靠它算位置会随字体/语言环境漂移，永远对不齐，这里直接给死值便于微调。
        int ttsDropdownX = w / 2 + TTS_DROPDOWN_OFFSET;
        int ttsDropdownWidth = (w / 2 + 103) - ttsDropdownX; // 右边缘与「深度思考」行对齐
        this.ttsProviderDropdown = new DropdownWidget<>(
                ttsDropdownX, startY + ROW_TTS, ttsDropdownWidth, 20,
                Component.literal("语音模型"),
                Arrays.asList("LOCAL", "NATIVE"),
                loadedConfig.ttsProvider.equals("NATIVE") ? "NATIVE" : "LOCAL",
                this::ttsProviderLabel,
                value -> saveConfig());
        this.addRenderableWidget(this.ttsProviderDropdown);
        // 手机端把 Verity™ 灰显并拦截点击：点了不会改配置，而是变红 + 抖动 + 提示
        this.ttsProviderDropdown.setOptionDisabled(VerityConfigScreen::isTtsValueDisabled);
        this.ttsProviderDropdown.setOnDisabledClick(value -> showTtsPlatformReject());
        // 关闭语音时隐藏下拉框
        this.ttsProviderDropdown.visible = loadedConfig.useTTS;

        // 7. 深度思考复选框
        this.thinkCheckbox = new AutoSaveCheckbox(
                w / 2 - 101, startY + ROW_THINK, 20, 20,
                Component.literal("深度思考"), loadedConfig.think, pendingSave::runNow);
        this.addRenderableWidget(this.thinkCheckbox);

        // 8. 完成按钮（语音行两端都显示，位置固定）
        int saveButtonY = startY + ROW_DONE;
        this.addRenderableWidget(Button.builder(Component.literal("完成"), btn -> {
            pendingSave.runNow();
            if (ModsListScreen.firstTimeSetup) {
                // 注意：不要在这里把 firstTimeSetup 置回 false。
                // ModsListScreen.init() 要靠它来决定是否执行首次自动启用模组，
                // 提前清掉会导致自动启用永远不触发。
                Minecraft.getInstance().setScreen(new ModsListScreen());
            } else {
                showRestartConfirm();
            }
        }).pos(w / 2 - 50, saveButtonY).size(100, 20).build());

        // 9. 右上角按钮：赞助码展开/收起 + 高级设置
        this.sponsorButton = Button.builder(sponsorButtonLabel(), btn -> {
            if (!isSponsorSpaceSufficient()) {
                // 空间不足，无法内嵌显示，改为跳转全屏页，不改动配置
                Minecraft.getInstance().setScreen(new SponsorScreen());
                return;
            }
            sponsorExpanded = !sponsorExpanded;
            ModConfig.setShowSponsor(sponsorExpanded);
            // 文案只在状态真正变化时重建，不再每帧 setMessage
            updateSponsorButtonText();
        }).pos(this.width - 155, 5).size(70, 20).build();
        this.addRenderableWidget(this.sponsorButton);

        this.addRenderableWidget(Button.builder(Component.literal("高级设置"), btn -> {
            Minecraft.getInstance().setScreen(new AdvancedSettingsScreen());
        }).pos(this.width - 80, 5).size(70, 20).build());

        // 配置模组按钮（仅非首次启动显示）
        if (!ModsListScreen.firstTimeSetup) {
            this.addRenderableWidget(Button.builder(Component.literal("配置模组"), btn -> {
                ModsListScreen.returnToConfigScreen = true;
                Minecraft.getInstance().setScreen(new ModsListScreen());
            }).pos(this.width - 80, 30).size(70, 20).build());
        }

        updateSponsorButtonText();
    }

    private void showRestartConfirm() {
        // 与模组管理、教程提醒界面共用同一个确认框
        RestartPrompt.show();
    }

    /**
     * 按钮文案分三种情况：
     * <ul>
     *   <li>空间不足：显示「赞助作者」，点击跳转全屏页；</li>
     *   <li>空间足够且已展开：显示「收起赞助码」；</li>
     *   <li>空间足够但已收起：显示「展开赞助码」。</li>
     * </ul>
     */
    private Component sponsorButtonLabel() {
        if (!isSponsorSpaceSufficient()) return Component.literal("赞助作者");
        return Component.literal(sponsorExpanded ? "收起赞助码" : "展开赞助码");
    }

    private void updateSponsorButtonText() {
        if (sponsorButton != null) {
            sponsorButton.setMessage(sponsorButtonLabel());
        }
    }

    private void loadSponsorTexture() {
        // 先把上一次注册的纹理释放掉，原因见 releaseSponsorTexture
        releaseSponsorTexture();
        ResourceLocation res = new ResourceLocation(VerityConfig.MODID, "textures/sponsor_qr.png");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(res).orElseThrow();
            try (InputStream is = resource.open()) {
                NativeImage img = NativeImage.read(is);
                sponsorTexWidth = img.getWidth();
                sponsorTexHeight = img.getHeight();
                DynamicTexture dynamicTexture = new DynamicTexture(img);
                dynamicTexture.setBlurMipmap(false, false);
                sponsorTexture = Minecraft.getInstance().getTextureManager()
                        .register("verityconfig_sponsor_preview", dynamicTexture);
                sponsorTextureLoaded = true;
            }
        } catch (Exception e) {
            Log.error("无法加载赞助纹理: " + res);
            sponsorTextureLoaded = false;
        }
    }

    /**
     * 释放已注册的赞助图片纹理。
     * <p>{@code TextureManager.register(String, DynamicTexture)} 每次调用都会生成一个
     * 新的 {@code dynamic/xxx_N} 名字，<b>不会</b>覆盖上一次注册的同名纹理，
     * 所以旧纹理既不会被替换、也不会被自动关闭。不显式 release 的话，
     * 每次 {@code init()}（窗口缩放也会重跑）都会泄漏一份解码后的
     * {@link NativeImage} 堆外内存和对应的显存。
     */
    private void releaseSponsorTexture() {
        if (sponsorTexture != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(sponsorTexture);
            } catch (Exception ignored) {
                // 释放失败不影响界面关闭
            }
            sponsorTexture = null;
        }
        sponsorTextureLoaded = false;
    }

    private boolean isSponsorSpaceSufficient() {
        if (!sponsorTextureLoaded || sponsorTexture == null) return false;
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int guiWidth = (int) Math.ceil(sponsorTexWidth / guiScale);
        int contentRight = this.width / 2 + 110;
        int spaceRight = this.width - contentRight - 10;
        return guiWidth <= spaceRight;
    }

    private void rebuildModelWidgets() {
        if (modelDropdown != null) removeWidget(modelDropdown);
        int w = this.width;
        int startY = contentStartY();
        int modelY = startY + ROW_MODEL;

        List<Providers.Model> modelOptions = new ArrayList<>(Providers.modelsFor(selectedProvider));
        modelOptions.add(new Providers.Model(Providers.CUSTOM, ""));

        String currentModelName = loadedConfig.model.isEmpty()
                ? (modelOptions.isEmpty() ? "" : modelOptions.get(0).name())
                : loadedConfig.model;
        boolean isCustom = modelOptions.stream().noneMatch(m -> m.name().equals(currentModelName))
                || Providers.CUSTOM.equals(currentModelName);
        Providers.Model initialOption = modelOptions.stream()
                .filter(m -> m.name().equals(isCustom ? Providers.CUSTOM : currentModelName))
                .findFirst()
                .orElse(modelOptions.get(0));

        this.modelDropdown = new DropdownWidget<>(
                w / 2 - 101, modelY, modelDropdownWidth(initialOption.name()), 22,
                Component.literal("模型"),
                modelOptions,
                initialOption,
                option -> Component.literal(option.desc().isEmpty()
                        ? option.name()
                        : option.name() + " (" + option.desc() + ")"),
                option -> {
                    applyModelLayout(w, option.name());
                    saveConfig();
                }
        );
        // 展开列表始终用完整宽度，这样选「自定义」后弹出时长选项仍是原宽度
        this.modelDropdown.setExpandedWidth(MODEL_DROPDOWN_WIDTH);
        this.addRenderableWidget(this.modelDropdown);

        applyModelLayout(w, initialOption.name());
        if (customModelField != null) {
            if (Providers.CUSTOM.equals(initialOption.name()) && !loadedConfig.model.isEmpty()) {
                customModelField.setValue(loadedConfig.model);
            } else {
                customModelField.setValue("");
            }
        }
    }

    /** 判断某个模型名是否代表下拉框里的「自定义」项。 */
    private boolean isCustomModel(String modelName) {
        return Providers.CUSTOM.equals(modelName);
    }

    /** 模型下拉框当前应显示的宽度：选「自定义」时收缩，把空间让给输入框。 */
    private int modelDropdownWidth(String modelName) {
        return isCustomModel(modelName) ? MODEL_DROPDOWN_NARROW_WIDTH : MODEL_DROPDOWN_WIDTH;
    }

    /** 输入框左边缘：紧跟收缩后的下拉框（含微调）。 */
    private int modelFieldX(int w) {
        return w / 2 - 101 + MODEL_DROPDOWN_NARROW_WIDTH + 1 + MODEL_FIELD_X_NUDGE;
    }

    /** 输入框宽度：从收缩后的下拉框右侧延伸到该行右边缘（含微调）。 */
    private int modelFieldWidth(int w) {
        return (w / 2 - 100 + MODEL_DROPDOWN_WIDTH) - modelFieldX(w) + MODEL_FIELD_WIDTH_NUDGE;
    }

    /**
     * 按当前选中项摆放「模型」这一行：
     * 选「自定义」时下拉框收缩到 {@link #MODEL_DROPDOWN_NARROW_WIDTH}，
     * 输入框占据剩余空间；否则下拉框占满整行、输入框隐藏。
     * <p>
     * 下拉框只是按钮变窄，展开列表由 {@link DropdownWidget#setExpandedWidth} 保持完整宽度，
     * 因此选「自定义」后弹出的长选项仍然完整显示。
     */
    private void applyModelLayout(int w, String modelName) {
        boolean custom = isCustomModel(modelName);
        if (modelDropdown != null) {
            modelDropdown.setWidth(modelDropdownWidth(modelName));
        }
        if (customModelField != null) {
            customModelField.setX(modelFieldX(w));
            customModelField.setWidth(modelFieldWidth(w));
            customModelField.setVisible(custom);
        }
    }

    /**
     * 语音朗读勾选框切换时调用：关闭语音就隐藏右侧的语音模型下拉框，
     * 开启则重新显示。最后照常保存配置。
     */
    private void onTtsToggled() {
        if (ttsProviderDropdown != null) {
            boolean on = ttsCheckbox != null && ttsCheckbox.selected();
            ttsProviderDropdown.visible = on;
            if (!on) {
                // 收起已展开的列表，否则隐藏后列表可能仍残留
                ttsProviderDropdown.collapse();
            }
        }
        pendingSave.runNow();
    }

    /** 语音模型下拉框的显示文案。 */
    private Component ttsProviderLabel(String value) {
        return Component.literal("NATIVE".equals(value)
                ? "手机/电脑原生 (中文)"
                : "Verity™ (英文)");
    }

    /**
     * 当前平台是否允许选择 Verity™ 语音模型。
     * <p>Verity™ 是电脑端专用通道，手机端（安卓，os.name 报 Linux）选它不会生效，
     * 因此在手机端把它灰显并拦截点击，避免用户改完配置却发现没作用。
     */
    private static boolean canUseVerityTts() {
        return PlatformUtils.isWindows();
    }

    /**
     * 判定给定的语音模型值在当前平台是否被禁用。
     * <p>{@code NATIVE}（原生）两个平台都可用，只有 {@code LOCAL}（Verity™）受限。
     */
    private static boolean isTtsValueDisabled(String value) {
        return TTS_VALUE_LOCAL.equals(value) && !canUseVerityTts();
    }

    /**
     * 手机端点选 Verity™ 时的反馈：交给下拉框自己在按钮内弹出警告
     * （边框变红、文字换成提示并抖动），这里只负责提供文案。
     * <p>重复点击由下拉框的忽略窗口统一挡掉，不会让抖动与淡出重新开始。
     */
    private void showTtsPlatformReject() {
        if (ttsProviderDropdown != null) {
            ttsProviderDropdown.showWarning(ttsRejectHint());
        }
    }

    /** 平台不支持时显示在下拉框内的说明文字。 */
    private static Component ttsRejectHint() {
        return Component.literal("安卓设备暂不支持");
    }

    /**
     * 控件区首行的 Y 坐标。
     * <p>
     * 先按窗口高度在 {@link #MIN_START_Y}~{@link #MAX_START_Y} 之间线性取值
     * （窗口越矮越靠上，越高越靠下），再叠加一条上限：底部留白必须
     * ≥ 屏幕高度的 1/{@link #BOTTOM_MARGIN_DIVISOR}（向下取整），
     * 即 {@code startY ≤ 屏幕高 - 留白 - CONTENT_HEIGHT}。
     * <p>
     * 窗口太矮导致该上限连 {@link #MIN_START_Y} 都容纳不下时，约束无法成立，
     * 此时直接取 {@link #FALLBACK_START_Y}，不再尝试满足留白。
     */
    private int contentStartY() {
        // inverseLerp 不夹取，窗口高度超出区间时会得到 <0 或 >1，必须自己 clamp
        float t = Mth.clamp(
                Mth.inverseLerp(this.height, START_Y_RAMP_FROM, START_Y_RAMP_TO), 0.0F, 1.0F);
        int startY = Mth.lerpInt(t, MIN_START_Y, MAX_START_Y);

        int bottomMargin = this.height / BOTTOM_MARGIN_DIVISOR;
        int maxAllowed = this.height - bottomMargin - CONTENT_HEIGHT;
        if (maxAllowed < MIN_START_Y) {
            // 留白下限与首行下限无法同时满足，放弃留白，取固定兜底值
            return FALLBACK_START_Y;
        }
        return Math.min(startY, maxAllowed);
    }

    private void updateApiKeyActionButton() {
        if (apiKeyActionButton == null || apiKeyField == null) return;
        if (apiKeyField.getValue().isEmpty()) {
            apiKeyActionButton.setMessage(Component.literal("📋"));
            apiKeyActionButton.setTooltip(Tooltip.create(Component.literal("粘贴")));
        } else {
            apiKeyActionButton.setMessage(Component.literal("×"));
            apiKeyActionButton.setTooltip(Tooltip.create(Component.literal("清除")));
        }
    }

    private void saveConfig() {
        // 控件尚未创建时不保存：去抖任务只会在 init() 之后被推进，
        // 这里加一道保护，免得将来改动调用时机时踩到空指针
        if (apiKeyField == null || endpointField == null || modelDropdown == null) return;

        VerityConfigManager.ConfigData data = new VerityConfigManager.ConfigData();
        data.apiKey = apiKeyField.getValue().trim();
        data.endpoint = endpointField.getValue().trim();
        Providers.Model selectedModel = modelDropdown.getSelected();
        data.model = Providers.CUSTOM.equals(selectedModel.name())
                ? customModelField.getValue().trim()
                : selectedModel.name();
        data.think = thinkCheckbox.selected();
        data.useTTS = false;
        data.ttsProvider = "LOCAL";

        // 兜底：万一在语音控件创建前被调用，沿用已加载的配置，
        // 而不是把 useTTS/ttsProvider 写成硬编码的默认值
        if (ttsCheckbox != null) {
            // Verity 只接受 NATIVE/LOCAL/GROQ/KOKORO/CARTESIA，未知值必须落回 LOCAL，
            // 否则会写出无法解析的配置项
            data.useTTS = ttsCheckbox.selected();
            String selectedTts = ttsProviderDropdown != null
                    ? ttsProviderDropdown.getSelected()
                    : null;
            data.ttsProvider = "NATIVE".equals(selectedTts) ? "NATIVE" : "LOCAL";
        } else if (loadedConfig != null && loadedConfig.useTTS) {
            data.useTTS = true;
            data.ttsProvider = "NATIVE".equals(loadedConfig.ttsProvider) ? "NATIVE" : "LOCAL";
        }

        // 写盘在后台线程进行；这里同步更新内存缓存，界面立刻能读到新值
        VerityConfigManager.saveAll(data, selectedProvider);
        loadedConfig = data;
    }

    @Override
    public void tick() {
        pendingSave.tick();
        super.tick();
    }

    @Override
    public void removed() {
        // 关闭或切换到其他界面时，把还没到点的改动落盘，避免丢失刚输入的内容
        pendingSave.flush();
        releaseSponsorTexture();
        super.removed();
    }

    @Override
    public void resize(Minecraft mc, int width, int height) {
        // 缩放会重建控件并重新从缓存读值：必须先把未保存的改动落盘，
        // 否则 init() 会用旧值覆盖用户刚输入的内容
        pendingSave.flush();
        super.resize(mc, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        // 标题固定在控件区上方（首行 y=20，标题占 9px，中间留 5px）
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);

        // 绘制赞助图片
        if (sponsorTextureLoaded && sponsorTexture != null && sponsorExpanded) {
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            int guiWidth = (int) Math.ceil(sponsorTexWidth / guiScale);
            int guiHeight = (int) Math.ceil(sponsorTexHeight / guiScale);
            int x = this.width - guiWidth - 5;
            int y = this.height / 2 - guiHeight / 2;

            float scaleX = (float) guiWidth / sponsorTexWidth;
            float scaleY = (float) guiHeight / sponsorTexHeight;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scaleX, scaleY, 1.0f);
            graphics.blit(sponsorTexture, 0, 0, 0, 0, sponsorTexWidth, sponsorTexHeight, sponsorTexWidth, sponsorTexHeight);
            graphics.pose().popPose();
        }

        // 注意：这里不刷新赞助按钮文案。
        // 文案只取决于 sponsorExpanded 与窗口宽度，两者变化时都有明确的回调
        // （点击时、init() 时），每帧重建一个 Component 纯属浪费。
        // 语音行的警告也由下拉框自己在按钮内绘制，这里不再插手。
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}