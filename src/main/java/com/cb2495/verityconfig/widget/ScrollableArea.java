package com.cb2495.verityconfig.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 列表的滚动状态与滚动条。
 * <p>滚轮的响应范围由这里统一判断：只有鼠标落在视口内才滚动，
 * 否则在标题栏/遮罩上滚滚轮也会把列表滚走。各界面原本各写一份，
 * 只有「高级设置」写对了，其余四处都漏了这个判断。
 */
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

    /** 鼠标是否落在视口内（滚轮只在这里生效）。 */
    public boolean isMouseInsideViewport(double mouseY) {
        return mouseY >= viewportTop && mouseY <= viewportBottom;
    }

    /**
     * 处理鼠标滚轮事件。
     * <p>只有鼠标在视口内、且内容确实超出视口时才滚动；滚动成功返回 true。
     *
     * @param mouseY 鼠标 Y 坐标，用于判断是否在视口内
     */
    public boolean mouseScrolled(double mouseY, double delta, int totalContentHeight) {
        if (!isMouseInsideViewport(mouseY)) return false;
        if (totalContentHeight <= viewportBottom - viewportTop) return false;
        scrollOffset -= (int) delta * 10;
        clampScroll(totalContentHeight);
        return true;
    }

    /**
     * 滑块高度：按视口与内容的比例算，并保证一个最小可抓取高度。
     * <p>绘制与拖动共用，避免两处算出的高度不一致。
     */
    private int sliderHeight(int trackHeight, int totalContentHeight) {
        return Math.max(10, trackHeight * trackHeight / Math.max(1, totalContentHeight));
    }

    /**
     * 判断鼠标是否在滚动条上。
     * <p>私有：滚动条只通过上面的「按下 / 拖动 / 松开」三个方法使用，
     * 各个界面不需要自己去做这个判断。
     */
    private boolean isMouseOverScrollbar(double mouseX, double mouseY, int screenWidth, int totalContentHeight) {
        int maxScroll = getMaxScroll(totalContentHeight);
        if (maxScroll <= 0) return false;

        int trackX = screenWidth - scrollbarMargin - scrollbarWidth;
        int trackY = viewportTop;
        int trackHeight = viewportBottom - viewportTop;

        return mouseX >= trackX && mouseX <= trackX + scrollbarWidth &&
                mouseY >= trackY && mouseY <= trackY + trackHeight;
    }

    // ---------- 拖动滚动条 ----------
    /**
     * 是否正在拖动滚动条。
     * <p>状态放在这里而不是各个界面里：5 个界面原本各自维护一个
     * {@code draggingScrollbar} 字段，再各写一遍「按下 / 拖动 / 松开」三件套，
     * 坐标换算抄错一处就会出现「拖不动」或「拖到别处」。
     */
    private boolean draggingScrollbar = false;

    /** 鼠标左键按下：若落在滚动条上则开始拖动，返回是否消费该点击。 */
    public boolean beginScrollbarDrag(double mouseX, double mouseY, int screenWidth, int totalContentHeight) {
        if (!isMouseOverScrollbar(mouseX, mouseY, screenWidth, totalContentHeight)) return false;
        draggingScrollbar = true;
        updateScrollFromMouse(mouseY, screenWidth, totalContentHeight);
        return true;
    }

    /** 拖动中：更新滚动位置。未在拖动时返回 false。 */
    public boolean dragScrollbar(double mouseY, int screenWidth, int totalContentHeight) {
        if (!draggingScrollbar) return false;
        updateScrollFromMouse(mouseY, screenWidth, totalContentHeight);
        return true;
    }

    /** 鼠标松开：结束拖动。未在拖动、或不是左键时返回 false。 */
    public boolean endScrollbarDrag(int button) {
        if (button != 0 || !draggingScrollbar) return false;
        draggingScrollbar = false;
        return true;
    }

    /**
     * 根据鼠标 Y 坐标更新滚动偏移。私有：由拖动方法内部调用。
     */
    private void updateScrollFromMouse(double mouseY, int screenWidth, int totalContentHeight) {
        int maxScroll = getMaxScroll(totalContentHeight);
        if (maxScroll <= 0) return;

        int trackY = viewportTop;
        int trackHeight = viewportBottom - viewportTop;
        int sliderHeight = sliderHeight(trackHeight, totalContentHeight);
        int maxSliderY = trackY + trackHeight - sliderHeight;

        // 视口极矮时滑块触底，可拖动范围是 0：直接留在顶部。
        // 不做这个判断会让 ratio 变成 0/0 = NaN，Math.round(NaN) 得 0，
        // 表现为一拖滚动条位置就跳回顶端。
        if (maxSliderY - trackY <= 0) {
            scrollOffset = 0;
            return;
        }

        double targetY = Mth.clamp(mouseY - sliderHeight / 2.0, trackY, maxSliderY);
        float ratio = (float) ((targetY - trackY) / (maxSliderY - trackY));
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

        int sliderHeight = sliderHeight(trackHeight, totalContentHeight);
        int maxSliderY = trackY + trackHeight - sliderHeight;
        int sliderY = trackY + (maxSliderY - trackY) * scrollOffset / maxScroll;
        graphics.fill(trackX, sliderY, trackX + scrollbarWidth, sliderY + sliderHeight, 0xFFAAAAAA);
    }
}
