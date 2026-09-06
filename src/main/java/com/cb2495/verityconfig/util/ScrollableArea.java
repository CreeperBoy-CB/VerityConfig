package com.cb2495.verityconfig.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public class ScrollableArea {
    private int scrollOffset = 0;
    private final int viewportTop;
    private final int viewportBottom;
    private final int scrollbarWidth = 4;
    private final int scrollbarMargin = 2;
    private int itemHeight = 0; // 固定行高，若 >0 则启用固定行高模式

    public ScrollableArea(int viewportTop, int viewportBottom) {
        this.viewportTop = viewportTop;
        this.viewportBottom = viewportBottom;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int offset) {
        this.scrollOffset = offset;
    }

    public int getViewportTop() {
        return viewportTop;
    }

    public int getViewportBottom() {
        return viewportBottom;
    }

    /**
     * 设置固定行高（像素），用于固定条目高度的列表。
     * 若设置为0或负数，则禁用固定行高模式。
     */
    public void setItemHeight(int itemHeight) {
        this.itemHeight = itemHeight;
    }

    /**
     * 获取最大可见条目数（仅在固定行高模式下有效）。
     * 未设置行高时返回0。
     */
    public int getMaxVisible() {
        if (itemHeight <= 0) return 0;
        int visibleHeight = viewportBottom - viewportTop;
        return Math.max(1, visibleHeight / itemHeight);
    }

    /**
     * 根据总内容高度限制滚动偏移
     */
    public void clampScroll(int totalContentHeight) {
        int maxScroll = getMaxScroll(totalContentHeight);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    public int getMaxScroll(int totalContentHeight) {
        int visibleHeight = viewportBottom - viewportTop;
        return Math.max(0, totalContentHeight - visibleHeight);
    }

    /**
     * 处理鼠标滚轮事件，适用于动态高度内容。
     * 若滚动成功返回 true。
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int totalContentHeight) {
        if (totalContentHeight > viewportBottom - viewportTop) {
            scrollOffset -= (int) delta * 10;
            clampScroll(totalContentHeight);
            return true;
        }
        return false;
    }

    /**
     * 判断鼠标是否在滚动条上
     */
    public boolean isMouseOverScrollbar(double mouseX, double mouseY, int screenWidth, int totalContentHeight) {
        int maxScroll = getMaxScroll(totalContentHeight);
        if (maxScroll <= 0) return false;

        int trackX = screenWidth - scrollbarMargin - scrollbarWidth;
        int trackY = viewportTop;
        int trackHeight = viewportBottom - viewportTop;

        return mouseX >= trackX && mouseX <= trackX + scrollbarWidth &&
                mouseY >= trackY && mouseY <= trackY + trackHeight;
    }

    /**
     * 根据鼠标 Y 坐标更新滚动偏移（拖动滚动条）
     */
    public void updateScrollFromMouse(double mouseY, int screenWidth, int totalContentHeight) {
        int maxScroll = getMaxScroll(totalContentHeight);
        if (maxScroll <= 0) return;

        int trackY = viewportTop;
        int trackHeight = viewportBottom - viewportTop;
        int sliderHeight = Math.max(10, trackHeight * trackHeight / Math.max(1, totalContentHeight));
        int maxSliderY = trackY + trackHeight - sliderHeight;

        double targetY = Mth.clamp(mouseY - sliderHeight / 2.0, trackY, maxSliderY);
        float ratio = (float)((targetY - trackY) / (maxSliderY - trackY));
        scrollOffset = Math.round(ratio * maxScroll);
    }

    /**
     * 绘制滚动条
     */
    public void renderScrollbar(GuiGraphics graphics, int screenWidth, int totalContentHeight) {
        int maxScroll = getMaxScroll(totalContentHeight);
        if (maxScroll <= 0) return;

        int trackX = screenWidth - scrollbarMargin - scrollbarWidth;
        int trackY = viewportTop;
        int trackHeight = viewportBottom - viewportTop;
        graphics.fill(trackX, trackY, trackX + scrollbarWidth, trackY + trackHeight, 0xFF333333);

        int sliderHeight = Math.max(10, trackHeight * trackHeight / Math.max(1, totalContentHeight));
        int maxSliderY = trackY + trackHeight - sliderHeight;
        int sliderY = trackY + (maxSliderY - trackY) * scrollOffset / maxScroll;
        graphics.fill(trackX, sliderY, trackX + scrollbarWidth, sliderY + sliderHeight, 0xFFAAAAAA);
    }
}