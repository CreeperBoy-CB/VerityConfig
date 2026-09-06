package com.cb2495.verityconfig;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DropdownWidget<T> extends AbstractWidget {
    private final List<T> options;
    private final Function<T, Component> displayText;
    private final Consumer<T> onSelect;
    private T selected;
    private boolean expanded = false;
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE = 6;          // 最多可见选项数
    private static final int ITEM_HEIGHT = 12;         // 每个选项高度
    private static final int SCROLLBAR_WIDTH = 4;      // 滚动条宽度

    private boolean draggingScrollbar = false;

    public DropdownWidget(int x, int y, int width, int height,
                          Component message, List<T> options, T initialValue,
                          Function<T, Component> displayText, Consumer<T> onSelect) {
        super(x, y, width, height, message);
        this.options = options;
        this.displayText = displayText;
        this.onSelect = onSelect;
        this.selected = initialValue;
    }

    private static DropdownWidget<?> currentlyOpen = null;

    public void collapse() {
        this.expanded = false;
        if (currentlyOpen == this) {
            currentlyOpen = null;
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 按钮背景
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
        graphics.renderOutline(getX(), getY(), width, height, 0xFFAAAAAA);

        // 选中文字
        Component text = displayText.apply(selected);
        graphics.drawString(Minecraft.getInstance().font, text, getX() + 4, getY() + (height - 8) / 2, 0xFFFFFF, false);

        // 下拉箭头
        graphics.drawString(Minecraft.getInstance().font, "▽", getX() + width - 12, getY() + (height - 8) / 2, 0xFFFFFF, false);

        // 展开列表
        if (expanded) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100); // 提高层级，避免被其他控件遮挡

            int listY = getY() + height + 1;
            int visibleCount = Math.min(MAX_VISIBLE, options.size());
            int totalHeight = visibleCount * ITEM_HEIGHT + 4;

            // 列表背景：半透明黑色
            graphics.fill(getX(), listY, getX() + width, listY + totalHeight, 0xCC000000);

            // 先绘制滚动条轨道（右侧）
            int trackX = getX() + width - SCROLLBAR_WIDTH;
            graphics.fill(trackX, listY + 1, trackX + SCROLLBAR_WIDTH, listY + totalHeight - 1, 0xFF333333);

            int maxScroll = Math.max(0, options.size() - MAX_VISIBLE);
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

            // 绘制所有可见选项
            for (int i = scrollOffset; i < Math.min(scrollOffset + MAX_VISIBLE, options.size()); i++) {
                int optionY = listY + 2 + (i - scrollOffset) * ITEM_HEIGHT;
                boolean isHovered = mouseX >= getX() && mouseX <= getX() + width &&
                        mouseY >= optionY && mouseY <= optionY + 10;

                // 选项背景（留出滚动条空间）
                int optionLeft = getX() + 1;
                int optionRight = trackX - 1; // 不让选项覆盖滚动条
                if (isHovered) {
                    graphics.fill(optionLeft, optionY, optionRight, optionY + 10, 0xFF2B2B2B);
                    graphics.renderOutline(optionLeft, optionY, optionRight - optionLeft, 10, 0xFFFFFFFF);
                } else {
                    graphics.fill(optionLeft, optionY, optionRight, optionY + 10, 0xFF1E1E1E);
                }

                graphics.drawString(Minecraft.getInstance().font,
                        displayText.apply(options.get(i)),
                        getX() + 4, optionY + 1, isHovered ? 0xFFFFFF : 0xCCCCCC, false);
            }

            // 绘制滚动条滑块
            if (maxScroll > 0) {
                int sliderHeight = Math.max(10, totalHeight * MAX_VISIBLE / options.size());
                int sliderY = listY + 2 + (totalHeight - 4 - sliderHeight) * scrollOffset / maxScroll;
                graphics.fill(trackX, sliderY, trackX + SCROLLBAR_WIDTH, sliderY + sliderHeight, 0xFFAAAAAA);
            }

            // 最后绘制列表边框
            graphics.renderOutline(getX(), listY, width, totalHeight, 0xFF555555);

            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (expanded) {
                int listY = getY() + height + 1;
                int visibleCount = Math.min(MAX_VISIBLE, options.size());
                int totalHeight = visibleCount * ITEM_HEIGHT + 4;

                // 判断鼠标是否在按钮或列表区域内（与 isMouseOver 一致）
                boolean inButton = mouseX >= getX() && mouseX <= getX() + width
                        && mouseY >= getY() && mouseY <= getY() + height;
                boolean inList = mouseX >= getX() && mouseX <= getX() + width
                        && mouseY >= listY && mouseY <= listY + totalHeight;

                // 如果点击在按钮或列表区域，都消费事件
                if (!inButton && !inList) {
                    expanded = false;   // 关闭菜单
                    if (currentlyOpen == this) {
                        currentlyOpen = null;
                    }
                    return false;
                }

                // 点击在列表区域
                if (inList) {
                    int trackX = getX() + width - SCROLLBAR_WIDTH;

                    // 滚动条拖动
                    if (mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH) {
                        draggingScrollbar = true;
                        updateScrollFromMouse(mouseY, listY, totalHeight);
                        return true;
                    }

                    // 选项选择（避开滚动条区域）
                    int optionAreaRight = getX() + width - SCROLLBAR_WIDTH;
                    int relativeY = (int) mouseY - listY;
                    int index = scrollOffset + relativeY / ITEM_HEIGHT;
                    if (relativeY >= 0 && index >= 0 && index < options.size()
                            && mouseX >= getX() && mouseX <= optionAreaRight) {
                        selected = options.get(index);
                        onSelect.accept(selected);
                        expanded = false;
                        if (currentlyOpen == this) {
                            currentlyOpen = null;
                        }
                        return true;

                    }

                    expanded = false;
                    if (currentlyOpen == this) {
                        currentlyOpen = null;
                    }
                    return true;
                }

                // 点击在按钮上：切换展开状态
                expanded = !expanded;
                return true;
            } else {
                if (isMouseOver(mouseX, mouseY)) {
                    // 关闭之前展开的下拉框
                    if (currentlyOpen != null && currentlyOpen != this) {
                        currentlyOpen.collapse();
                    }
                    expanded = true;
                    currentlyOpen = this;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (expanded && isMouseOver(mouseX, mouseY)) {
            int maxScroll = Math.max(0, options.size() - MAX_VISIBLE);
            if (maxScroll > 0) {
                scrollOffset = Mth.clamp(scrollOffset - (int) delta, 0, maxScroll);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (expanded) {
            int listY = getY() + height + 1;
            int visibleCount = Math.min(MAX_VISIBLE, options.size());
            int totalHeight = visibleCount * ITEM_HEIGHT + 4;
            return mouseX >= getX() && mouseX <= getX() + width
                    && mouseY >= getY() && mouseY <= listY + totalHeight;
        }
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && expanded) {
            int listY = getY() + height + 1;
            int visibleCount = Math.min(MAX_VISIBLE, options.size());
            int totalHeight = visibleCount * ITEM_HEIGHT + 4;
            updateScrollFromMouse(mouseY, listY, totalHeight);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }

    private void updateScrollFromMouse(double mouseY, int listY, int totalHeight) {
        int maxScroll = Math.max(0, options.size() - MAX_VISIBLE);
        if (maxScroll == 0) return;
        int sliderHeight = Math.max(10, totalHeight * MAX_VISIBLE / options.size());
        int trackHeight = totalHeight - 4;
        int maxSliderY = listY + 2 + trackHeight - sliderHeight;
        int clampedY = (int) Mth.clamp(mouseY - listY - 2 - sliderHeight / 2, 0, maxSliderY - listY - 2);
        scrollOffset = (int) Math.round((double) clampedY / (maxSliderY - listY - 2) * maxScroll);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    public T getSelected() {
        return selected;
    }
}