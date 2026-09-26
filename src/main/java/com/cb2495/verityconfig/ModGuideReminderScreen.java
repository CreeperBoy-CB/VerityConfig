package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.HelpReadTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 「启用了模组但没看过教程」的提醒界面。
 * <p>取代直接弹出的重启确认：用户刚启用触摸控制器或 Oculus 时，
 * 若还没读过对应教程，先引导去看，看完再问是否重启。
 * <p>每一条教程占一行，可分别点击；多个模组会分行列出。
 */
public class ModGuideReminderScreen extends Screen {

    /** 一条待引导的教程。 */
    public record GuideEntry(String bracketText, String label, String topic) {
    }

    /** 已经启用了这些模组、但对应教程尚未阅读。 */
    private final List<GuideEntry> pending;
    private final Screen returnScreen;

    /** 本页结束后的回调：交给调用方继续下一级检查（前置模组、重启确认）。 */
    private final Runnable onFinished;

    /** 每行「点击此处」的屏幕区域，render 时重建。 */
    private final List<int[]> linkRects = new ArrayList<>();

    private static final int LINE_GAP = 24;
    private static final int TITLE_Y = 30;

    public ModGuideReminderScreen(List<GuideEntry> pending, Screen returnScreen) {
        this(pending, returnScreen, null);
    }

    public ModGuideReminderScreen(List<GuideEntry> pending, Screen returnScreen, Runnable onFinished) {
        super(Component.literal("模组教程提醒"));
        this.pending = new ArrayList<>(pending);
        this.returnScreen = returnScreen;
        this.onFinished = onFinished;
    }

    /**
     * 过滤掉已经读过的教程。
     * <p>每次 {@link #init()} 都会调用：从帮助界面返回、或窗口缩放重新
     * init 时，已读的那一条不该继续占着一行。
     */
    private void refreshPending() {
        this.pending.removeIf(entry -> HelpReadTracker.hasRead(entry.topic()));
    }

    @Override
    protected void init() {
        super.init();
        refreshPending();
        int buttonY = TITLE_Y + 16 + pending.size() * LINE_GAP + 14;
        this.addRenderableWidget(Button.builder(Component.literal("我已了解，继续"), btn -> {
            // 用户明确表示不需要看：把本次提醒到的主题一次性记为已读，
            // 否则下次点完成还会被同一个提示拦一次
            List<String> topics = new ArrayList<>();
            for (GuideEntry entry : pending) {
                topics.add(entry.topic());
            }
            HelpReadTracker.markAllRead(topics);
            proceed();
        }).pos(this.width / 2 - 105, buttonY).size(100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("返回"), btn -> {
            if (returnScreen != null) {
                Minecraft.getInstance().setScreen(returnScreen);
            } else {
                Minecraft.getInstance().setScreen(null);
            }
        }).pos(this.width / 2 + 5, buttonY).size(100, 20).build());
    }

    /**
     * 本页结束，交回给调用方继续。
     * <p>没有回调时退化为直接弹重启确认，保持单独使用本界面的可能。
     */
    private void proceed() {
        if (onFinished != null) {
            onFinished.run();
            return;
        }
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        this.linkRects.clear();

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // 所有教程都在本界面内被读完时会走到这里：不再渲染引导行，
        // 只留「继续」按钮，避免出现「没有内容的空页」
        if (pending.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.literal("已经了解过所有相关教程了"),
                    this.width / 2, TITLE_Y + 16, 0xFFAAAAAA);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        int y = TITLE_Y + 16;
        boolean hoverAny = false;

        for (GuideEntry entry : pending) {
            String head = "你已经启用了 " + entry.bracketText() + " 模组，但并没有阅读过其帮助文档。";
            String body = "除非你已经了解如何使用这个模组，否则建议";
            String link = "[点击此处]";
            String tail = "阅读其帮助文档：" + entry.label();

            int bodyWidth = this.font.width(body);
            int linkWidth = this.font.width(link);

            // 整行居中；链接夹在正文之间，需要单独算出它的起点
            int totalWidth = bodyWidth + linkWidth + this.font.width(tail);
            // 窄屏放不下整行时，把「阅读其帮助文档：xx」挪到第三行，
            // 否则文字会顶出屏幕外被裁掉，反而看不到文档名
            boolean tailOnOwnLine = totalWidth > this.width - 8;
            int lineWidth = tailOnOwnLine ? bodyWidth + linkWidth : totalWidth;
            int lineX = (this.width - lineWidth) / 2;

            graphics.drawCenteredString(this.font, head, this.width / 2, y, 0xFFDDDDDD);

            int textY = y + 12;
            graphics.drawString(this.font, body, lineX, textY, 0xFFDDDDDD, false);

            int linkX = lineX + bodyWidth;
            boolean hovered = mouseX >= linkX && mouseX < linkX + linkWidth
                    && mouseY >= textY && mouseY < textY + this.font.lineHeight;
            hoverAny |= hovered;
            int linkColor = hovered ? 0xFFFFFF : 0xFF88CCFF;
            graphics.drawString(this.font, link, linkX, textY, linkColor, false);
            // 悬停时加下划线，明确这是可点击的
            if (hovered) {
                graphics.fill(linkX, textY + this.font.lineHeight - 1,
                        linkX + linkWidth, textY + this.font.lineHeight, linkColor);
            }
            this.linkRects.add(new int[]{linkX, textY, linkX + linkWidth, textY + this.font.lineHeight});

            if (tailOnOwnLine) {
                graphics.drawCenteredString(this.font, tail, this.width / 2, textY + 12, 0xFFDDDDDD);
            } else {
                graphics.drawString(this.font, tail, linkX + linkWidth, textY, 0xFFDDDDDD, false);
            }

            y += LINE_GAP;
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (hoverAny) {
            graphics.renderTooltip(this.font, Component.literal("打开对应的帮助文档"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < linkRects.size(); i++) {
                int[] rect = linkRects.get(i);
                if (mouseX >= rect[0] && mouseX < rect[2]
                        && mouseY >= rect[1] && mouseY < rect[3]) {
                    GuideEntry entry = pending.get(i);
                    // 看完教程后回到本界面：还有别的没读就继续显示，
                    // 都读完了就只剩「继续」这一步
                    Minecraft.getInstance().setScreen(
                            new HelperScreen(entry.topic(), this));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
