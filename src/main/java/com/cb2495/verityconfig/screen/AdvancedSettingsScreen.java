package com.cb2495.verityconfig.screen;

import com.cb2495.verityconfig.config.GeneralSettings;
import com.cb2495.verityconfig.util.DebouncedTask;
import com.cb2495.verityconfig.widget.AutoSaveCheckbox;
import com.cb2495.verityconfig.widget.AutoSaveSlider;
import com.cb2495.verityconfig.widget.ScrollableArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class AdvancedSettingsScreen extends Screen {

    private AutoSaveSlider dayCountSlider;
    private AutoSaveCheckbox canCrashBox, playVideoBox, requireVerityBox, trueDarknessBox,
            killWildlifeBox, killEntitiesBox, killVillagersBox, showKarmaBox;
    private Button saveButton;

    /**
     * 去抖保存：滑块停止拖动、复选框停下 0.7 秒后才写盘。
     * <p>原本滑块每拖过一帧都会重写整个配置文件，这里合并成一次；
     * 写入由 {@link GeneralSettings} 交给后台线程，渲染线程不再碰磁盘。
     */
    private final DebouncedTask pendingSave = new DebouncedTask(this::saveGeneralSettings);

    /** 当前设置值，来自内存缓存（启动时已后台预读）。 */
    private GeneralSettings settings = new GeneralSettings();

    // 滚动相关
    private ScrollableArea scrollableArea;
    private static final int ITEM_SPACING = 25;
    private static final int TITLE_BAR_HEIGHT = 26;   // 顶部标题栏（含分隔线）高度
    private static final int MASK_BOTTOM = TITLE_BAR_HEIGHT; // 顶部遮罩的下边界
    private static final int TOP_MARGIN = TITLE_BAR_HEIGHT + 4;

    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
    private final List<Integer> initialY = new ArrayList<>();

    public AdvancedSettingsScreen() {
        super(Component.literal("Verity 高级设置"));
    }

    @Override
    protected void init() {
        super.init();
        // 关键：窗口缩放会重新调用 init()，必须清空上一次的控件记录，
        // 否则 scrollWidgets 会不断累积、坐标也会算错
        this.scrollWidgets.clear();
        this.initialY.clear();
        this.savedTooltips.clear();
        this.tooltipsSuppressed = false;
        // 拖动状态由 ScrollableArea 持有，下面会重建一个新的，等同于取消拖动

        // 从内存缓存取；预读未完成时才会同步读一次盘
        this.settings = GeneralSettings.get();

        // 滚动视口 = 标题栏下方 到 屏幕底部。
        // 返回按钮已移到右上角遮罩上，底部不再需要预留按钮区域。
        this.scrollableArea = new ScrollableArea(TITLE_BAR_HEIGHT, this.height);

        int baseY = TOP_MARGIN;
        int w = this.width;
        int index = 0;

        // dayCount 滑块
        this.dayCountSlider = new AutoSaveSlider(
                w / 2 - 100, baseY, 200, 20,
                Component.literal("dayCount: "), Component.literal(""),
                1, 100, settings.dayCount, 1, 0, true,
                pendingSave::schedule);
        addScrollWidget(dayCountSlider, index++, Tooltip.create(Component.literal("调整 Verity 开始变异的时间")));
        baseY += ITEM_SPACING;

        // canCrash
        this.canCrashBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("canCrash"), settings.canCrash,
                pendingSave::schedule);
        addScrollWidget(canCrashBox, index++, Tooltip.create(Component.literal("允许 Verity 将你踢出游戏")));
        baseY += ITEM_SPACING;

        // playVideo
        this.playVideoBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("playVideo"), settings.playVideo,
                pendingSave::schedule);
        addScrollWidget(playVideoBox, index++, Tooltip.create(Component.literal("在启动时播放视频，若使用手机且配置较低建议关闭")));
        baseY += ITEM_SPACING;

        // requireVerity
        this.requireVerityBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("requireVerity"), settings.requireVerity,
                pendingSave::schedule);
        addScrollWidget(requireVerityBox, index++, Tooltip.create(Component.literal("每句话中必须包含“Verity”Verity 才会回应你")));
        baseY += ITEM_SPACING;

        // trueDarkness
        this.trueDarknessBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("trueDarkness"), settings.trueDarkness,
                pendingSave::schedule);
        addScrollWidget(trueDarknessBox, index++, Tooltip.create(Component.literal("使视野更加黑暗")));
        baseY += ITEM_SPACING;

        // killWildlife
        this.killWildlifeBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("killWildlife"), settings.killWildlife,
                pendingSave::schedule);
        addScrollWidget(killWildlifeBox, index++, Tooltip.create(Component.literal("允许 Verity 杀死动物")));
        baseY += ITEM_SPACING;

        // killEntities
        this.killEntitiesBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("killEntities"), settings.killEntities,
                pendingSave::schedule);
        addScrollWidget(killEntitiesBox, index++, Tooltip.create(Component.literal("允许 Verity 杀死世界中的全部生物")));
        baseY += ITEM_SPACING;

        // killVillagers
        this.killVillagersBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("killVillagers"), settings.killVillagers,
                pendingSave::schedule);
        addScrollWidget(killVillagersBox, index++, Tooltip.create(Component.literal("允许 Verity 杀死村民")));
        baseY += ITEM_SPACING;

        // showKarma
        this.showKarmaBox = new AutoSaveCheckbox(w / 2 - 100, baseY, 20, 20, Component.literal("showKarma"), settings.showKarma,
                pendingSave::schedule);
        addScrollWidget(showKarmaBox, index++, Tooltip.create(Component.literal("显示屏幕右侧的好感度")));

        // 保存按钮固定在右上角遮罩上（与模组管理界面保持一致），略微上移使其在遮罩内居中
        this.saveButton = Button.builder(Component.literal("返回"), btn -> {
            // 返回是明确的一次结束操作，立即保存并取消已排队的去抖
            pendingSave.runNow();
            Minecraft.getInstance().setScreen(new VerityConfigScreen());
        }).pos(this.width - 80, 3).size(70, 20).build();
        this.addRenderableWidget(this.saveButton);

        // 初始化控件位置
        updateWidgetPositions();
    }

    private void addScrollWidget(AbstractWidget widget, int index, Tooltip tooltip) {
        this.addRenderableWidget(widget);
        this.scrollWidgets.add(widget);
        // 用 List 而不是固定长度数组：Screen.resize() 会重新调用 init()，
        // 固定长度数组会在第二次初始化时越界
        this.initialY.add(widget.getY());
        // 记录原始 tooltip，鼠标压到遮罩上时需要临时摘掉
        widget.setTooltip(tooltip);
        savedTooltips.put(widget, tooltip);
    }

    private void updateWidgetPositions() {
        int offset = scrollableArea.getScrollOffset();
        int count = Math.min(scrollWidgets.size(), initialY.size());
        for (int i = 0; i < count; i++) {
            AbstractWidget widget = scrollWidgets.get(i);
            int y = initialY.get(i) - offset;
            widget.setY(y);
            // 只在"完全移出屏幕"或"完全被遮罩盖住"时才隐藏，
            // 否则会出现控件还在视野里却整条消失的突兀感
            widget.visible = !shouldHideWidget(y, widget.getHeight());
        }
    }

    /**
     * 控件是否应当被隐藏（用于 visible 与 tooltip 抑制）。
     * <p>只在两种情况下隐藏，避免"还没被挡住就整条消失"：
     * <ul>
     *   <li>已经完全移出屏幕（在屏幕上方或下方之外）</li>
     *   <li>完全位于遮罩之下（整条都被遮罩盖住）</li>
     * </ul>
     * 只要还有一部分露在遮罩下方，就保持可见（哪怕被遮罩裁掉一部分）。
     */
    private boolean shouldHideWidget(int widgetY, int widgetHeight) {
        int top = widgetY;
        int bottom = widgetY + widgetHeight;
        if (bottom <= 0 || top >= this.height) return true;
        return bottom <= MASK_BOTTOM;
    }

    /**
     * 鼠标是否压在顶部遮罩上。
     */
    private boolean isMouseOverMask(double mouseY) {
        return mouseY < MASK_BOTTOM;
    }

    private int getTotalContentHeight() {
        if (scrollWidgets.isEmpty()) return 0;
        // 最后一项下方补一个行距，滚动到底时最后一项能完整露出
        return scrollWidgets.size() * ITEM_SPACING;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 鼠标在遮罩上时先摘掉 tooltip，避免遮罩下方的控件弹出提示
        applyTooltipSuppression(mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        // 滚动条先画，这样顶部遮罩能把它盖住（否则滚动条会画到标题栏上）
        scrollableArea.renderScrollbar(graphics, this.width, getTotalContentHeight());

        // 顶部遮罩和标题（遮罩下边界 = MASK_BOTTOM，最后一行是分隔线）
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(0, 0, this.width, MASK_BOTTOM - 1, 0xD9000000);
        graphics.fill(0, MASK_BOTTOM - 1, this.width, MASK_BOTTOM, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        graphics.pose().popPose();

        // 返回按钮画在遮罩之上，否则会被遮罩盖住
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        if (saveButton != null) {
            saveButton.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.pose().popPose();
    }

    /** 记录控件原始 tooltip，鼠标压到遮罩上时临时摘掉，移开后恢复。 */
    private final Map<AbstractWidget, Tooltip> savedTooltips = new IdentityHashMap<>();

    /**
     * 鼠标压在遮罩上时，临时摘掉所有滚动控件的 tooltip。
     * <p>1.20.1 中 tooltip 是控件在 render() 里发现悬停后挂到 Screen 队列上的，
     * 没有可直接覆写的 renderTooltip(graphics,x,y)，因此用摘除 tooltip 的方式抑制。
     */
    private void applyTooltipSuppression(double mouseY) {
        if (savedTooltips.isEmpty()) return;
        boolean suppress = isMouseOverMask(mouseY);
        if (suppress == tooltipsSuppressed) return;
        tooltipsSuppressed = suppress;
        for (Map.Entry<AbstractWidget, Tooltip> entry : savedTooltips.entrySet()) {
            entry.getKey().setTooltip(suppress ? null : entry.getValue());
        }
    }

    private boolean tooltipsSuppressed = false;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 是否在视口内（也即在遮罩下方）由 ScrollableArea 统一判断
        if (scrollableArea.mouseScrolled(mouseY, delta, getTotalContentHeight())) {
            updateWidgetPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滚动条的按下/拖动/松开状态由 ScrollableArea 自己维护。
        // 这个界面的控件会跟着滚动位移，所以每次都要重新摆一遍位置
        if (button == 0
                && scrollableArea.beginScrollbarDrag(mouseX, mouseY, this.width, getTotalContentHeight())) {
            updateWidgetPositions();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollableArea.dragScrollbar(mouseY, this.width, getTotalContentHeight())) {
            updateWidgetPositions();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollableArea.endScrollbarDrag(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ========== 配置保存 ==========

    /**
     * 把当前控件取值写回设置。
     * <p>控件尚未全部创建时直接跳过：去抖任务只会在 init() 之后被推进。
     */
    private void saveGeneralSettings() {
        if (dayCountSlider == null || canCrashBox == null || playVideoBox == null
                || requireVerityBox == null || trueDarknessBox == null
                || killWildlifeBox == null || killEntitiesBox == null
                || killVillagersBox == null || showKarmaBox == null) {
            return;
        }

        GeneralSettings data = new GeneralSettings();
        data.dayCount = dayCountSlider.getValueInt();
        data.canCrash = canCrashBox.selected();
        data.playVideo = playVideoBox.selected();
        data.requireVerity = requireVerityBox.selected();
        data.trueDarkness = trueDarknessBox.selected();
        data.killWildlife = killWildlifeBox.selected();
        data.killEntities = killEntitiesBox.selected();
        data.killVillagers = killVillagersBox.selected();
        data.showKarma = showKarmaBox.selected();

        // 内存缓存同步更新，磁盘写入在后台线程
        GeneralSettings.save(data);
        this.settings = data;
    }

    @Override
    public void tick() {
        pendingSave.tick();
        super.tick();
    }

    @Override
    public void removed() {
        // 关闭或切换到其他界面时，把还没到点的改动落盘
        pendingSave.flush();
        super.removed();
    }

    @Override
    public void resize(Minecraft mc, int width, int height) {
        // 缩放会重建控件并重新读设置：先把未保存的改动落盘，
        // 否则刚拖动/勾选的结果会被旧值覆盖
        pendingSave.flush();
        super.resize(mc, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        pendingSave.flush();
        Minecraft.getInstance().setScreen(null);
    }
}
