package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.PlatformUtils;
import com.cb2495.verityconfig.util.SponsorUtils;
import com.cb2495.verityconfig.util.VerityConfigManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.*;

@SuppressWarnings("removal")
public class VerityConfigScreen extends Screen {

    // ---------- 提供商与模型数据 ----------
    private static class ModelOption {
        final String name;
        final String desc;
        ModelOption(String name, String desc) { this.name = name; this.desc = desc; }
    }

    private static final Map<String, List<ModelOption>> PROVIDER_MODELS = new LinkedHashMap<>();
    static {
        PROVIDER_MODELS.put("DeepSeek", Arrays.asList(
                new ModelOption("deepseek-flash", "低价、极速、深度思考"),
                new ModelOption("deepseek-v4-pro", "专业、深度思考")
        ));
        PROVIDER_MODELS.put("智谱 (GLM)", Arrays.asList(
                new ModelOption("glm-4-flash", "免费、文本生成"),
                new ModelOption("glm-4.7-flash", "免费、深度思考"),
                new ModelOption("glm-4-flash-250414", "免费、文本生成"),
                new ModelOption("glm-5.3-flash", "低价、深度思考"),
                new ModelOption("glm-4.7-flash-x", "付费、深度思考"),
                new ModelOption("glm-4.7", "付费、深度思考"),
                new ModelOption("glm-4.5-air", "付费、深度思考"),
                new ModelOption("glm-4.6", "付费、深度思考"),
                new ModelOption("glm-5.2", "付费、深度思考"),
                new ModelOption("glm-5.3", "付费、旗舰")
        ));
        PROVIDER_MODELS.put("阿里云百炼 (通义千问)", Arrays.asList(
                new ModelOption("qwen3.6-flash", "低成本、轻量极速"),
                new ModelOption("qwen3-turbo", "低成本、轻量极速"),
                new ModelOption("qwen3.7-plus", "付费、均衡主力"),
                new ModelOption("qwen3.8-max", "付费、旗舰"),
                new ModelOption("qwen3.7-max", "付费、旗舰")
        ));
        PROVIDER_MODELS.put("Kimi", Arrays.asList(
                new ModelOption("kimi-k2.6", "付费、思考/非思考"),
                new ModelOption("kimi-k2.5", "付费、思考/非思考"),
                new ModelOption("kimi-k3", "付费、旗舰"),
                new ModelOption("moonshot-v1-128k", "付费、纯文本"),
                new ModelOption("moonshot-v1-32k", "付费、纯文本"),
                new ModelOption("moonshot-v1-8k", "付费、纯文本")
        ));
        PROVIDER_MODELS.put("自定义", Collections.emptyList());
    }

    private static final Map<String, String> KEY_URLS = new HashMap<>();
    static {
        KEY_URLS.put("DeepSeek", "https://platform.deepseek.com/api_keys");
        KEY_URLS.put("智谱 (GLM)", "https://open.bigmodel.cn/console/overview");
        KEY_URLS.put("阿里云百炼 (通义千问)", "https://bailian.console.aliyun.com/?apiKey=1");
        KEY_URLS.put("Kimi", "https://platform.moonshot.cn/console/api-keys");
        KEY_URLS.put("自定义", "");
    }

    // ---------- 控件 ----------
    private EditBox apiKeyField, endpointField, customModelField;
    private Button apiKeyActionButton;
    private Checkbox thinkCheckbox;
    private CycleButton<String> ttsProviderButton;
    private DropdownWidget<String> providerDropdown;
    private DropdownWidget<ModelOption> modelDropdown;

    private String selectedProvider = new ArrayList<>(PROVIDER_MODELS.keySet()).get(0);
    private VerityConfigManager.ConfigData loadedConfig;

    // ---------- 赞助相关 ----------
    private ResourceLocation sponsorTexture;
    private int sponsorTexWidth, sponsorTexHeight;
    private boolean sponsorTextureLoaded = false;
    private boolean sponsorExpanded = false;
    private Button sponsorButton;

    // 图片显示区域与悬停状态
    private int sponsorImageX, sponsorImageY, sponsorImageWidth, sponsorImageHeight;
    private boolean isHoveringSponsorImage = false;

    public VerityConfigScreen() {
        super(Component.literal("Verity AI 配置"));
    }

    @Override
    protected void init() {
        super.init();
        int w = this.width, h = this.height;
        int startY = h / 2 - 70;

        loadSponsorTexture();
        sponsorExpanded = isSponsorSpaceSufficient();

        loadedConfig = VerityConfigManager.loadVerityConfig();
        selectedProvider = VerityConfigManager.inferProviderFromEndpoint(loadedConfig.endpoint, selectedProvider);

        // 1. 提供商下拉框 + 获取Key按钮
        this.providerDropdown = new DropdownWidget<>(
                w / 2 - 101, startY, 156, 20,
                Component.literal("提供商"),
                new ArrayList<>(PROVIDER_MODELS.keySet()),
                selectedProvider,
                value -> Component.literal(value),
                value -> {
                    selectedProvider = value;
                    String endpoint = VerityConfigManager.getEndpointForProvider(value);
                    if (!endpoint.isEmpty()) endpointField.setValue(endpoint);
                    List<ModelOption> models = PROVIDER_MODELS.getOrDefault(value, Collections.emptyList());
                    if (!models.isEmpty()) loadedConfig.model = models.get(0).name;
                    else loadedConfig.model = "";
                    rebuildModelWidgets();
                    saveConfig();
                }
        );
        this.addRenderableWidget(this.providerDropdown);

        this.addRenderableWidget(Button.builder(Component.literal("获取 Key"), btn -> {
            String url = KEY_URLS.get(selectedProvider);
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
        }).pos(w / 2 + 55, startY).size(48, 20).build());

        // 2. API Key 输入框
        this.apiKeyField = new EditBox(this.font, w / 2 - 100, startY + 30, 180, 20, Component.literal("API Key"));
        this.apiKeyField.setMaxLength(512);
        this.apiKeyField.setValue(loadedConfig.apiKey);
        this.apiKeyField.setHint(Component.literal("请输入 API Key")
                .withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC));
        this.addRenderableWidget(this.apiKeyField);
        this.apiKeyField.setResponder(s -> updateApiKeyActionButton());

        this.apiKeyActionButton = Button.builder(Component.literal(""), btn -> {
            if (apiKeyField.getValue().isEmpty()) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (!clipboard.isEmpty()) apiKeyField.setValue(clipboard);
            } else {
                apiKeyField.setValue("");
            }
            updateApiKeyActionButton();
        }).pos(apiKeyField.getX() + apiKeyField.getWidth() + 1, apiKeyField.getY() - 1).size(22, 22).build();
        this.addRenderableWidget(this.apiKeyActionButton);
        updateApiKeyActionButton();

        // 3. Base URL
        this.endpointField = new EditBox(this.font, w / 2 - 100, startY + 60, 201, 20, Component.literal("Base URL"));
        this.endpointField.setMaxLength(128);
        this.endpointField.setValue(loadedConfig.endpoint.isEmpty()
                ? VerityConfigManager.getEndpointForProvider(selectedProvider)
                : loadedConfig.endpoint);
        this.endpointField.setHint(Component.literal("请输入 Base URL")
                .withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC));
        this.addRenderableWidget(this.endpointField);
        this.endpointField.setResponder(s -> saveConfig());

        // 4. 自定义模型输入框（先创建）
        this.customModelField = new EditBox(this.font, w / 2 + 1, startY + 120, 100, 20, Component.literal("自定义模型"));
        this.customModelField.setMaxLength(128);
        this.customModelField.setHint(Component.literal("自定义模型名称")
                .withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC));
        this.customModelField.setVisible(false);
        this.addRenderableWidget(this.customModelField);

        // 5. 模型下拉框
        rebuildModelWidgets();

        // 6. 深度思考复选框
        this.thinkCheckbox = new AutoSaveCheckbox(
                w / 2 - 100, startY + 120, 20, 20,
                Component.literal("深度思考"), loadedConfig.think, this::saveConfig);
        this.addRenderableWidget(this.thinkCheckbox);

        // 7. 语音模型（仅 Windows）
        if (PlatformUtils.isWindows()) {
            this.ttsProviderButton = CycleButton.<String>builder(value -> Component.literal(
                            switch (value) {
                                case "NATIVE" -> "系统原生 (中文)";
                                case "LOCAL" -> "原版声音 (英文)";
                                default -> "关闭";
                            }
                    )).withValues(Arrays.asList("NATIVE", "LOCAL", "NONE"))
                    .withInitialValue(loadedConfig.useTTS
                            ? (loadedConfig.ttsProvider.equals("NATIVE") ? "NATIVE" : "LOCAL")
                            : "NONE")
                    .create(w / 2 - 100, startY + 150, 200, 20, Component.literal("语音模型"),
                            (button, value) -> saveConfig());
            this.addRenderableWidget(this.ttsProviderButton);
        }

        // 8. 完成按钮（关键修改）
        int saveButtonY = PlatformUtils.isWindows() ? startY + 180 : startY + 150;
        this.addRenderableWidget(Button.builder(Component.literal("完成"), btn -> {
            saveConfig();
            if (ModsListScreen.firstTimeSetup) {
                ModsListScreen.firstTimeSetup = false;
                Minecraft.getInstance().setScreen(new ModsListScreen());
            } else {
                showRestartConfirm();
            }
        }).pos(w / 2 - 50, saveButtonY).size(100, 20).build());

        // 9. 右上角按钮：赞助作者 + 高级设置
        this.sponsorButton = Button.builder(Component.literal(sponsorExpanded ? "收起" : "赞助作者"), btn -> {
            if (sponsorExpanded) {
                sponsorExpanded = false;
                btn.setMessage(Component.literal("赞助作者"));
            } else {
                if (isSponsorSpaceSufficient()) {
                    sponsorExpanded = true;
                    btn.setMessage(Component.literal("收起"));
                } else {
                    Minecraft.getInstance().setScreen(new SponsorScreen());
                }
            }
        }).pos(this.width - 155, 5).size(70, 20).build();
        this.addRenderableWidget(this.sponsorButton);

        this.addRenderableWidget(Button.builder(Component.literal("高级设置"), btn -> {
            Minecraft.getInstance().setScreen(new AdvancedSettingsScreen());
        }).pos(this.width - 80, 5).size(70, 20).build());

        updateSponsorButtonText();
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

    private void updateSponsorButtonText() {
        if (sponsorButton != null) {
            sponsorButton.setMessage(Component.literal(sponsorExpanded ? "收起" : "赞助作者"));
        }
    }

    private void loadSponsorTexture() {
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
            System.err.println("[VerityConfig] 无法加载赞助纹理: " + res);
            sponsorTextureLoaded = false;
        }
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
        int w = this.width, h = this.height;
        int startY = h / 2 - 70;
        int modelY = startY + 90;

        List<ModelOption> modelOptions = new ArrayList<>(PROVIDER_MODELS.getOrDefault(selectedProvider, Collections.emptyList()));
        modelOptions.add(new ModelOption("自定义", ""));

        String currentModelName = loadedConfig.model.isEmpty()
                ? (modelOptions.isEmpty() ? "" : modelOptions.get(0).name)
                : loadedConfig.model;
        boolean isCustom = !modelOptions.stream().anyMatch(m -> m.name.equals(currentModelName)) || "自定义".equals(currentModelName);
        ModelOption initialOption = modelOptions.stream()
                .filter(m -> m.name.equals(isCustom ? "自定义" : currentModelName))
                .findFirst()
                .orElse(modelOptions.get(0));

        this.modelDropdown = new DropdownWidget<>(
                w / 2 - 100, modelY, 202, 20,
                Component.literal("模型"),
                modelOptions,
                initialOption,
                option -> Component.literal(option.desc.isEmpty() ? option.name : option.name + " (" + option.desc + ")"),
                option -> {
                    if ("自定义".equals(option.name)) {
                        if (customModelField != null) customModelField.setVisible(true);
                    } else {
                        if (customModelField != null) customModelField.setVisible(false);
                    }
                    saveConfig();
                }
        );
        this.addRenderableWidget(this.modelDropdown);

        if (customModelField != null) {
            customModelField.setVisible("自定义".equals(initialOption.name));
            if ("自定义".equals(initialOption.name) && !loadedConfig.model.isEmpty()) {
                customModelField.setValue(loadedConfig.model);
            } else {
                customModelField.setValue("");
            }
        }
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
        VerityConfigManager.ConfigData data = new VerityConfigManager.ConfigData();
        data.apiKey = apiKeyField.getValue().trim();
        data.endpoint = endpointField.getValue().trim();
        ModelOption selectedModel = modelDropdown.getSelected();
        data.model = "自定义".equals(selectedModel.name)
                ? customModelField.getValue().trim()
                : selectedModel.name;
        data.think = thinkCheckbox.selected();

        if (!PlatformUtils.isWindows()) {
            data.useTTS = false;
            data.ttsProvider = "LOCAL";
        } else {
            String selectedTts = ttsProviderButton.getValue();
            if ("NONE".equals(selectedTts)) {
                data.useTTS = false;
                data.ttsProvider = "LOCAL";
            } else {
                data.useTTS = true;
                data.ttsProvider = selectedTts;
            }
        }

        VerityConfigManager.saveVerityConfig(data);
        VerityConfigManager.saveSimpleTranslateConfig(selectedProvider, data.apiKey, data.endpoint, data.model);
        loadedConfig = data;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // 绘制赞助图片
        if (sponsorTextureLoaded && sponsorTexture != null && sponsorExpanded) {
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            int guiWidth = (int) Math.ceil(sponsorTexWidth / guiScale);
            int guiHeight = (int) Math.ceil(sponsorTexHeight / guiScale);
            int x = this.width - guiWidth - 5;
            int y = this.height / 2 - guiHeight / 2;

            sponsorImageX = x;
            sponsorImageY = y;
            sponsorImageWidth = guiWidth;
            sponsorImageHeight = guiHeight;

            float scaleX = (float) guiWidth / sponsorTexWidth;
            float scaleY = (float) guiHeight / sponsorTexHeight;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scaleX, scaleY, 1.0f);
            graphics.blit(sponsorTexture, 0, 0, 0, 0, sponsorTexWidth, sponsorTexHeight, sponsorTexWidth, sponsorTexHeight);
            graphics.pose().popPose();

            isHoveringSponsorImage = mouseX >= x && mouseX <= x + guiWidth && mouseY >= y && mouseY <= y + guiHeight;
            if (isHoveringSponsorImage) {
                graphics.renderTooltip(this.font, Component.literal("点击可保存至本地"), mouseX, mouseY);
            }
        } else {
            isHoveringSponsorImage = false;
        }

        updateSponsorButtonText();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHoveringSponsorImage) {
            SponsorUtils.openSaveDialog();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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