package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.ConfigFileUtils;
import com.cb2495.verityconfig.util.ScrollableArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AdvancedSettingsScreen extends Screen {
    private final Path configFile = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config/verity-common.toml");

    private AutoSaveSlider dayCountSlider;
    private AutoSaveCheckbox canCrashBox, playVideoBox, requireVerityBox, trueDarknessBox,
            killWildlifeBox, killEntitiesBox, killVillagersBox, showKarmaBox;
    private Button saveButton;

    // 滚动相关
    private ScrollableArea scrollableArea;
    private boolean draggingScrollbar = false;
    private static final int ITEM_SPACING = 25;
    private static final int TOP_MARGIN = 30;
    private static final int BOTTOM_MARGIN = 30;
    private static final int BUTTON_AREA_HEIGHT = 30; // 底部按钮区域预留高度

    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
    private int[] initialY = new int[9]; // 9个可滚动控件

    public AdvancedSettingsScreen() {
        super(Component.literal("Verity 高级设置"));
    }

    @Override
    protected void init() {
        super.init();
        loadGeneralSettings();

        // 初始化滚动区域：视口从 TOP_MARGIN 到屏幕底部减去 BOTTOM_MARGIN 和按钮区域
        this.scrollableArea = new ScrollableArea(TOP_MARGIN, this.height - BOTTOM_MARGIN - BUTTON_AREA_HEIGHT);

        int baseY = TOP_MARGIN;
        int w = this.width;
        int index = 0;

        // dayCount 滑块
        this.dayCountSlider = new AutoSaveSlider(
                w / 2 - 100, baseY, 200, 20,
                Component.literal("dayCount: "), Component.literal(""),
                1, 100, loadedDayCount, 1, 0, true,
                this::saveGeneralSettingsSilent);
        this.dayCountSlider.setTooltip(Tooltip.create(Component.literal("调整 Verity 开始变异的时间")));
        addScrollWidget(dayCountSlider, index++);
        baseY += ITEM_SPACING;

        // canCrash
        this.canCrashBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("canCrash"), loadedCanCrash,
                this::saveGeneralSettingsSilent);
        this.canCrashBox.setTooltip(Tooltip.create(Component.literal("允许 Verity 将你踢出游戏")));
        addScrollWidget(canCrashBox, index++);
        baseY += ITEM_SPACING;

        // playVideo
        this.playVideoBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("playVideo"), loadedPlayVideo,
                this::saveGeneralSettingsSilent);
        this.playVideoBox.setTooltip(Tooltip.create(Component.literal("在启动时播放视频，若使用手机且配置较低建议关闭")));
        addScrollWidget(playVideoBox, index++);
        baseY += ITEM_SPACING;

        // requireVerity
        this.requireVerityBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("requireVerity"), loadedRequireVerity,
                this::saveGeneralSettingsSilent);
        this.requireVerityBox.setTooltip(Tooltip.create(Component.literal("每句话中必须包含“Verity”Verity 才会回应你")));
        addScrollWidget(requireVerityBox, index++);
        baseY += ITEM_SPACING;

        // trueDarkness
        this.trueDarknessBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("trueDarkness"), loadedTrueDarkness,
                this::saveGeneralSettingsSilent);
        this.trueDarknessBox.setTooltip(Tooltip.create(Component.literal("使视野更加黑暗")));
        addScrollWidget(trueDarknessBox, index++);
        baseY += ITEM_SPACING;

        // killWildlife
        this.killWildlifeBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("killWildlife"), loadedKillWildlife,
                this::saveGeneralSettingsSilent);
        this.killWildlifeBox.setTooltip(Tooltip.create(Component.literal("允许 Verity 杀死动物")));
        addScrollWidget(killWildlifeBox, index++);
        baseY += ITEM_SPACING;

        // killEntities
        this.killEntitiesBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("killEntities"), loadedKillEntities,
                this::saveGeneralSettingsSilent);
        this.killEntitiesBox.setTooltip(Tooltip.create(Component.literal("允许 Verity 杀死世界中的全部生物")));
        addScrollWidget(killEntitiesBox, index++);
        baseY += ITEM_SPACING;

        // killVillagers
        this.killVillagersBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("killVillagers"), loadedKillVillagers,
                this::saveGeneralSettingsSilent);
        this.killVillagersBox.setTooltip(Tooltip.create(Component.literal("允许 Verity 杀死村民")));
        addScrollWidget(killVillagersBox, index++);
        baseY += ITEM_SPACING;

        // showKarma
        this.showKarmaBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("showKarma"), loadedShowKarma,
                this::saveGeneralSettingsSilent);
        this.showKarmaBox.setTooltip(Tooltip.create(Component.literal("显示屏幕右侧的好感度")));
        addScrollWidget(showKarmaBox, index++);

        // 保存按钮固定底部
        this.saveButton = Button.builder(Component.literal("返回"), btn -> {
            saveGeneralSettingsSilent();
            Minecraft.getInstance().setScreen(new VerityConfigScreen());
        }).pos(w / 2 - 50, this.height - BOTTOM_MARGIN - 20).size(100, 20).build();
        this.addRenderableWidget(this.saveButton);

        // 初始化控件位置
        updateWidgetPositions();
    }

    private void addScrollWidget(AbstractWidget widget, int index) {
        this.addRenderableWidget(widget);
        this.scrollWidgets.add(widget);
        this.initialY[index] = widget.getY();
    }

    private void updateWidgetPositions() {
        int offset = scrollableArea.getScrollOffset();
        for (int i = 0; i < scrollWidgets.size(); i++) {
            scrollWidgets.get(i).setY(initialY[i] - offset);
        }
    }

    private int getTotalContentHeight() {
        return scrollWidgets.size() * ITEM_SPACING;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        // 顶部遮罩和标题
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(0, 0, this.width, 25, 0xD9000000);
        graphics.fill(0, 25, this.width, 26, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        graphics.pose().popPose();

        // 绘制滚动条
        scrollableArea.renderScrollbar(graphics, this.width, getTotalContentHeight());

        // 重新绘制保存按钮，确保在最上层
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 150);
        if (saveButton != null) {
            saveButton.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scrollableArea.mouseScrolled(mouseX, mouseY, delta, getTotalContentHeight())) {
            updateWidgetPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollableArea.isMouseOverScrollbar(mouseX, mouseY, this.width, getTotalContentHeight())) {
            draggingScrollbar = true;
            scrollableArea.updateScrollFromMouse(mouseY, this.width, getTotalContentHeight());
            updateWidgetPositions();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollableArea.updateScrollFromMouse(mouseY, this.width, getTotalContentHeight());
            updateWidgetPositions();
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

    // ========== 配置读取与保存（保持不变） ==========
    private int loadedDayCount = 5;
    private boolean loadedCanCrash = true;
    private boolean loadedPlayVideo = true;
    private boolean loadedRequireVerity = false;
    private boolean loadedTrueDarkness = true;
    private boolean loadedKillWildlife = true;
    private boolean loadedKillEntities = true;
    private boolean loadedKillVillagers = true;
    private boolean loadedShowKarma = false;

    private void loadGeneralSettings() {
        if (!java.nio.file.Files.exists(configFile)) return;
        try {
            List<String> lines = java.nio.file.Files.readAllLines(configFile);
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("dayCount")) {
                    loadedDayCount = ConfigFileUtils.parseInt(line, 5);
                } else if (line.startsWith("canCrash")) {
                    loadedCanCrash = ConfigFileUtils.parseBoolean(line);
                } else if (line.startsWith("playVideo")) {
                    loadedPlayVideo = ConfigFileUtils.parseBoolean(line);
                } else if (line.startsWith("requireVerity")) {
                    loadedRequireVerity = ConfigFileUtils.parseBoolean(line);
                } else if (line.startsWith("trueDarkness")) {
                    loadedTrueDarkness = ConfigFileUtils.parseBoolean(line);
                } else if (line.startsWith("killWildlife")) {
                    loadedKillWildlife = ConfigFileUtils.parseBoolean(line);
                } else if (line.startsWith("killEntities")) {
                    loadedKillEntities = ConfigFileUtils.parseBoolean(line);
                } else if (line.startsWith("killVillagers")) {
                    loadedKillVillagers = ConfigFileUtils.parseBoolean(line);
                } else if (line.startsWith("showKarma")) {
                    loadedShowKarma = ConfigFileUtils.parseBoolean(line);
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveGeneralSettingsSilent() {
        int dayCount = dayCountSlider.getValueInt();
        boolean canCrash = canCrashBox.selected();
        boolean playVideo = playVideoBox.selected();
        boolean requireVerity = requireVerityBox.selected();
        boolean trueDarkness = trueDarknessBox.selected();
        boolean killWildlife = killWildlifeBox.selected();
        boolean killEntities = killEntitiesBox.selected();
        boolean killVillagers = killVillagersBox.selected();
        boolean showKarma = showKarmaBox.selected();

        List<String> lines = ConfigFileUtils.readLines(configFile);
        ConfigFileUtils.replaceOrAddRaw(lines, "dayCount", String.valueOf(dayCount));
        ConfigFileUtils.replaceOrAddRaw(lines, "canCrash", String.valueOf(canCrash));
        ConfigFileUtils.replaceOrAddRaw(lines, "playVideo", String.valueOf(playVideo));
        ConfigFileUtils.replaceOrAddRaw(lines, "requireVerity", String.valueOf(requireVerity));
        ConfigFileUtils.replaceOrAddRaw(lines, "trueDarkness", String.valueOf(trueDarkness));
        ConfigFileUtils.replaceOrAddRaw(lines, "killWildlife", String.valueOf(killWildlife));
        ConfigFileUtils.replaceOrAddRaw(lines, "killEntities", String.valueOf(killEntities));
        ConfigFileUtils.replaceOrAddRaw(lines, "killVillagers", String.valueOf(killVillagers));
        ConfigFileUtils.replaceOrAddRaw(lines, "showKarma", String.valueOf(showKarma));
        ConfigFileUtils.writeLines(configFile, lines);
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