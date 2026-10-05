package com.cb2495.verityconfig.screen;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.help.HelpReadTracker;
import com.cb2495.verityconfig.help.HelpText;
import com.cb2495.verityconfig.util.ConfigIO;
import com.cb2495.verityconfig.util.ImageHeader;
import com.cb2495.verityconfig.util.Log;
import com.cb2495.verityconfig.util.PlatformUtils;
import com.cb2495.verityconfig.widget.ScrollableArea;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.io.IOUtils;

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

    /** 文本起始 x 坐标，渲染与点击检测共用，避免两处写死后不一致。 */
    private static final int TEXT_LEFT = 10;

    /**
     * 当前帧中可点击链接的屏幕区域。
     * <p>每帧渲染时重建：文本会随滚动和窗口缩放移动，缓存旧坐标会导致点错位置。
     */
    private final List<LinkHitbox> linkHitboxes = new ArrayList<>();

    /** 一个链接的可点击区域（单个字符宽度，多字符链接会有多段，换行时按行拆开）。 */
    private record LinkHitbox(int x1, int y1, int x2, int y2, String url) {
        boolean contains(double mx, double my) {
            return mx >= x1 && mx < x2 && my >= y1 && my < y2;
        }
    }

    private Button prevSectionButton;
    private Button nextSectionButton;

    // 图片缓存
    private final Map<String, ResourceLocation> imageTextureCache = new HashMap<>();
    private final Map<String, int[]> imageSizeCache = new HashMap<>();

    /** 当前图片缓存对应的主题；null 表示缓存为空。 */
    private String cachedImageTopic = null;

    /** 正在后台解码的图片名，避免同一张图被重复提交。 */
    private final Set<String> imagesLoading = new HashSet<>();

    /**
     * 加载代次：切换主题或关闭界面时自增。
     * <p>后台解码完成时若代次已经变了，说明界面已经不看这批图了，必须把结果
     * 释放掉而不能注册——否则又会在 TextureManager 里留下一个永远不会被关闭的纹理。
     */
    private int imageGeneration = 0;

    /** 尺寸读不出来（资源缺失或格式不认识）时的占位高度。 */
    private static final int PLACEHOLDER_IMAGE_HEIGHT = 120;

    /** 图片尚未解码完成时的占位文字。 */
    private static final String IMAGE_LOADING_TEXT = "图片加载中……";
    /** 图片确实取不到时显示的文字。 */
    private static final String IMAGE_FAILED_TEXT = "图片加载失败";

    // 滚动区域辅助
    private ScrollableArea scrollableArea;

    // 标题映射（英文 topic -> 中文名），用 LinkedHashMap 保证候选项顺序与注册顺序一致
    private static final Map<String, String> TOPIC_TITLES = new LinkedHashMap<>();
    static {
        TOPIC_TITLES.put("low_memory", "内存不足");
        TOPIC_TITLES.put("shader_install", "光影安装");
        TOPIC_TITLES.put("mod_install", "模组安装");
        TOPIC_TITLES.put("tc_use", "TouchController使用教程");
    }

    /** 全部已注册的中文名，供命令候选项使用。 */
    public static java.util.Collection<String> topicTitles() {
        return TOPIC_TITLES.values();
    }

    /**
     * 中文名反查英文 topic；传入英文 topic 时原样返回。
     * <p>顺便剥掉可能存在的成对引号与首尾空白，让带引号的写法也能用。
     */
    public static String resolveTopic(String input) {
        if (input == null) return null;
        String key = input.trim();
        if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) {
            key = key.substring(1, key.length() - 1).trim();
        }
        for (Map.Entry<String, String> entry : TOPIC_TITLES.entrySet()) {
            if (entry.getValue().equals(key)) return entry.getKey();
        }
        return key;
    }



    public HelperScreen(String topic) {
        this(topic, null);
    }

    public HelperScreen(String topic, Screen returnScreen) {
        super(Component.literal("帮助"));
        this.topic = topic;
        this.currentPlatform = PlatformUtils.isWindows() ? Platform.WINDOWS : Platform.ANDROID;
        this.returnScreen = returnScreen;
        // 打开即视为已读：用于「启用了模组却没看过教程」的提醒判定。
        // 放在构造函数里，这样从模组列表、命令、欢迎界面进来的都能记录到。
        HelpReadTracker.markRead(topic);
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

        // 图片纹理只在主题变化时才重建：同一主题下的重复加载（窗口缩放、
        // 切换 Windows/Android 按钮）复用已有纹理，不必重新读文件头、也不必
        // 重新解码已经到位的图片。
        if (!topic.equals(cachedImageTopic)) {
            releaseImageTextures();
            cachedImageTopic = topic;
        }

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
                        Component parsed = HelpText.parse(rawLine, Style.EMPTY.withColor(ChatFormatting.WHITE));
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
            Log.error("无法加载帮助内容: " + res, e);
        }

        // 按图片文件头算好版面（不做解码），图片本身等滚动到可见区域时再后台加载
        recalcImageHeights();
        calculateSectionStartY();

        // 重置滚动
        scrollableArea.setScrollOffset(0);
        scrollableArea.clampScroll(getTotalContentHeight());
    }

    // 行内标记的解析（/warn/、/hint/、/url/）已提取到 HelpText，与常见问题界面共用；
    // 那份实现原来只在这里，Q&A 的副本缺了 /url/，写链接会被当普通文字显示出来。

    // ========== 图片：尺寸、后台解码与占位 ==========

    /**
     * 计算每张图片的显示尺寸与占位高度。
     * <p>这里只读文件头拿原始宽高，<b>不做完整解码</b>：解码一张 2400×1080 的
     * 截图要分配好几 MB 内存，原本在 {@code init()} 里同步做完，既卡住渲染线程，
     * 又让窗口每缩放一次就重新来一遍。尺寸先按文件头算好，版面就不会在图片
     * 陆续到位时来回跳。
     */
    private void recalcImageHeights() {
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int availableGuiWidth = this.width - 4;

        for (HelpLine line : currentLines) {
            if (line.type != HelpLine.Type.IMAGE) continue;

            int[] originalSize = imageSizeCache.get(line.imageName);
            if (originalSize == null) {
                originalSize = readImageSize(line.imageName);
                if (originalSize != null) {
                    imageSizeCache.put(line.imageName, originalSize);
                }
            }

            if (originalSize == null) {
                // 尺寸取不到（资源缺失或格式不认识）：先给一个占位高度，
                // 万一后台能解码出来，finishImageLoad 会按真实尺寸重排
                line.imageWidth = 0;
                line.imageHeight = 0;
                line.height = PLACEHOLDER_IMAGE_HEIGHT + 2;
                continue;
            }

            int guiWidth = (int) Math.ceil(originalSize[0] / guiScale);
            int guiHeight = (int) Math.ceil(originalSize[1] / guiScale);

            if (guiWidth > availableGuiWidth) {
                double ratio = (double) availableGuiWidth / guiWidth;
                guiWidth = availableGuiWidth;
                guiHeight = (int) Math.ceil(guiHeight * ratio);
            }

            line.imageWidth = Math.max(1, guiWidth);
            line.imageHeight = Math.max(1, guiHeight);
            line.height = line.imageHeight + 2;
        }
    }

    /** 图片在资源包里的位置；名字非法时返回 {@code null}。 */
    private ResourceLocation imageResource(String imageName) {
        // 图片不再按平台分目录：同一主题下 PC 与手机共用 pngres/<topic>/，
        // 文件名本身已能区分（PC 多为 pcl_*.png，手机多为 fcl_*.jpg）
        try {
            return new ResourceLocation(VerityConfig.MODID, "help/pngres/" + topic + "/" + imageName);
        } catch (Exception e) {
            Log.warn("非法图片路径: " + topic + "/" + imageName);
            return null;
        }
    }

    /** 注册动态纹理用的名字；带主题前缀，避免不同主题的同名图片互相顶掉。 */
    private ResourceLocation textureLocation(String imageName) {
        try {
            return new ResourceLocation(VerityConfig.MODID, "helpimage/" + topic + "/" + imageName);
        } catch (Exception e) {
            return null;
        }
    }

    /** 只读文件头取原始宽高；资源打不开或格式不认识时返回 {@code null}。 */
    private int[] readImageSize(String imageName) {
        ResourceLocation res = imageResource(imageName);
        if (res == null) return null;
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(res).orElse(null);
            if (resource == null) return null;
            try (InputStream is = resource.open()) {
                return ImageHeader.readSize(is);
            }
        } catch (Exception e) {
            Log.warn("无法读取帮助图片尺寸: " + res);
            return null;
        }
    }

    /**
     * 安排一次后台解码。
     * <p>已在缓存里（含「读失败」的记录）或正在解码的图片直接跳过。
     * <p>只在图片即将被绘制时调用，所以打开帮助页不会一次性把整页大图
     * 都解码进内存。
     */
    private void ensureImageLoaded(String imageName) {
        if (imageTextureCache.containsKey(imageName)) return;
        if (!imagesLoading.add(imageName)) return;

        ResourceLocation res = imageResource(imageName);
        Resource resource = null;
        if (res != null) {
            try {
                resource = Minecraft.getInstance().getResourceManager().getResource(res).orElse(null);
            } catch (Exception ignored) {
                // 下面按「资源不存在」处理
            }
        }
        if (resource == null) {
            // 记住失败，避免每帧都去查一次
            imagesLoading.remove(imageName);
            imageTextureCache.put(imageName, null);
            return;
        }

        final int generation = imageGeneration;
        final Resource source = resource;
        final String name = imageName;
        ConfigIO.run(() -> {
            NativeImage decoded = null;
            try (InputStream is = source.open()) {
                // 直接解码，不再先整个读成 byte[]：原本要那份字节数组只是为了
                // 记录文件大小，而那个大小并没有任何地方会用到
                decoded = NativeImage.read(is);
            } catch (Exception ignored) {
                // 解码失败按「没有这张图」处理，界面会显示占位文字
            }
            final NativeImage image = decoded;
            ConfigIO.backToClient(() -> finishImageLoad(name, image, generation));
        });
    }

    /**
     * 后台解码完成，回到渲染线程注册纹理。
     * <p>代次不匹配说明界面已关闭或已换主题：把解码结果直接释放掉，绝不能注册，
     * 否则又会在 TextureManager 里留下一个无人释放的纹理。
     * <p>回调由 {@link ConfigIO#backToClient} 投递到主线程队列，在主线程 tick 时
     * 统一执行，因此不会打断正在进行的渲染循环。
     */
    private void finishImageLoad(String imageName, NativeImage image, int generation) {
        if (generation != imageGeneration) {
            // 界面已关闭或已换主题：结果作废，只释放内存。
            // 这里不碰 imagesLoading：它可能已经被新一轮加载重新占上了同一个名字，
            // 清掉会让那张图被重复提交一遍
            closeQuietly(image);
            return;
        }

        imagesLoading.remove(imageName);
        if (image == null) {
            imageTextureCache.put(imageName, null);
            return;
        }

        ResourceLocation location = textureLocation(imageName);
        if (location == null) {
            closeQuietly(image);
            imageTextureCache.put(imageName, null);
            return;
        }

        DynamicTexture texture = null;
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            texture = new DynamicTexture(image);   // 之后 image 归 texture 所有
            texture.setBlurMipmap(false, false);
            Minecraft.getInstance().getTextureManager().register(location, texture);
            imageTextureCache.put(imageName, location);

            // 之前只能按占位高度排版时，拿到真实尺寸后需要重排一次
            boolean sizeWasKnown = imageSizeCache.containsKey(imageName);
            imageSizeCache.put(imageName, new int[]{width, height});
            if (!sizeWasKnown) {
                recalcImageHeights();
                calculateSectionStartY();
            }
        } catch (Exception e) {
            Log.error("无法注册帮助图片纹理: " + imageName);
            if (texture != null) {
                // 纹理已经接管了 image，由它负责释放，不能再直接关 image
                try {
                    texture.close();
                } catch (Exception ignored) {
                    // 释放失败不影响流程
                }
            } else {
                closeQuietly(image);
            }
            imageTextureCache.put(imageName, null);
        }
    }

    private static void closeQuietly(NativeImage image) {
        if (image == null) return;
        try {
            image.close();
        } catch (Exception ignored) {
            // 释放失败无需打扰玩家
        }
    }

    /**
     * 释放已注册的帮助图片纹理并清空缓存。
     * <p>{@code TextureManager.register(String, DynamicTexture)} 每次调用都会生成一个
     * 新的 {@code dynamic/xxx_N} 名字，<b>不会</b>覆盖上一次注册的同名纹理，
     * 旧纹理既不会被替换、也不会被自动关闭。整页图片解码后是几十 MB 的堆外内存
     * 加等量显存，不显式 release 的话每次切换主题或缩放窗口都会泄漏一份。
     */
    private void releaseImageTextures() {
        // 让还在后台解码的图片作废：结果回来时会被丢弃并释放
        imageGeneration++;
        imagesLoading.clear();

        TextureManager manager = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation location : imageTextureCache.values()) {
            if (location == null) continue;
            try {
                manager.release(location);
            } catch (Exception ignored) {
                // 释放失败不影响界面关闭
            }
        }
        imageTextureCache.clear();
        imageSizeCache.clear();
        cachedImageTopic = null;
    }

    @Override
    public void removed() {
        releaseImageTextures();
        super.removed();
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
        // 标题不在这里画：下面盖完遮罩之后会统一画一次。
        // 半透明遮罩盖不住文字，先画一次会让标题看起来偏暗、像重影。
        graphics.fill(0, CONTENT_TOP - 1, this.width, CONTENT_TOP, 0xFFAAAAAA);

        int contentBottom = this.height - CONTENT_BOTTOM_OFFSET;

        int totalContentHeight = getTotalContentHeight();
        scrollableArea.clampScroll(totalContentHeight);
        int currentOffset = scrollableArea.getScrollOffset();

        int currentY = CONTENT_TOP - currentOffset;
        linkHitboxes.clear();
        for (HelpLine line : currentLines) {
            if (currentY + line.height < CONTENT_TOP || currentY > contentBottom) {
                currentY += line.height;
                continue;
            }

            if (line.type == HelpLine.Type.TEXT) {
                graphics.drawString(this.font, line.sequence, TEXT_LEFT, currentY, 0xFFFFFF, false);
                collectLinkHitboxes(line.sequence, currentY);
                currentY += line.height;
            } else if (line.type == HelpLine.Type.SECTION) {
                graphics.fill(10, currentY - 2, this.width - 10, currentY - 1, 0xFFAAAAAA);
                graphics.drawString(this.font, line.sequence, TEXT_LEFT, currentY + 2, 0xFFFFFF, false);
                currentY += line.height;
            } else if (line.type == HelpLine.Type.IMAGE) {
                renderImage(graphics, line, currentY, contentBottom);
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

    /**
     * 绘制一张图片；尚未解码完成时在同样的位置显示「图片加载中……」。
     * <p>版面是按文件头尺寸提前算好的，占位框与最终图片大小一致，
     * 图片到位后不会引起版面跳动。
     * <p>只有真正画到可见区域的图片才会安排解码，因此打开帮助页不会一次性
     * 把整页大图读进内存。
     */
    private void renderImage(GuiGraphics graphics, HelpLine line, int y, int contentBottom) {
        ResourceLocation texture = imageTextureCache.get(line.imageName);
        int[] originalSize = imageSizeCache.get(line.imageName);
        boolean laidOut = line.imageWidth > 0 && line.imageHeight > 0;

        if (texture != null && laidOut && originalSize != null) {
            float scaleX = (float) line.imageWidth / originalSize[0];
            float scaleY = (float) line.imageHeight / originalSize[1];
            graphics.pose().pushPose();
            graphics.pose().translate(2, y, 0);
            graphics.pose().scale(scaleX, scaleY, 1.0f);
            graphics.blit(texture, 0, 0, 0, 0, originalSize[0], originalSize[1],
                    originalSize[0], originalSize[1]);
            graphics.pose().popPose();
            return;
        }

        // 缓存里存了 null 表示这张图确实取不到：不要再显示「加载中」，
        // 也不要每帧重试
        boolean failed = imageTextureCache.containsKey(line.imageName);
        if (!failed) {
            ensureImageLoaded(line.imageName);
        }
        boolean loading = !failed
                && (imagesLoading.contains(line.imageName) || laidOut);

        renderImagePlaceholder(graphics, line, y, contentBottom, loading);
    }

    /** 图片位置的占位框：深色底 + 居中文字。 */
    private void renderImagePlaceholder(GuiGraphics graphics, HelpLine line, int y,
                                        int contentBottom, boolean loading) {
        boolean laidOut = line.imageWidth > 0 && line.imageHeight > 0;
        int width = laidOut ? line.imageWidth : Math.max(1, this.width - 4);
        int height = laidOut
                ? line.imageHeight
                : Math.max(1, Math.min(line.height - 2, contentBottom - y));

        graphics.fill(2, y, 2 + width, y + height, 0x40000000);
        graphics.renderOutline(2, y, width, height, 0x40AAAAAA);
        graphics.drawCenteredString(this.font, loading ? IMAGE_LOADING_TEXT : IMAGE_FAILED_TEXT,
                2 + width / 2, y + Math.max(0, (height - 8) / 2), 0xFFAAAAAA);
    }

    /**
     * 扫描一行文本，记录其中可点击链接的屏幕区域。
     * <p>逐字符取 {@link Style}，把 URL 相同的连续字符归并为一段。
     * 链接被自动换行拆到多行时，会按行分别记录（每行调用一次本方法）。
     */
    private void collectLinkHitboxes(FormattedCharSequence sequence, int lineY) {
        if (sequence == null) return;

        final int[] cursorX = {TEXT_LEFT};
        final String[] runUrl = {null};
        final int[] runStartX = {TEXT_LEFT};

        sequence.accept((index, style, codePoint) -> {
            String url = clickUrlOf(style);
            int charWidth = this.font.width(new String(Character.toChars(codePoint)));

            if (url == null || !url.equals(runUrl[0])) {
                // 链接段结束：记录上一段，然后开始新的一段
                flushLink(runUrl[0], runStartX[0], cursorX[0], lineY);
                runUrl[0] = url;
                runStartX[0] = cursorX[0];
            }
            cursorX[0] += charWidth;
            return true;
        });

        flushLink(runUrl[0], runStartX[0], cursorX[0], lineY);
    }

    /** 把一段链接区间记录为可点击区域。 */
    private void flushLink(String url, int startX, int endX, int lineY) {
        if (url == null || endX <= startX) return;
        linkHitboxes.add(new LinkHitbox(startX, lineY, endX, lineY + this.font.lineHeight, url));
    }

    /** 取出样式上挂载的 URL；没有点击事件或不是 OPEN_URL 时返回 {@code null}。 */
    private static String clickUrlOf(Style style) {
        if (style == null) return null;
        ClickEvent event = style.getClickEvent();
        if (event == null || event.getAction() != ClickEvent.Action.OPEN_URL) return null;
        return event.getValue();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 是否在视口内由 ScrollableArea 判断：在顶部标题栏上滚滚轮不应带动正文
        return scrollableArea.mouseScrolled(mouseY, delta, getTotalContentHeight())
                || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滚动条的按下/拖动/松开状态由 ScrollableArea 自己维护
        if (button == 0
                && scrollableArea.beginScrollbarDrag(mouseX, mouseY, this.width, getTotalContentHeight())) {
            return true;
        }
        // 帮助文档中的超链接（区域由上一帧 render 收集）
        if (button == 0) {
            for (LinkHitbox hitbox : linkHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    openUrl(hitbox.url());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 弹确认框后交给系统浏览器打开，与配置界面「获取 Key」的处理方式一致。 */
    private void openUrl(String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                net.minecraft.Util.getPlatform().openUri(url);
            }
            mc.setScreen(this);
        }, url, false));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollableArea.dragScrollbar(mouseY, this.width, getTotalContentHeight())) {
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
