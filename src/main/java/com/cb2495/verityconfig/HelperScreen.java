package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.PlatformUtils;
import com.cb2495.verityconfig.util.ScrollableArea;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("removal")
public class HelperScreen extends Screen {
    private static final Pattern IMAGE_PATTERN = Pattern.compile("<([^>]+)>");
    private static final Pattern SECTION_PATTERN = Pattern.compile("^§(\\d+):\"([^\"]*)\"$");

    private enum Platform { WINDOWS, ANDROID }
    private Platform currentPlatform;

    private final String topic;

    // 章节信息
    private static class SectionInfo {
        final int index;
        final String title;
        final int lineIndex;
        int startY;   // 章节标题在内容中的 Y 偏移
        SectionInfo(int index, String title, int lineIndex) {
            this.index = index;
            this.title = title;
            this.lineIndex = lineIndex;
        }
    }

    // 行类型
    private static class HelpLine {
        enum Type { TEXT, IMAGE, SECTION }
        final Type type;
        final FormattedCharSequence sequence; // 文本或章节标题样式
        final String imageName;               // 图片文件名
        int height;
        int imageWidth;   // 图片显示宽度（GUI坐标）
        int imageHeight;  // 图片显示高度（GUI坐标）
        long imageFileSize;

        HelpLine(FormattedCharSequence seq) {
            this.type = Type.TEXT;
            this.sequence = seq;
            this.imageName = null;
            this.height = 12;
        }

        HelpLine(FormattedCharSequence seq, boolean isSection) {
            this.type = Type.SECTION;
            this.sequence = seq;
            this.imageName = null;
            this.height = 20;
        }

        HelpLine(String imageName, boolean isImage) {
            this.type = Type.IMAGE;
            this.sequence = null;
            this.imageName = imageName;
            this.height = 0;
        }
    }

    private List<HelpLine> currentLines = new ArrayList<>();
    private List<SectionInfo> sections = new ArrayList<>();
    private static final int LINE_HEIGHT = 12;
    private static final int SECTION_HEIGHT = 20;
    private static final int CONTENT_TOP = 30;
    private static final int CONTENT_BOTTOM_OFFSET = 10;

    private Button prevSectionButton;
    private Button nextSectionButton;

    // 图片缓存
    private final Map<String, ResourceLocation> imageTextureCache = new HashMap<>();
    private final Map<String, int[]> imageSizeCache = new HashMap<>();
    private final Map<String, Long> imageFileSizeCache = new HashMap<>();

    // 滚动区域辅助
    private ScrollableArea scrollableArea;

    // 标题映射
    private static final Map<String, String> TOPIC_TITLES = new HashMap<>();
    static {
        TOPIC_TITLES.put("low_memory", "内存不足");
        TOPIC_TITLES.put("shader_install", "光影安装");
        TOPIC_TITLES.put("mod_install", "模组安装");
    }



    public HelperScreen(String topic) {
        this(topic, null);
    }

    public HelperScreen(String topic, Screen returnScreen) {
        super(Component.literal("帮助"));
        this.topic = topic;
        this.currentPlatform = PlatformUtils.isWindows() ? Platform.WINDOWS : Platform.ANDROID;
        this.returnScreen = returnScreen;
    }

    public HelperScreen() {
        this("low_memory");
    }

    private final Screen returnScreen;

    @Override
    protected void init() {
        super.init();

        int buttonY = 5;
        int buttonHeight = 20;
        this.addRenderableWidget(Button.builder(Component.literal("Windows"), btn -> {
            currentPlatform = Platform.WINDOWS;
            loadText();
        }).pos(5, buttonY).size(80, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Android"), btn -> {
            currentPlatform = Platform.ANDROID;
            loadText();
        }).pos(90, buttonY).size(80, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("退出"), btn -> {
            this.onClose();
        }).pos(this.width - 60, buttonY).size(50, buttonHeight).build());

        // 初始化滚动区域
        this.scrollableArea = new ScrollableArea(CONTENT_TOP, this.height - CONTENT_BOTTOM_OFFSET);

        // 章节导航按钮
        this.prevSectionButton = Button.builder(Component.literal("上一章"), btn -> {
            gotoSection(getCurrentSectionIndex() - 1);
        }).pos(this.width - 110, this.height - 50).size(50, 20).build();
        this.prevSectionButton.visible = false;
        this.addRenderableWidget(this.prevSectionButton);

        this.nextSectionButton = Button.builder(Component.literal("下一章"), btn -> {
            gotoSection(getCurrentSectionIndex() + 1);
        }).pos(this.width - 55, this.height - 50).size(50, 20).build();
        this.nextSectionButton.visible = false;
        this.addRenderableWidget(this.nextSectionButton);

        loadText();
    }

    private void loadText() {
        currentLines.clear();
        sections.clear();
        imageTextureCache.clear();
        imageSizeCache.clear();
        imageFileSizeCache.clear();

        String fileName = currentPlatform == Platform.WINDOWS
                ? topic + "_pc.txt"
                : topic + "_mobile.txt";
        ResourceLocation res = new ResourceLocation(VerityConfig.MODID, "help/" + fileName);

        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(res).orElseThrow();
            try (InputStream is = resource.open()) {
                String content = IOUtils.toString(is, StandardCharsets.UTF_8);
                String[] rawLines = content.split("\\r?\\n");

                int maxTextWidth = this.width - 20 - 6;
                int lineIndexCounter = 0;
                for (String rawLine : rawLines) {
                    // 章节标题
                    Matcher sectionMatcher = SECTION_PATTERN.matcher(rawLine.trim());
                    if (sectionMatcher.matches()) {
                        int sectionNumber = Integer.parseInt(sectionMatcher.group(1));
                        String sectionTitle = sectionMatcher.group(2);
                        Component sectionComp = Component.literal(sectionNumber + ". " + sectionTitle)
                                .withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE);
                        FormattedCharSequence sectionSeq = sectionComp.getVisualOrderText();
                        currentLines.add(new HelpLine(sectionSeq, true));
                        sections.add(new SectionInfo(sectionNumber, sectionTitle, lineIndexCounter));
                        lineIndexCounter++;
                        continue;
                    }

                    // 图片标记
                    Matcher matcher = IMAGE_PATTERN.matcher(rawLine);
                    if (matcher.find() && matcher.start() == 0 && matcher.end() == rawLine.length()) {
                        String imageName = matcher.group(1);
                        imageName = imageName.toLowerCase().replaceAll("[^a-z0-9._-]", "");
                        if (!imageName.isEmpty()) {
                            currentLines.add(new HelpLine(imageName, true));
                            lineIndexCounter++;
                        } else {
                            currentLines.add(new HelpLine(Component.literal("图片标记无效").getVisualOrderText()));
                            lineIndexCounter++;
                        }
                    } else {
                        // 普通文本
                        Component parsed = parseFormatting(rawLine, Style.EMPTY.withColor(ChatFormatting.WHITE));
                        List<FormattedCharSequence> wrapped = this.font.split(parsed, maxTextWidth);
                        for (FormattedCharSequence seq : wrapped) {
                            currentLines.add(new HelpLine(seq));
                            lineIndexCounter++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            currentLines.add(new HelpLine(Component.literal("无法加载帮助内容: " + res).getVisualOrderText()));
            e.printStackTrace();
        }

        // 计算图片高度与章节起始位置
        recalcImageHeights();
        calculateSectionStartY();

        // 重置滚动
        scrollableArea.setScrollOffset(0);
        scrollableArea.clampScroll(getTotalContentHeight());
    }

    private Component parseFormatting(String text, Style baseStyle) {
        MutableComponent result = Component.literal("");
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("/warn/", i)) {
                int end = text.indexOf("/warn/", i + 6);
                if (end != -1) {
                    String content = text.substring(i + 6, end);
                    result.append(Component.literal(content).setStyle(baseStyle.withColor(ChatFormatting.RED)));
                    i = end + 6;
                    continue;
                }
            }
            if (text.startsWith("/hint/", i)) {
                int end = text.indexOf("/hint/", i + 6);
                if (end != -1) {
                    String content = text.substring(i + 6, end);
                    result.append(Component.literal(content).setStyle(baseStyle.withColor(ChatFormatting.YELLOW)));
                    i = end + 6;
                    continue;
                }
            }
            int nextWarn = text.indexOf("/warn/", i);
            int nextHint = text.indexOf("/hint/", i);
            int next = text.length();
            if (nextWarn != -1 && nextWarn < next) next = nextWarn;
            if (nextHint != -1 && nextHint < next) next = nextHint;
            result.append(Component.literal(text.substring(i, next)).setStyle(baseStyle));
            i = next;
        }
        return result;
    }

    private void recalcImageHeights() {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int availableGuiWidth = this.width - 4;

        for (HelpLine line : currentLines) {
            if (line.type == HelpLine.Type.IMAGE) {
                ResourceLocation tex = getOrLoadImageTexture(line.imageName);
                if (tex != null && imageSizeCache.containsKey(line.imageName)) {
                    int[] originalSize = imageSizeCache.get(line.imageName);
                    int originalWidth = originalSize[0];
                    int originalHeight = originalSize[1];

                    int guiWidth = (int) Math.ceil(originalWidth / guiScale);
                    int guiHeight = (int) Math.ceil(originalHeight / guiScale);

                    if (guiWidth > availableGuiWidth) {
                        double ratio = (double) availableGuiWidth / guiWidth;
                        guiWidth = availableGuiWidth;
                        guiHeight = (int) Math.ceil(guiHeight * ratio);
                    }

                    line.imageWidth = Math.max(1, guiWidth);
                    line.imageHeight = Math.max(1, guiHeight);
                    line.height = line.imageHeight + 2;
                    line.imageFileSize = imageFileSizeCache.getOrDefault(line.imageName, 0L);
                } else {
                    line.height = LINE_HEIGHT;
                }
            }
        }
    }

    private void calculateSectionStartY() {
        int accumulatedY = 0;
        int sectionIdx = 0;
        for (int i = 0; i < currentLines.size(); i++) {
            HelpLine line = currentLines.get(i);
            if (sectionIdx < sections.size() && sections.get(sectionIdx).lineIndex == i) {
                sections.get(sectionIdx).startY = accumulatedY;
                sectionIdx++;
            }
            accumulatedY += line.height;
        }
    }

    private int getTotalContentHeight() {
        int total = 0;
        for (HelpLine line : currentLines) {
            total += line.height;
        }
        return total;
    }

    private int getCurrentSectionIndex() {
        int currentOffset = scrollableArea.getScrollOffset();
        if (sections.isEmpty()) return 0;
        int current = 0;
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).startY <= currentOffset) {
                current = i;
            } else {
                break;
            }
        }
        return current;
    }

    private void gotoSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= sections.size()) return;
        SectionInfo target = sections.get(sectionIndex);
        scrollableArea.setScrollOffset(target.startY);
        scrollableArea.clampScroll(getTotalContentHeight());
    }

    private ResourceLocation getOrLoadImageTexture(String imageName) {
        if (imageTextureCache.containsKey(imageName)) {
            return imageTextureCache.get(imageName);
        }

        String baseName = currentPlatform == Platform.WINDOWS
                ? topic + "_pc"
                : topic + "_mobile";
        String path = "help/pngres/" + baseName + "/" + imageName;

        ResourceLocation imageRes;
        try {
            imageRes = new ResourceLocation(VerityConfig.MODID, path);
        } catch (Exception e) {
            System.err.println("[VerityConfig] 非法图片路径: " + path);
            imageTextureCache.put(imageName, null);
            return null;
        }

        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(imageRes).orElseThrow();
            try (InputStream is = resource.open()) {
                byte[] bytes = IOUtils.toByteArray(is);
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(bytes));
                int width = nativeImage.getWidth();
                int height = nativeImage.getHeight();
                DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                dynamicTexture.setBlurMipmap(false, false);
                ResourceLocation textureLocation = Minecraft.getInstance().getTextureManager()
                        .register("verityconfig_help_" + imageName, dynamicTexture);
                imageTextureCache.put(imageName, textureLocation);
                imageSizeCache.put(imageName, new int[]{width, height});
                imageFileSizeCache.put(imageName, (long) bytes.length);
                return textureLocation;
            }
        } catch (Exception e) {
            System.err.println("[VerityConfig] 无法加载帮助图片: " + imageRes);
            e.printStackTrace();
            imageTextureCache.put(imageName, null);
            return null;
        }
    }

    private void updateSectionButtons() {
        if (prevSectionButton != null && nextSectionButton != null) {
            int currentIdx = getCurrentSectionIndex();
            boolean hasPrev = currentIdx > 0;
            boolean hasNext = currentIdx < sections.size() - 1;
            prevSectionButton.visible = hasPrev;
            nextSectionButton.visible = hasNext;
            if (hasPrev) {
                prevSectionButton.setTooltip(Tooltip.create(Component.literal(sections.get(currentIdx - 1).title)));
            }
            if (hasNext) {
                nextSectionButton.setTooltip(Tooltip.create(Component.literal(sections.get(currentIdx + 1).title)));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        String title = "帮助 - " + TOPIC_TITLES.getOrDefault(topic, topic);
        graphics.drawCenteredString(this.font, title, this.width / 2, 5, 0xFFFFFF);
        graphics.fill(0, CONTENT_TOP - 1, this.width, CONTENT_TOP, 0xFFAAAAAA);

        int contentBottom = this.height - CONTENT_BOTTOM_OFFSET;
        int visibleHeight = contentBottom - CONTENT_TOP;

        int totalContentHeight = getTotalContentHeight();
        scrollableArea.clampScroll(totalContentHeight);
        int currentOffset = scrollableArea.getScrollOffset();

        int currentY = CONTENT_TOP - currentOffset;
        for (HelpLine line : currentLines) {
            if (currentY + line.height < CONTENT_TOP || currentY > contentBottom) {
                currentY += line.height;
                continue;
            }

            if (line.type == HelpLine.Type.TEXT) {
                graphics.drawString(this.font, line.sequence, 10, currentY, 0xFFFFFF, false);
                currentY += line.height;
            } else if (line.type == HelpLine.Type.SECTION) {
                graphics.fill(10, currentY - 2, this.width - 10, currentY - 1, 0xFFAAAAAA);
                graphics.drawString(this.font, line.sequence, 10, currentY + 2, 0xFFFFFF, false);
                currentY += line.height;
            } else if (line.type == HelpLine.Type.IMAGE) {
                ResourceLocation tex = getOrLoadImageTexture(line.imageName);
                if (tex != null && line.imageWidth > 0 && line.imageHeight > 0) {
                    int[] originalSize = imageSizeCache.get(line.imageName);
                    if (originalSize != null) {
                        float scaleX = (float) line.imageWidth / originalSize[0];
                        float scaleY = (float) line.imageHeight / originalSize[1];
                        graphics.pose().pushPose();
                        graphics.pose().translate(2, currentY, 0);
                        graphics.pose().scale(scaleX, scaleY, 1.0f);
                        graphics.blit(tex, 0, 0, 0, 0, originalSize[0], originalSize[1], originalSize[0], originalSize[1]);
                        graphics.pose().popPose();
                    } else {
                        graphics.drawString(this.font, Component.literal("图片加载失败").getVisualOrderText(), 10, currentY, 0xFF5555, false);
                    }
                } else {
                    graphics.drawString(this.font, Component.literal("图片加载失败").getVisualOrderText(), 10, currentY, 0xFF5555, false);
                }
                currentY += line.height;
            }
        }

        // 顶部遮罩
        graphics.fill(0, 0, this.width, CONTENT_TOP, 0x80000000);
        graphics.drawCenteredString(this.font, title, this.width / 2, 5, 0xFFFFFF);

        // 滚动条
        scrollableArea.renderScrollbar(graphics, this.width, totalContentHeight);

        // 更新章节按钮并绘制所有控件
        updateSectionButtons();
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scrollableArea.mouseScrolled(mouseX, mouseY, delta, getTotalContentHeight());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollableArea.isMouseOverScrollbar(mouseX, mouseY, this.width, getTotalContentHeight())) {
            // 开始拖动滚动条
            draggingScrollbar = true;
            scrollableArea.updateScrollFromMouse(mouseY, this.width, getTotalContentHeight());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollableArea.updateScrollFromMouse(mouseY, this.width, getTotalContentHeight());
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

    // 需要一个字段记录是否正在拖动滚动条
    private boolean draggingScrollbar = false;

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        if (returnScreen != null) {
            Minecraft.getInstance().setScreen(returnScreen);
        } else {
            Minecraft.getInstance().setScreen(null);
        }
    }
}