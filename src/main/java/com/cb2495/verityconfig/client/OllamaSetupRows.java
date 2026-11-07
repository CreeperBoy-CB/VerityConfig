package com.cb2495.verityconfig.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link OllamaSetupScreenZh} 的列表与行。
 * <p>行高 26、滚动条在左侧、每行右侧是 74 宽的「下载 / 使用」按钮，
 * 有删除按钮时再加一个 44 宽的「删除」按钮——这些尺寸和原版完全一致。
 */
final class OllamaSetupRows {

    private OllamaSetupRows() {}

    /** 当前行高亮色，0x2300FF00。 */
    private static final int COLOR_CURRENT_ROW = 587202559;
    /** 信息行灰字。 */
    private static final int COLOR_INFO = -5592406;
    /** 说明文字暗灰。 */
    private static final int COLOR_DETAIL = -7362360;
    /** 注释灰。 */
    private static final int COLOR_NOTE = -6250336;
    /** 警告色。 */
    private static final int COLOR_WARN = -26022;
    /** 进度色。 */
    private static final int COLOR_PROGRESS = -8054;
    /** 使用中强调色。 */
    private static final int COLOR_ACCENT = -8913033;

    /** 已安装模型的一条记录。 */
    record InstalledModel(String name, long sizeBytes, String parameterSize, String quantization) {}

    /** 推荐的下载项。 */
    record Recommended(String tag, String params, long downloadBytes, boolean thinking, String blurb) {}

    /** Ollama 当前状态。 */
    record Status(boolean running, String version, boolean installed,
                  boolean local, boolean managed, String endpoint) {}

    /** 行基类。 */
    abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {}

    /** 分组标题行：黄色，左边缩进 2，y 偏移 12。 */
    static final class HeaderRow extends Row {

        private final Component text;

        HeaderRow(Component text) {
            this.text = text;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width,
                           int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(Minecraft.getInstance().font, text, left + 2, top + 12, 0xFFFFFF, false);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** 信息行：灰字，左边缩进 8，y 偏移 8。 */
    static final class InfoRow extends Row {

        private final String text;

        InfoRow(String text) {
            this.text = text;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width,
                           int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            graphics.drawString(font, ellipsize(font, text, width - 8),
                    left + 8, top + 8, COLOR_INFO, false);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** 模型行：名字 + 详情 + 说明，右侧一排按钮。 */
    static final class ModelRow extends Row {

        private final OllamaSetupScreenZh screen;
        private final String tag;
        private final String detail;
        private final String note;
        private final boolean warn;
        private final Button button;
        private final Button deleteButton;

        ModelRow(OllamaSetupScreenZh screen, String tag, String detail, String note,
                 boolean warn, long sizeBytes) {
            this.screen = screen;
            this.tag = tag;
            this.detail = detail;
            this.note = note;
            this.warn = warn;

            this.button = Button.builder(Component.literal("下载"), b -> press())
                    .bounds(0, 0, 74, 20)
                    .tooltip(Tooltip.create(Component.literal(
                            "从 Ollama 官方模型库下载到本机，大小见左侧说明。"
                                    + "\n\n文件源来自境外，可能需要加速器。")))
                    .build();

            if (sizeBytes < 0) {
                this.deleteButton = null;
            } else {
                String tip = sizeBytes > 0
                        ? "从此电脑删除 " + tag + " 并释放 " + OllamaSetupScreenZh.formatBytes(sizeBytes) + "。"
                        : "从此电脑删除 " + tag + "。";
                this.deleteButton = Button.builder(
                                Component.literal("删除").withStyle(ChatFormatting.RED),
                                b -> screen.confirmDelete(tag, sizeBytes))
                        .bounds(0, 0, 44, 20)
                        .tooltip(Tooltip.create(Component.literal(tip)))
                        .build();
            }
        }

        /** 这一行是不是正在下载的模型。 */
        private boolean downloadingThis() {
            if (!OllamaSetupScreenZh.isBusyStatic()) return false;
            String active = OllamaSetupScreenZh.activeModelStatic();
            return !active.isBlank()
                    && OllamaSetupScreenZh.withTagStatic(active)
                    .equalsIgnoreCase(OllamaSetupScreenZh.withTagStatic(tag));
        }

        /** 点「下载 / 使用」：已装就切换过去，没装就下载。 */
        private void press() {
            if (OllamaSetupScreenZh.isCurrentStatic(tag) || downloadingThis()) return;
            if (screen.isInstalledInList(tag)) {
                OllamaSetupScreenZh.selectModelStatic(tag);
            } else {
                OllamaSetupScreenZh.downloadModelStatic(tag);
            }
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width,
                           int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            boolean current = OllamaSetupScreenZh.isCurrentStatic(tag);
            boolean installed = screen.isInstalledInList(tag);
            boolean downloading = downloadingThis();
            Status status = screen.statusStatic();

            // 只有本地 Ollama 在线时才允许下载
            boolean canUse = status != null && (status.running() || !status.local());

            if (downloading) {
                Object snapshot = OllamaSetupScreenZh.snapshotStatic();
                float fraction = snapshot == null
                        ? -1.0F : OllamaSetupScreenZh.fractionOfStatic(snapshot);
                String title = snapshot == null
                        ? "" : String.valueOf(OllamaSetupScreenZh.field(snapshot, "title"));
                if (fraction >= 0 && title.startsWith("Downloading")) {
                    button.setMessage(Component.literal((int) (fraction * 100) + "%"));
                } else {
                    button.setMessage(Component.literal("处理中..."));
                }
                button.active = false;
            } else if (current) {
                button.setMessage(Component.literal("使用中").withStyle(ChatFormatting.GREEN));
                button.active = false;
            } else if (installed) {
                button.setMessage(Component.literal("使用"));
                button.active = true;
            } else {
                button.setMessage(Component.literal("下载"));
                button.active = canUse;
            }

            if (current) {
                graphics.fill(left - 2, top - 1, left + width + 2, top + height - 1, COLOR_CURRENT_ROW);
            }

            int reserved = button.getWidth()
                    + (deleteButton != null ? deleteButton.getWidth() + 4 : 0);
            int textWidth = width - reserved - 12;

            Font font = Minecraft.getInstance().font;
            String label = tag + (current ? "  (当前)" : "");
            String shown = ellipsize(font, label, textWidth);
            graphics.drawString(font, shown, left + 4, top + 2,
                    current ? COLOR_ACCENT : 0xFFFFFF, false);

            // 名字后面接参数和大小，放不下就不画
            int nameWidth = font.width(shown);
            if (nameWidth + 8 + font.width(detail) < textWidth) {
                graphics.drawString(font, detail, left + 4 + nameWidth + 8, top + 2, COLOR_DETAIL, false);
            }

            // 说明：正在下载时换成进度统计
            String shownNote = note;
            int noteColor = warn ? COLOR_WARN : COLOR_NOTE;
            if (downloading) {
                Object snapshot = OllamaSetupScreenZh.snapshotStatic();
                if (snapshot != null) {
                    shownNote = OllamaSetupScreenZh.statsStatic(snapshot);
                    noteColor = COLOR_PROGRESS;
                }
            }
            graphics.drawString(font, ellipsize(font, shownNote, textWidth),
                    left + 4, top + 13, noteColor, false);

            button.setX(left + width - button.getWidth() - 2);
            button.setY(top + 1);
            button.render(graphics, mouseX, mouseY, partialTick);

            if (deleteButton != null) {
                deleteButton.active = !downloading && !OllamaSetupScreenZh.isBusyStatic()
                        && status != null && status.running();
                deleteButton.setX(button.getX() - deleteButton.getWidth() - 4);
                deleteButton.setY(top + 1);
                deleteButton.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return deleteButton == null ? List.of(button) : List.of(deleteButton, button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            // Button 同时是 GuiEventListener 和 NarratableEntry，
            // 但 children() 返回的是 List<? extends GuiEventListener>，
            // 两个通配符之间不能直接转换，所以这里按元素重新构造一份
            List<NarratableEntry> entries = new ArrayList<>(2);
            if (deleteButton != null) entries.add(deleteButton);
            entries.add(button);
            return entries;
        }
    }

    /** 模型列表：宽度最多 360 减去滚动条，居中偏右。 */
    static final class ModelList extends ContainerObjectSelectionList<Row> {

        ModelList(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
            super(minecraft, width, height, top, bottom, itemHeight);
        }

        void clear() {
            clearEntries();
        }

        void add(Row row) {
            addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, width - 24);
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + getRowWidth() / 2 + 6;
        }
    }

    /** 按像素宽度截断并补省略号。 */
    static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }
}
