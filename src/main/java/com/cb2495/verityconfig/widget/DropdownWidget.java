package com.cb2495.verityconfig.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
    /**
     * 展开列表的宽度。默认与按钮同宽；设为更大值时，按钮保持窄宽度，
     * 但展开的列表按此宽度绘制，避免长选项文字被截断。
     */
    private int expandedWidth = -1;

    /**
     * 判定某个选项是否被禁用（灰显且不可选中）。
     * <p>默认没有禁用的选项，需要时由构造方通过
     * {@link #setOptionDisabled} 覆盖。返回 true 时点击不改变选中值，
     * 改为调用 {@link #setOnDisabledClick} 注册的回调。
     */
    private Predicate<T> optionDisabled = value -> false;

    /**
     * 点击被禁用选项时的回调，用于给出「为什么不能选」的反馈。
     * <p>默认什么都不做；为 null 时点击被禁用的选项等同于点击空白，
     * 只收起列表、不改变选中值。
     */
    private Consumer<T> onDisabledClick;

    public DropdownWidget(int x, int y, int width, int height,
                          Component message, List<T> options, T initialValue,
                          Function<T, Component> displayText, Consumer<T> onSelect) {
        super(x, y, width, height, message);
        this.options = options;
        this.displayText = displayText;
        this.onSelect = onSelect;
        this.selected = initialValue;
    }

    /** 设置展开列表的宽度（按钮宽度不变）。传 0 或负数表示恢复为与按钮同宽。 */
    public void setExpandedWidth(int expandedWidth) {
        this.expandedWidth = expandedWidth;
    }

    /**
     * 设置选项禁用判定。传 null 表示恢复为「所有选项都可选」。
     * <p>已选中的项即使被判定为禁用，也仍然正常显示为当前值 ——
     * 禁用只拦截<b>新的选择</b>，不会把已有配置清掉。
     */
    public void setOptionDisabled(Predicate<T> optionDisabled) {
        this.optionDisabled = optionDisabled != null ? optionDisabled : value -> false;
    }

    /** 设置点击被禁用选项时的回调（用于弹提示、报错等）。 */
    public void setOnDisabledClick(Consumer<T> onDisabledClick) {
        this.onDisabledClick = onDisabledClick;
    }

    /**
     * 在按钮内弹出警告：边框变红，选中文字被 {@code text} 替换并左右抖动，
     * 之后自动淡出。用于告诉玩家「为什么这一项选不了」。
     * <p>警告期间本下拉框的点击会被忽略一段时间（见
     * {@link #WARN_IGNORE_CLICK_MS}），避免连点让抖动与淡出反复从头开始。
     * <p>警告只在按钮内部绘制，文字会按按钮宽度裁剪，不需要调用方
     * 另外计算屏幕边界。
     */
    public void showWarning(Component text) {
        showWarningInternal(text);
    }

    /** 该选项当前是否被禁用。 */
    private boolean isDisabled(T value) {
        return optionDisabled.test(value);
    }

    // ---------- 按钮内的警告覆盖层 ----------
    /**
     * 警告提示的显示时长与淡出时长（毫秒）。
     * <p>取值与 {@code ModsListScreen} 的平台拒绝提示一致，两处观感统一。
     */
    private static final long WARN_HOLD_MS = 2500;
    private static final long WARN_FADE_MS = 500;
    /** 淡出结束后再多留一会儿才清状态，避免临界帧反复切换。 */
    private static final long WARN_CLEAR_GRACE_MS = 200;

    /** 抖动幅度（像素）、单次完整往复周期与衰减到 0 的总时长（毫秒）。 */
    private static final float WARN_SHAKE_AMPLITUDE = 6f;
    private static final long WARN_SHAKE_PERIOD_MS = 180;
    private static final long WARN_SHAKE_DURATION_MS = 600;

    /** 警告文字与边框的颜色。 */
    private static final int WARN_COLOR = 0xFFFF5555;
    /** 警告文字左边距（与选中文字的 4px 对齐）。 */
    private static final int WARN_TEXT_INSET = 4;

    /**
     * 忽略点击的总时长（毫秒）：从弹出警告那一刻起，
     * 这段时间内点击<b>本下拉框</b>不生效。
     * <p>必须<b>大于</b> {@link #WARN_HOLD_MS} + {@link #WARN_FADE_MS}，
     * 并覆盖到 {@link #clearWarnIfExpired()} 真正清空状态的时刻。
     * <p>否则会出现漏洞窗口：淡出结束后再点被禁用项，会走
     * {@link #showWarning} 重置计时，警告永远不消失。
     * <p>更重要的是它挡住了「抖动中连点」：每次点击都重置计时基准，
     * 会让抖动幅度反复回到最大、alpha 反复跳回 1，看起来就是画面在闪。
     */
    private static final long WARN_IGNORE_CLICK_MS = 3250;

    /** 警告起始时间戳；-1 表示当前没有警告。 */
    private long warnAt = -1;
    /** 警告文字；为 null 表示当前没有警告。 */
    private Component warnText;

    /**
     * 弹出警告：按钮边框变红，选中文字被警告文字替换，并左右抖动。
     * <p>重复调用只刷新起始时间，不叠加幅度或时长。
     */
    private void showWarningInternal(Component text) {
        this.warnText = text;
        this.warnAt = System.currentTimeMillis();
    }

    /**
     * 当前是否处于「忽略点击」窗口内。
     * <p>只依据时间戳判断，不要求 {@code warnText} 仍非空，
     * 因此状态清除前后都连续生效，没有边界缝隙。
     */
    private boolean warnClicksIgnored() {
        return warnAt >= 0 && System.currentTimeMillis() - warnAt < WARN_IGNORE_CLICK_MS;
    }

    /**
     * 警告的不透明度（0~1）。
     * <p>只读方法，不修改任何状态：状态清除统一交给
     * {@link #clearWarnIfExpired()}，否则同一帧内抖动偏移读到的
     * 计时基准会和这里不一致。
     */
    private float warnAlpha() {
        if (warnText == null || warnAt < 0) return 0f;
        long elapsed = System.currentTimeMillis() - warnAt;
        if (elapsed >= WARN_HOLD_MS + WARN_FADE_MS) return 0f;
        if (elapsed <= WARN_HOLD_MS) return 1f;
        return 1f - (float) (elapsed - WARN_HOLD_MS) / WARN_FADE_MS;
    }

    /**
     * 警告期间警告文字的水平晃动偏移（像素）。
     * <p>正弦往复，幅度在 {@link #WARN_SHAKE_DURATION_MS} 内连续线性衰减到 0。
     * <p>用连续衰减而不是「每 100ms 硬减 1px」：后者会在每个 100ms 边界
     * 让幅度瞬间掉 1px，该边界与 180ms 的正弦周期不同步，相位对齐时
     * 合成位移会在相邻两帧间突跳数像素，肉眼即「画面抖了一下」。
     */
    private float warnShakeOffset() {
        if (warnText == null || warnAt < 0) return 0f;
        long elapsed = System.currentTimeMillis() - warnAt;
        if (elapsed >= WARN_SHAKE_DURATION_MS) return 0f;

        float amplitude = WARN_SHAKE_AMPLITUDE
                * (1f - (float) elapsed / WARN_SHAKE_DURATION_MS);
        double phase = (double) elapsed / WARN_SHAKE_PERIOD_MS * Math.PI * 2;
        return (float) Math.sin(phase) * amplitude;
    }

    /** 警告彻底结束后清除状态，避免残留影响后续绘制与点击。 */
    private void clearWarnIfExpired() {
        if (warnText != null && warnAt >= 0
                && System.currentTimeMillis() - warnAt
                        >= WARN_HOLD_MS + WARN_FADE_MS + WARN_CLEAR_GRACE_MS) {
            warnText = null;
            warnAt = -1;
        }
    }

    /** 把 0~1 的不透明度应用到 ARGB 颜色上。 */
    private static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** 展开列表实际使用的宽度。 */
    private int listWidth() {
        return expandedWidth > 0 ? expandedWidth : width;
    }

    /**
     * 当前展开的下拉框，用于「打开一个就收起另一个」。
     * <p>用弱引用而不是强引用：{@code onSelect} 回调通常捕获了它所属的 Screen，
     * 而这个字段的生命周期和游戏一样长，强引用会让已经关闭的界面一直无法回收。
     * <p>弱引用不影响正确性：下拉框展开时必然被所属 Screen 的控件表强引用着，
     * 只有界面关闭、它已经不可达之后才会被回收，那时也确实不该再收起谁。
     */
    private static WeakReference<DropdownWidget<?>> currentlyOpen = new WeakReference<>(null);

    private static DropdownWidget<?> currentOpen() {
        return currentlyOpen.get();
    }

    private static void setCurrentlyOpen(DropdownWidget<?> widget) {
        currentlyOpen = new WeakReference<>(widget);
    }

    /** 收起自己，并清掉「当前展开」的登记（登记的确实是自己时）。 */
    private void collapseSelf() {
        this.expanded = false;
        if (currentOpen() == this) {
            setCurrentlyOpen(null);
        }
    }

    public void collapse() {
        collapseSelf();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 先清理已结束的警告，再取值：warnAlpha() 只读，状态清除必须单独进行，
        // 否则同一帧内后续读取到的值与判断依据会不一致。
        // 每帧只求值一次并缓存，保证同一帧内边框与文字用同一个 alpha
        clearWarnIfExpired();
        float warnA = warnAlpha();

        // 按钮背景
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
        // 警告期间边框变红，否则维持默认灰边
        graphics.renderOutline(getX(), getY(), width, height,
                warnA > 0f ? withAlpha(WARN_COLOR, warnA) : 0xFFAAAAAA);

        int textY = getY() + (height - 8) / 2;

        if (warnA > 0f && warnText != null) {
            // 警告文字替代选中文字，画在按钮内部：
            // 抖动幅度按左右两侧各自预留，避免抖到边缘时被按钮边界切掉；
            // 文字比按钮宽时以左内边距为起点，超出部分自然被右侧裁掉。
            int shake = (int) warnShakeOffset();
            int hintW = Minecraft.getInstance().font.width(warnText);
            int maxX = getX() + width - WARN_TEXT_INSET - hintW;
            int textX = Math.min(getX() + WARN_TEXT_INSET + shake, maxX);
            // 极窄按钮下 maxX 可能小于左内边距，这里兜底避免文字跑到按钮左侧外面
            textX = Math.max(textX, getX() + WARN_TEXT_INSET);
            graphics.drawString(Minecraft.getInstance().font, warnText, textX, textY,
                    withAlpha(WARN_COLOR, warnA), false);
        } else {
            // 选中文字
            Component text = displayText.apply(selected);
            graphics.drawString(Minecraft.getInstance().font, text, getX() + WARN_TEXT_INSET, textY, 0xFFFFFF, false);
        }

        // 下拉箭头
        graphics.drawString(Minecraft.getInstance().font, "▽", getX() + width - 12, textY, 0xFFFFFF, false);

        // 展开列表
        if (expanded) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100); // 提高层级，避免被其他控件遮挡

            int listY = getY() + height + 1;
            int lw = listWidth(); // 列表可比按钮宽，避免长选项被截断
            int visibleCount = Math.min(MAX_VISIBLE, options.size());
            int totalHeight = visibleCount * ITEM_HEIGHT + 4;

            // 列表背景：半透明黑色
            graphics.fill(getX(), listY, getX() + lw, listY + totalHeight, 0xCC000000);

            // 先绘制滚动条轨道（右侧）
            int trackX = getX() + lw - SCROLLBAR_WIDTH;
            graphics.fill(trackX, listY + 1, trackX + SCROLLBAR_WIDTH, listY + totalHeight - 1, 0xFF333333);

            int maxScroll = Math.max(0, options.size() - MAX_VISIBLE);
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

            // 绘制所有可见选项
            for (int i = scrollOffset; i < Math.min(scrollOffset + MAX_VISIBLE, options.size()); i++) {
                int optionY = listY + 2 + (i - scrollOffset) * ITEM_HEIGHT;
                boolean isHovered = mouseX >= getX() && mouseX <= getX() + lw &&
                        mouseY >= optionY && mouseY <= optionY + 10;
                // 禁用项：灰字、不画悬停高亮，让「点了没反应」在视觉上先有预告
                boolean disabled = isDisabled(options.get(i));
                if (disabled) {
                    isHovered = false;
                }

                // 选项背景（留出滚动条空间）
                int optionLeft = getX() + 1;
                int optionRight = trackX - 1; // 不让选项覆盖滚动条
                if (isHovered) {
                    graphics.fill(optionLeft, optionY, optionRight, optionY + 10, 0xFF2B2B2B);
                    graphics.renderOutline(optionLeft, optionY, optionRight - optionLeft, 10, 0xFFFFFFFF);
                } else {
                    graphics.fill(optionLeft, optionY, optionRight, optionY + 10, 0xFF1E1E1E);
                }

                int textColor = disabled ? 0xFF666666 : (isHovered ? 0xFFFFFFFF : 0xFFCCCCCC);
                graphics.drawString(Minecraft.getInstance().font,
                        displayText.apply(options.get(i)),
                        getX() + 4, optionY + 1, textColor, false);
            }

            // 绘制滚动条滑块
            if (maxScroll > 0) {
                int sliderHeight = Math.max(10, totalHeight * MAX_VISIBLE / options.size());
                int sliderY = listY + 2 + (totalHeight - 4 - sliderHeight) * scrollOffset / maxScroll;
                graphics.fill(trackX, sliderY, trackX + SCROLLBAR_WIDTH, sliderY + sliderHeight, 0xFFAAAAAA);
            }

            // 最后绘制列表边框
            graphics.renderOutline(getX(), listY, lw, totalHeight, 0xFF555555);

            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 警告期间忽略点击：连点会让 showWarning 反复重置计时基准，
            // 抖动幅度与 alpha 每次都被拉回起点，看起来就是画面在闪。
            // 这里连「展开/收起」一起挡掉，警告结束前按钮保持按下前的状态。
            if (warnClicksIgnored()) {
                return true;
            }
            if (expanded) {
                int listY = getY() + height + 1;
                int lw = listWidth();
                int visibleCount = Math.min(MAX_VISIBLE, options.size());
                int totalHeight = visibleCount * ITEM_HEIGHT + 4;

                // 判断鼠标是否在按钮或列表区域内（与 isMouseOver 一致）
                // 注意按钮用 width，列表用 listWidth()，两者可能不同宽
                boolean inButton = mouseX >= getX() && mouseX <= getX() + width
                        && mouseY >= getY() && mouseY <= getY() + height;
                boolean inList = mouseX >= getX() && mouseX <= getX() + lw
                        && mouseY >= listY && mouseY <= listY + totalHeight;

                // 如果点击在按钮或列表区域，都消费事件
                if (!inButton && !inList) {
                    collapseSelf();   // 关闭菜单
                    return false;
                }

                // 点击在列表区域
                if (inList) {
                    int trackX = getX() + lw - SCROLLBAR_WIDTH;

                    // 滚动条拖动
                    if (mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH) {
                        draggingScrollbar = true;
                        updateScrollFromMouse(mouseY, listY, totalHeight);
                        return true;
                    }

                    // 选项选择（避开滚动条区域）
                    int optionAreaRight = getX() + lw - SCROLLBAR_WIDTH;
                    int relativeY = (int) mouseY - listY;
                    int index = scrollOffset + relativeY / ITEM_HEIGHT;
                    if (relativeY >= 0 && index >= 0 && index < options.size()
                            && mouseX >= getX() && mouseX <= optionAreaRight) {
                        T picked = options.get(index);
                        // 禁用项：不改变选中值，改走「为什么不能选」的回调。
                        // 这里**不**收起列表也不返回 true —— 交给 Screen 自己决定，
                        // 否则回调里想做「变红 + 抖动 + 提示」时列表已经收起来了，
                        // 用户看不到自己点的是哪一项。
                        if (isDisabled(picked)) {
                            if (onDisabledClick != null) {
                                onDisabledClick.accept(picked);
                            }
                            return true;
                        }
                        selected = picked;
                        onSelect.accept(selected);
                        collapseSelf();
                        return true;

                    }

                    collapseSelf();
                    return true;
                }

                // 点击在按钮上：切换展开状态
                expanded = !expanded;
                return true;
            } else {
                if (isMouseOver(mouseX, mouseY)) {
                    // 关闭之前展开的下拉框
                    DropdownWidget<?> open = currentOpen();
                    if (open != null && open != this) {
                        open.collapse();
                    }
                    expanded = true;
                    setCurrentlyOpen(this);
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
            int lw = listWidth();
            int visibleCount = Math.min(MAX_VISIBLE, options.size());
            int totalHeight = visibleCount * ITEM_HEIGHT + 4;
            return mouseX >= getX() && mouseX <= getX() + lw
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