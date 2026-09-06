package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.ScrollableArea;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QandAScreen extends Screen {

    private static final Pattern QUESTION_PATTERN = Pattern.compile("^Q:\\s*(.*)$");
    private static final Pattern ANSWER_PATTERN = Pattern.compile("^A:\\s*(.*)$");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^##\\s*(.*)$");

    private static final int LINE_HEIGHT = 12;
    private static final int CONTENT_TOP = 30;
    private static final int CONTENT_BOTTOM_OFFSET = 10;
    private static final int QUESTION_HEIGHT = 18;
    private static final int ANSWER_INDENT = 12;

    private static final String[] CATEGORIES = {"Verity", "其他模组", "其他"};

    private static class QAEntry {
        final String question;
        final List<String> answerLines = new ArrayList<>();
        boolean expanded = false;
        int totalHeight = QUESTION_HEIGHT;
        List<FormattedCharSequence> wrappedQuestion = new ArrayList<>();
        List<FormattedCharSequence> wrappedAnswers = new ArrayList<>();

        QAEntry(String question) {
            this.question = question;
        }
    }

    // 保存每个分类下的条目，保持插入顺序
    private final Map<String, List<QAEntry>> categorizedEntries = new LinkedHashMap<>();
    private List<QAEntry> currentEntries = new ArrayList<>(); // 当前分类的条目列表
    private String currentCategory = CATEGORIES[0];

    private ScrollableArea scrollableArea;
    private boolean draggingScrollbar = false;
    private final List<Button> categoryButtons = new ArrayList<>();

    public QandAScreen() {
        super(Component.literal("常见问题解答"));
    }

    @Override
    protected void init() {
        super.init();
        this.scrollableArea = new ScrollableArea(CONTENT_TOP, this.height - CONTENT_BOTTOM_OFFSET);

        loadContent();
        updateCurrentEntries();
        recalculateHeights();

        // 退出按钮
        this.addRenderableWidget(Button.builder(Component.literal("退出"), btn -> {
            Minecraft.getInstance().setScreen(null);
        }).pos(this.width - 60, 5).size(50, 20).build());

        // 分类选项卡按钮
        categoryButtons.clear();
        int tabX = 5;
        int tabY = 5;
        for (String category : CATEGORIES) {
            final String cat = category;
            int tabWidth = this.font.width(cat) + 12;
            Button btn = Button.builder(Component.literal(cat), b -> {
                currentCategory = cat;
                updateCurrentEntries();
                recalculateHeights();
                scrollableArea.setScrollOffset(0);
                updateCategoryButtonStyles();
            }).pos(tabX, tabY).size(tabWidth, 20).build();
            this.addRenderableWidget(btn);
            categoryButtons.add(btn);
            tabX += tabWidth + 2;
        }

        updateCategoryButtonStyles();
    }

    private void updateCategoryButtonStyles() {
        for (int i = 0; i < categoryButtons.size(); i++) {
            Button btn = categoryButtons.get(i);
            boolean selected = CATEGORIES[i].equals(currentCategory);
            btn.setMessage(Component.literal(CATEGORIES[i]).withStyle(selected ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
    }

    private void updateCurrentEntries() {
        currentEntries = categorizedEntries.getOrDefault(currentCategory, new ArrayList<>());
    }

    private void loadContent() {
        categorizedEntries.clear();
        // 初始化三个分类，确保顺序
        for (String cat : CATEGORIES) {
            categorizedEntries.put(cat, new ArrayList<>());
        }

        ResourceLocation res = new ResourceLocation(VerityConfig.MODID, "help/qanda.txt");
        String currentCat = CATEGORIES[0]; // 默认分类为第一个

        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(res).orElseThrow();
            try (InputStream is = resource.open()) {
                String content = IOUtils.toString(is, StandardCharsets.UTF_8);
                String[] lines = content.split("\\r?\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    Matcher catMatcher = CATEGORY_PATTERN.matcher(trimmed);
                    if (catMatcher.matches()) {
                        String category = catMatcher.group(1).trim();
                        // 检查是否在预设分类中，若不在则归为“其他”
                        if (Arrays.asList(CATEGORIES).contains(category)) {
                            currentCat = category;
                        } else {
                            currentCat = "其他";
                        }
                        continue;
                    }

                    Matcher qm = QUESTION_PATTERN.matcher(trimmed);
                    if (qm.matches()) {
                        QAEntry entry = new QAEntry(qm.group(1).trim());
                        categorizedEntries.get(currentCat).add(entry);
                        continue;
                    }

                    Matcher am = ANSWER_PATTERN.matcher(trimmed);
                    if (am.matches()) {
                        List<QAEntry> list = categorizedEntries.get(currentCat);
                        if (!list.isEmpty()) {
                            QAEntry last = list.get(list.size() - 1);
                            last.answerLines.add(am.group(1).trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void recalculateHeights() {
        int questionWidth = this.width - 20 - 6;
        int answerWidth = this.width - 20 - 6 - ANSWER_INDENT;

        for (QAEntry entry : currentEntries) {
            entry.wrappedQuestion.clear();
            entry.wrappedQuestion.addAll(this.font.split(Component.literal(entry.question), questionWidth));
            int questionHeight = entry.wrappedQuestion.size() * LINE_HEIGHT;

            entry.wrappedAnswers.clear();
            int answersHeight = 0;
            for (String ans : entry.answerLines) {
                List<FormattedCharSequence> wrapped = this.font.split(parseFormatting(ans), answerWidth);
                entry.wrappedAnswers.addAll(wrapped);
                answersHeight += wrapped.size() * LINE_HEIGHT;
            }

            if (entry.expanded) {
                entry.totalHeight = questionHeight + answersHeight + 4;
            } else {
                entry.totalHeight = questionHeight + 2;
            }
        }
    }

    private Component parseFormatting(String text) {
        MutableComponent result = Component.literal("");
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("/warn/", i)) {
                int end = text.indexOf("/warn/", i + 6);
                if (end != -1) {
                    result.append(Component.literal(text.substring(i + 6, end)).withStyle(ChatFormatting.RED));
                    i = end + 6;
                    continue;
                }
            }
            if (text.startsWith("/hint/", i)) {
                int end = text.indexOf("/hint/", i + 6);
                if (end != -1) {
                    result.append(Component.literal(text.substring(i + 6, end)).withStyle(ChatFormatting.YELLOW));
                    i = end + 6;
                    continue;
                }
            }
            int nextWarn = text.indexOf("/warn/", i);
            int nextHint = text.indexOf("/hint/", i);
            int next = text.length();
            if (nextWarn != -1 && nextWarn < next) next = nextWarn;
            if (nextHint != -1 && nextHint < next) next = nextHint;
            result.append(Component.literal(text.substring(i, next)));
            i = next;
        }
        return result;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 5, 0xFFFFFF);
        graphics.fill(0, CONTENT_TOP - 1, this.width, CONTENT_TOP, 0xFFAAAAAA);

        int totalHeight = 0;
        for (QAEntry entry : currentEntries) {
            totalHeight += entry.totalHeight;
        }
        scrollableArea.clampScroll(totalHeight);
        int scrollOffset = scrollableArea.getScrollOffset();

        int currentY = CONTENT_TOP - scrollOffset;
        for (QAEntry entry : currentEntries) {
            if (currentY + entry.totalHeight < CONTENT_TOP || currentY > this.height - CONTENT_BOTTOM_OFFSET) {
                currentY += entry.totalHeight;
                continue;
            }

            int questionY = currentY;
            // 绘制问题（可能多行）
            for (int i = 0; i < entry.wrappedQuestion.size(); i++) {
                graphics.drawString(this.font, entry.wrappedQuestion.get(i), 10, questionY + i * LINE_HEIGHT, 0xFFFFFF, false);
            }
            // 展开指示符
            String indicator = entry.expanded ? "▼" : "▶";
            graphics.drawString(this.font, indicator, this.width - 20, questionY, 0xFFFFFF, false);

            if (entry.expanded) {
                int answerStartY = questionY + entry.wrappedQuestion.size() * LINE_HEIGHT + 2;
                int lineIndex = 0;
                for (FormattedCharSequence seq : entry.wrappedAnswers) {
                    graphics.drawString(this.font, seq, 10 + ANSWER_INDENT, answerStartY + lineIndex * LINE_HEIGHT, 0xFFAAAAAA, false);
                    lineIndex++;
                }
            }

            if (mouseX >= 10 && mouseX <= this.width - 20 &&
                    mouseY >= questionY && mouseY <= questionY + entry.wrappedQuestion.size() * LINE_HEIGHT) {
                graphics.fill(10, questionY, this.width - 20, questionY + entry.wrappedQuestion.size() * LINE_HEIGHT, 0x20FFFFFF);
            }

            currentY += entry.totalHeight;
        }

        scrollableArea.renderScrollbar(graphics, this.width, totalHeight);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollableArea.isMouseOverScrollbar(mouseX, mouseY, this.width, getTotalHeight())) {
            draggingScrollbar = true;
            scrollableArea.updateScrollFromMouse(mouseY, this.width, getTotalHeight());
            return true;
        }

        // 检测点击问题行
        int currentY = CONTENT_TOP - scrollableArea.getScrollOffset();
        for (QAEntry entry : currentEntries) {
            int questionHeight = entry.wrappedQuestion.size() * LINE_HEIGHT;
            if (mouseX >= 10 && mouseX <= this.width - 20 &&
                    mouseY >= currentY && mouseY <= currentY + questionHeight) {
                entry.expanded = !entry.expanded;
                recalculateHeights();
                return true;
            }
            currentY += entry.totalHeight;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getTotalHeight() {
        int total = 0;
        for (QAEntry entry : currentEntries) {
            total += entry.totalHeight;
        }
        return total;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scrollableArea.mouseScrolled(mouseX, mouseY, delta, getTotalHeight());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollableArea.updateScrollFromMouse(mouseY, this.width, getTotalHeight());
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

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}