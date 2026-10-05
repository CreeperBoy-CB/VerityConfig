package com.cb2495.verityconfig.screen;

import com.cb2495.verityconfig.save.LegacySaveScanner.LegacySave;
import com.cb2495.verityconfig.save.LegacySaveScanner;
import com.cb2495.verityconfig.save.SaveIconCache;
import com.cb2495.verityconfig.widget.ScrollableArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 发现旧整合包存档时的导入界面。
 * <p>上方提示发现旧版整合包存档，下方列出所有候选存档供选择，
 * 选中的一条会在行内展开「导入」按钮。
 * <p>导入采用复制，源存档保留；导入结果在界面内就地提示，
 * 不需要玩家去翻日志。
 */
public class LegacySaveImportScreen extends Screen {

    /** 从旧实例里扫出来的存档，按最后游玩时间倒序。 */
    private final List<LegacySave> saves;

    /** 本页结束后的后续动作，通常是进入配置界面。 */
    private final Runnable onFinished;

    /** 返回上一页时要展示的界面。 */
    private final Screen returnScreen;

    // ===== 布局常量 =====
    private static final int TITLE_Y = 16;
    private static final int HINT_Y = 34;
    private static final int LIST_TOP = 74;
    private static final int BOTTOM_BAR_HEIGHT = 46;

    /** 一条存档占的高度：两行文字加行距。 */
    private static final int ROW_HEIGHT = 40;

    /** 行内「导入」按钮的宽度，绘制与文字避让共用。 */
    private static final int IMPORT_BUTTON_WIDTH = 60;

    private ScrollableArea scrollableArea;

    /** 当前展开「导入」按钮的条目下标，-1 表示都没展开。 */
    private int selectedIndex = -1;

    /** 导入结果的提示文字与颜色，null 表示无提示。 */
    private String resultMessage = null;
    private int resultColor = 0xFFFFFF;

    /** 每行的屏幕区域，render 时重建，点击时读取。 */
    private final List<int[]> rowRects = new ArrayList<>();

    /** 「导入」按钮的屏幕区域与所属下标，render 时重建。 */
    private int[] importButtonRect = null;
    private int importButtonIndex = -1;

    public LegacySaveImportScreen(List<LegacySave> saves, Screen returnScreen, Runnable onFinished) {
        super(Component.literal("导入旧存档"));
        this.saves = new ArrayList<>(saves);
        this.returnScreen = returnScreen;
        this.onFinished = onFinished;
    }

    @Override
    protected void init() {
        super.init();

        int listBottom = this.height - BOTTOM_BAR_HEIGHT;
        this.scrollableArea = new ScrollableArea(LIST_TOP, listBottom);
        this.scrollableArea.setItemHeight(ROW_HEIGHT);
        this.scrollableArea.clampScroll(saves.size() * ROW_HEIGHT);

        int buttonY = this.height - 30;

        // 没有旧存档时不显示导入按钮，只留跳过
        boolean hasSaves = !saves.isEmpty();
        this.addRenderableWidget(Button.builder(Component.literal("跳过，直接开始配置"), btn -> {
            finish();
        }).pos(this.width / 2 - (hasSaves ? 105 : 75), buttonY)
                .size(hasSaves ? 100 : 150, 20).build());

        if (hasSaves) {
            this.addRenderableWidget(Button.builder(Component.literal("全部导入"), btn -> {
                importAll();
            }).pos(this.width / 2 + 5, buttonY).size(100, 20).build());
        }
    }

    /** 本页结束，交给调用方继续（进入配置界面）。 */
    private void finish() {
        if (onFinished != null) {
            onFinished.run();
        } else {
            ModsListScreen.firstTimeSetup = true;
            Minecraft.getInstance().setScreen(new VerityConfigScreen());
        }
    }

    /** 导入当前选中的存档。 */
    private void importSelected() {
        if (selectedIndex < 0 || selectedIndex >= saves.size()) {
            return;
        }
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        LegacySaveScanner.ImportResult result =
                LegacySaveScanner.importSave(saves.get(selectedIndex), gameDir);

        resultMessage = result.message();
        resultColor = result.success() ? 0xFF55FF55 : 0xFFFF5555;
        if (result.success()) {
            // 导完把该条从列表移除，避免重复导入出一堆 "(2)" 后缀的副本
            saves.remove(selectedIndex);
            selectedIndex = -1;
            // 列表可能变空，按钮布局要跟着变
            this.rebuildWidgets();
        }
    }

    /** 一次性导入全部存档。 */
    private void importAll() {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        int success = 0;
        int failed = 0;
        for (LegacySave save : new ArrayList<>(saves)) {
            if (LegacySaveScanner.importSave(save, gameDir).success()) {
                success++;
            } else {
                failed++;
            }
        }
        saves.clear();
        selectedIndex = -1;

        if (failed == 0) {
            resultMessage = "已导入 " + success + " 个存档";
            resultColor = 0xFF55FF55;
        } else {
            resultMessage = "已导入 " + success + " 个，失败 " + failed + " 个";
            resultColor = 0xFFFFAA00;
        }
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        this.rowRects.clear();
        this.importButtonRect = null;
        this.importButtonIndex = -1;

        int centerX = this.width / 2;

        graphics.drawCenteredString(this.font, this.title, centerX, TITLE_Y, 0xFFFFD700);
        graphics.drawCenteredString(this.font,
                Component.literal("发现了旧版整合包存档，是否导入？"),
                centerX, HINT_Y, 0xFFFFFF);

        int listBottom = this.height - BOTTOM_BAR_HEIGHT;
        int totalContentHeight = saves.size() * ROW_HEIGHT;
        scrollableArea.clampScroll(totalContentHeight);

        if (saves.isEmpty()) {
            String emptyText = resultMessage != null ? null : "没有发现旧版整合包存档";
            if (emptyText != null) {
                graphics.drawCenteredString(this.font, Component.literal(emptyText),
                        centerX, LIST_TOP + 20, 0xFFAAAAAA);
            }
        } else {
            // 裁剪到列表区域，滚动时上下不会溢出到标题或按钮上
            graphics.enableScissor(0, LIST_TOP, this.width, listBottom);

            int scrollOffset = scrollableArea.getScrollOffset();
            int firstVisible = scrollOffset / ROW_HEIGHT;
            int visibleCount = scrollableArea.getMaxVisible();

            for (int i = 0; i < visibleCount && firstVisible + i < saves.size(); i++) {
                int index = firstVisible + i;
                int rowY = LIST_TOP + index * ROW_HEIGHT - scrollOffset;
                renderRow(graphics, index, rowY, mouseX, mouseY);
            }

            graphics.disableScissor();
            scrollableArea.renderScrollbar(graphics, this.width, totalContentHeight);
        }

        // 结果提示固定在列表下方，不随滚动移动
        if (resultMessage != null) {
            graphics.drawCenteredString(this.font, Component.literal(resultMessage),
                    centerX, this.height - BOTTOM_BAR_HEIGHT + 4, resultColor);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // 展开的那一行上，导入按钮的悬停提示
        if (importButtonRect != null
                && mouseX >= importButtonRect[0] && mouseX < importButtonRect[2]
                && mouseY >= importButtonRect[1] && mouseY < importButtonRect[3]) {
            graphics.renderTooltip(this.font,
                    Component.literal("复制到当前存档目录，原存档保留"), mouseX, mouseY);
        }
    }

    /** 画一行存档，并记录它的点击区域。 */
    private void renderRow(GuiGraphics graphics, int index, int rowY, int mouseX, int mouseY) {
        LegacySave save = saves.get(index);
        int left = 12;
        int right = this.width - 22; // 给右侧滚动条留位置

        boolean hovered = mouseX >= left && mouseX < right
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;

        // 行底色：选中最亮，悬停次之
        int background = index == selectedIndex ? 0x80335577
                : hovered ? 0x40FFFFFF : 0x30000000;
        graphics.fill(left, rowY, right, rowY + ROW_HEIGHT - 2, background);

        // 最左侧画存档缩略图，没有 icon.png 时用原版未知存档图标
        int iconSize = ROW_HEIGHT - 10;
        int iconX = left + 5;
        int iconY = rowY + 4;
        renderIcon(graphics, save, iconX, iconY, iconSize);

        // 展开的那一行右侧要放「导入」按钮，文字得给它让位，
        // 否则长存档名会被按钮压住
        int textRight = right - 6;
        if (index == selectedIndex) {
            textRight = right - 6 - (IMPORT_BUTTON_WIDTH + 8);
        }
        int textLeft = iconX + iconSize + 6;
        int available = textRight - textLeft;

        // 第一行：存档名（白）+ 文件夹名（灰）
        String folder = save.directory().getFileName().toString();
        String name = save.levelName() == null || save.levelName().isEmpty()
                ? folder : save.levelName();
        int nameWidth = this.font.width(name);

        // 名字与文件夹名分开画，颜色不同；整体超出时逐个截断。
        // 判断能否放下必须用实际绘制的间隔 NAME_FOLDER_GAP，不能用
        // 空格宽度代替 —— 两者不等，会出现「判定放得下、画出来却溢出」
        int folderStart = nameWidth + NAME_FOLDER_GAP;
        if (folderStart + this.font.width(folder) <= available) {
            graphics.drawString(this.font, name, textLeft, rowY + 5, 0xFFFFFF, false);
            graphics.drawString(this.font, folder, textLeft + folderStart,
                    rowY + 5, 0xFF8A8A8A, false);
        } else {
            // 放不下就把文件夹名截短；还放不下连名字一起截
            int folderSpace = available - folderStart;
            if (folderSpace > 0) {
                graphics.drawString(this.font, name, textLeft, rowY + 5, 0xFFFFFF, false);
                graphics.drawString(this.font, trim(folder, folderSpace),
                        textLeft + folderStart, rowY + 5, 0xFF8A8A8A, false);
            } else {
                graphics.drawString(this.font, trim(name, available),
                        textLeft, rowY + 5, 0xFFFFFF, false);
            }
        }

        // 第二行：来源实例、最后游玩时间、游戏版本
        StringBuilder detail = new StringBuilder();
        detail.append("来自 ").append(save.sourceName());
        detail.append("  |  最后游玩 ").append(save.lastPlayedText());
        if (!save.versionText().isEmpty()) {
            detail.append("  |  版本 ").append(save.versionText());
        }
        graphics.drawString(this.font, trim(detail.toString(), available),
                textLeft, rowY + 20, 0xFFAAAAAA, false);

        this.rowRects.add(new int[]{left, rowY, right, rowY + ROW_HEIGHT - 2});

        // 选中的那一行右侧放「导入」按钮，位置与点击检测共用同一份坐标
        if (index == selectedIndex) {
            int buttonWidth = IMPORT_BUTTON_WIDTH;
            int buttonHeight = 16;
            int buttonX = right - buttonWidth - 6;
            int buttonY = rowY + (ROW_HEIGHT - 2 - buttonHeight) / 2;

            boolean buttonHovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                    && mouseY >= buttonY && mouseY < buttonY + buttonHeight;
            int buttonColor = buttonHovered ? 0xFF3F7F3F : 0xFF2F5F2F;

            graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
            graphics.drawCenteredString(this.font, Component.literal("导入"),
                    buttonX + buttonWidth / 2, buttonY + (buttonHeight - 8) / 2, 0xFFFFFF);

            this.importButtonRect = new int[]{buttonX, buttonY,
                    buttonX + buttonWidth, buttonY + buttonHeight};
            this.importButtonIndex = index;
        }
    }

    /** 存档名与文件夹名之间的间隔。 */
    private static final int NAME_FOLDER_GAP = 6;

    /**
     * 画存档缩略图。
     * <p>贴图尺寸随玩家的 icon.png 而变，uv 与「贴图实际尺寸」必须对上，
     * 否则 {@code innerBlit} 里按贴图宽高做的归一化会算错、图会糊或错位。
     * 所以这里显式传入真实的贴图宽高，把整张图缩放到目标方格。
     */
    private void renderIcon(GuiGraphics graphics, LegacySave save,
                            int x, int y, int size) {
        ResourceLocation texture = SaveIconCache.get(save.directory());
        int textureWidth = SaveIconCache.getWidth(save.directory());
        int textureHeight = SaveIconCache.getHeight(save.directory());
        graphics.blit(texture,
                x, y,                       // 目标左上角
                size, size,                 // 目标宽高（缩放）
                0.0F, 0.0F,                 // uv 起点
                textureWidth, textureHeight, // 取整张贴图
                textureWidth, textureHeight);// 贴图真实尺寸，供归一化
    }

    /**
     * 按可用宽度截断文字，超出部分用省略号收尾。
     * <p>窗口窄时存档名与来源信息都可能长过一行，直接画会顶到屏幕外
     * 被裁掉，反而看不到关键的时间信息。
     */
    private String trim(String text, int maxWidth) {
        if (maxWidth <= 0 || this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        // 宽度连省略号都放不下时，省略号本身就会溢出，此时直接不画，
        // 免得文字戳到裁剪区外面
        if (ellipsisWidth > maxWidth) {
            return "";
        }
        // 从后往前缩，直到加上省略号也放得下
        int end = text.length();
        while (end > 0
                && this.font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滚动条：这个界面本来就画了滚动条，但原先只能滚轮、拖不动，
        // 与其它几个列表界面不一致。滚动条在行区域右侧，不会和行/导入按钮抢点击
        if (button == 0 && scrollableArea.beginScrollbarDrag(mouseX, mouseY, this.width,
                saves.size() * ROW_HEIGHT)) {
            return true;
        }

        if (button == 0) {
            // 先判导入按钮，否则点它会先被行选中逻辑吃掉
            if (importButtonRect != null && importButtonIndex == selectedIndex
                    && mouseX >= importButtonRect[0] && mouseX < importButtonRect[2]
                    && mouseY >= importButtonRect[1] && mouseY < importButtonRect[3]) {
                importSelected();
                return true;
            }

            for (int i = 0; i < rowRects.size(); i++) {
                int[] rect = rowRects.get(i);
                if (mouseX >= rect[0] && mouseX < rect[2]
                        && mouseY >= rect[1] && mouseY < rect[3]) {
                    // rowRects 按可见顺序记录，需要换算回 saves 里的真实下标
                    int realIndex = scrollableArea.getScrollOffset() / ROW_HEIGHT + i;
                    // 再点一次同一行收起，避免误展开后没法取消
                    selectedIndex = selectedIndex == realIndex ? -1 : realIndex;
                    resultMessage = null;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollableArea.dragScrollbar(mouseY, this.width, saves.size() * ROW_HEIGHT)) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int totalContentHeight = saves.size() * ROW_HEIGHT;
        boolean scrolled = scrollableArea.mouseScrolled(mouseY, delta, totalContentHeight);
        return scrolled || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        // 离开本界面时释放缩略图显存：存档可能已被导入或删除，
        // 下次进来重新读，也避免长期占着用不到的贴图
        SaveIconCache.clear();
    }
}
