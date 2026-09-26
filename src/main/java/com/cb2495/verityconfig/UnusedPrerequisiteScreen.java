package com.cb2495.verityconfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 「前置模组没派上用场」的确认界面。
 * <p>在教程提醒之后、重启确认之前出现：某些前置模组虽然启用着，
 * 但它所服务的功能性模组都没启用，留着只是白占内存。
 * <p>用户可以一次性禁用这些前置，或选择忽略——忽略只对本次生效，
 * 下次点「完成」还会再问。
 */
public class UnusedPrerequisiteScreen extends Screen {

    /** 待处理的、没被任何已启用模组依赖的前置模组名。 */
    private final List<String> unused;
    private final Screen returnScreen;

    /** 处理完成后的回调：继续走原本的「点完成」流程。 */
    private final Runnable onFinished;

    private static final int LINE_GAP = 16;
    private static final int TITLE_Y = 30;
    private static final int TEXT_COLOR = 0xFFDDDDDD;
    private static final int NAME_COLOR = 0xFF88CCFF;

    public UnusedPrerequisiteScreen(List<String> unused, Screen returnScreen, Runnable onFinished) {
        super(Component.literal("前置模组检查"));
        this.unused = new ArrayList<>(unused);
        this.returnScreen = returnScreen;
        this.onFinished = onFinished;
    }

    @Override
    protected void init() {
        super.init();
        // 按钮位置按实际换行后的行数算，否则名字多到换行时按钮会压在文字上
        int buttonY = TITLE_Y + 18 + (wrapNames().size() + 1) * LINE_GAP + 10;

        this.addRenderableWidget(Button.builder(Component.literal("禁用"), btn -> {
            // 与模组列表里的开关一致：改名为 .disabled，随时可以再启用回来
            disableAll();
            finish();
        }).pos(this.width / 2 - 105, buttonY).size(100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("忽略"), btn -> {
            // 只跳过本次：不写任何记录，下次点「完成」仍会提示
            finish();
        }).pos(this.width / 2 + 5, buttonY).size(100, 20).build());
    }

    /**
     * 把模组名按可用宽度折行。
     * <p>{@link #init()} 与 {@link #render} 都调用它，保证按钮位置
     * 与文字行数始终一致——分别各算一遍迟早会算岔。
     */
    private List<List<String>> wrapNames() {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentWidth = 0;
        int maxWidth = this.width - 20;
        int separatorWidth = this.font.width("、");
        for (String name : unused) {
            int nameWidth = this.font.width(name);
            int addedWidth = current.isEmpty() ? nameWidth : separatorWidth + nameWidth;
            if (!current.isEmpty() && currentWidth + addedWidth > maxWidth) {
                rows.add(current);
                current = new ArrayList<>();
                currentWidth = 0;
                addedWidth = nameWidth;
            }
            current.add(name);
            currentWidth += addedWidth;
        }
        if (!current.isEmpty()) rows.add(current);
        return rows;
    }

    /** 禁用全部待处理的前置模组。 */
    private void disableAll() {
        if (returnScreen instanceof ModsListScreen listScreen) {
            // 由列表自己负责重新扫描目录，这里不越过它去操作文件
            listScreen.disableModsByName(unused);
        }
    }

    /** 处理完毕，交回给调用方继续原本的流程（弹重启确认）。 */
    private void finish() {
        if (onFinished != null) {
            onFinished.run();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        int y = TITLE_Y + 18;
        graphics.drawCenteredString(this.font,
                Component.literal("以下前置模组对应的依赖模组并没有被启用，是否禁用这些模组？"),
                this.width / 2, y, TEXT_COLOR);

        y += LINE_GAP + 4;
        // 第二行：逐个列出模组名，横向排不下就换行
        for (List<String> row : wrapNames()) {
            String joined = String.join("、", row);
            graphics.drawCenteredString(this.font, Component.literal(joined),
                    this.width / 2, y, NAME_COLOR);
            y += LINE_GAP;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
