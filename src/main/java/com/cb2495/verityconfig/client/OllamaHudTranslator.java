package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.util.Log;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 中文版的 Ollama 下载进度面板。
 * <p><b>为什么要自己画：</b>Verity 的面板文字全部来自
 * {@code OllamaProgressHud} 里的几个 {@code static} 方法，方法体就是一句句
 * 写死的 {@code return "..."}，没有任何字段可以替换，反射也改不了方法的返回值。
 * <p>所以反过来做——把 Verity 那份面板关掉（改它唯一的 {@code visible} 字段），
 * 由我们自己注册一个 {@code IGuiOverlay} 用中文绘制同样的信息。
 * <p>读数全部来自 Verity 自己的 {@code OllamaSetupManager.snapshot()}，
 * 本模组不重复实现下载逻辑，只负责显示。
 * <p>Verity 没装时整层不注册。
 */
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class OllamaHudTranslator {

    private OllamaHudTranslator() {}

    /** Verity 的进度面板类。 */
    private static final String HUD_CLASS = "varmite.verity.client.gui.OllamaProgressHud";
    /** Verity 的下载任务管理器类。 */
    private static final String MANAGER_CLASS = "varmite.verity.ollama.OllamaSetupManager";

    /** 面板是否已经被我们接管：只有接管成功后，屏幕上的绘制才改成中文层。 */
    private static boolean takenOver;

    /**
     * 中文面板自己的开关，默认打开。
     * <p><b>为什么不复用 Verity 的 {@code visible}：</b>那个字段的语义是「Verity 原面板
     * 要不要画」，被我们强行压成 false 了；如果拿它当我们的开关，两边会互相覆盖。
     * 现在各管各的：{@code visible} 只用来压制英文面板，这个字段决定画不画中文面板。
     */
    private static boolean hudEnabled = true;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                if (VeritySpec.spec() == null) return;
                Class.forName(HUD_CLASS);
                takenOver = true;
                Log.info("已接管 Ollama 下载进度面板，将显示中文版");
            } catch (ClassNotFoundException | LinkageError e) {
                // 没有 Verity 的进度面板，本模组这一层也就不需要了
                takenOver = false;
            }
        });
    }

    /**
     * 注册中文 HUD。
     * <p>注册在主 HUD 之上，覆盖在 Verity 面板原位置上；是否真的被 Verity 接管
     * 由 {@link #takenOver} 决定，没接管时不画，避免与 Verity 的面板叠在一起。
     */
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("verityconfig_ollama_progress", (gui, graphics, partialTick, width, height) -> {
            try {
                renderInGame(graphics, width);
            } catch (RuntimeException e) {
                // 每帧都跑的路径，出错只记一次日志就够，不要刷屏
                logOnce("绘制中文 Ollama 进度面板出错: " + e);
            }
        });
    }

    /** 游戏内（没有打开任何界面）时的绘制位置。 */
    private static void renderInGame(GuiGraphics graphics, int screenWidth) {
        if (!takenOver || !hudEnabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        // hideGui 是 Options 的字段（不是 Minecraft 的），对应 Verity 源码里的 f_92062_
        if (minecraft.options.hideGui || minecraft.screen != null) return;
        Object snapshot = snapshot();
        if (snapshot == null) return;
        if (!shouldShow(snapshot)) return;
        int width = panelWidth(minecraft.font, snapshot, screenWidth);
        boolean right = rightSide(minecraft);
        int x = right ? (screenWidth - width) / 2 : 6;
        int y = right ? 40 : 6;
        render(graphics, minecraft.font, x, y, width, snapshot, true);
    }

    /**
     * 打开界面时的进度面板。
     * <p>普通界面画在右上角；本地 AI 设置界面改为<b>贴着右上角、但往下挪到 y=76</b>——
     * 那个界面的标题（y=8）、状态行（y=20）、进度条（y=56~69）占满了顶部，
     * 面板直接放 y=6 会和它们压在一起，看起来就像被界面盖住了。
     */
    static void renderOverScreen(GuiGraphics graphics, Screen screen, int screenWidth, boolean overSetupScreen) {
        if (!takenOver || !hudEnabled) return;
        Object snapshot = snapshot();
        if (snapshot == null) return;
        if (!shouldShow(snapshot)) return;
        Minecraft minecraft = Minecraft.getInstance();
        int width = panelWidth(minecraft.font, snapshot, screenWidth);
        int x = screenWidth - width - 6;
        int y = overSetupScreen ? SETUP_SCREEN_PANEL_Y : 6;
        render(graphics, minecraft.font, x, y, width, snapshot, true);
    }

    /**
     * 在本地 AI 设置界面上的纵向位置。
     * <p>上方到 y=69 是标题 / 状态行 / 进度条，下方 height-27 起是「完成」按钮，
     * 76 落在两者之间，面板高 48，不会压到任何一边。
     */
    private static final int SETUP_SCREEN_PANEL_Y = 76;

    // ============================================================
    // 读 Verity 的下载状态
    // ============================================================

    /**
     * 取 Verity 当前的下载快照。
     * <p>返回的是 Verity 自己的 {@code Snapshot} 记录对象，字段用反射读——
     * 不复制一份状态，否则两边会不同步。
     */
    private static Object snapshot() {
        try {
            Class<?> manager = Class.forName(MANAGER_CLASS);
            return manager.getMethod("snapshot").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** 该不该显示：正在下载中，或者刚结束（成功 / 失败）的一小段时间内。 */
    private static boolean shouldShow(Object snapshot) {
        try {
            String phase = String.valueOf(call(snapshot, "phase"));
            long finishedAt = ((Number) call(snapshot, "finishedAtMs")).longValue();
            long age = System.currentTimeMillis() - finishedAt;
            return switch (phase) {
                case "RUNNING" -> true;
                case "DONE", "CANCELLED" -> age < 8000L;
                case "FAILED" -> age < 30000L;
                default -> false;
            };
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * 调记录对象上的访问器。
     * <p>{@code snapshot} 的字段名会以 {@code phase} / {@code title} 这样的形式出现，
     * 记录类的访问器与字段同名，所以直接按名字取即可。
     */
    private static Object call(Object record, String name) throws ReflectiveOperationException {
        Method method = record.getClass().getMethod(name);
        return method.invoke(record);
    }

    private static String text(Object record, String name) {
        try {
            Object value = call(record, name);
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    private static long number(Object record, String name) {
        try {
            Object value = call(record, name);
            return value instanceof Number n ? n.longValue() : 0L;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0L;
        }
    }

    private static double decimal(Object record, String name) {
        try {
            Object value = call(record, name);
            return value instanceof Number n ? n.doubleValue() : 0.0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0.0;
        }
    }

    /** 进度比例；总量未知时为 -1。 */
    private static float fraction(Object record) {
        long total = number(record, "total");
        if (total <= 0L) return -1.0f;
        long done = number(record, "done");
        return (float) Math.min(1.0, Math.max(0.0, (double) done / (double) total));
    }

    /** 主手在左边时原版 HUD 靠右，进度面板跟着放中间；否则放左上。 */
    private static boolean rightSide(Minecraft minecraft) {
        return minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
    }

    // ============================================================
    // 绘制
    // ============================================================

    private static final int LINE_GAP = 11;
    private static final int BAR_HEIGHT = 5;

    private static int panelWidth(net.minecraft.client.gui.Font font, Object snapshot, int screenWidth) {
        String title = title(snapshot) + "  " + hint();
        int text = Math.max(font.width(title), Math.max(font.width(detail(snapshot)), font.width(stats(snapshot))));
        int max = Math.max(200, screenWidth - 12);
        return Math.min(max, Math.max(200, text + 14));
    }

    private static void render(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int width,
                               Object snapshot, boolean withHint) {
        int titleY = y + 5;
        int detailY = titleY + LINE_GAP;
        int barY = detailY + LINE_GAP;
        int statsY = barY + BAR_HEIGHT + 3;
        int height = statsY + 9 + 5 - 1 - y;

        int accent = accentColor(snapshot);
        // 背景与左侧色条
        g.fill(x, y, x + width, y + height, 0xC8000000);
        g.fill(x, y, x + 2, y + height, accent);

        int textX = x + 7;
        int textWidth = width - 12;
        int titleWidth = textWidth;
        if (withHint) {
            String hint = hint();
            int hintWidth = font.width(hint);
            g.drawString(font, hint, x + width - 5 - hintWidth, titleY, 0xFF8B8B8B, false);
            titleWidth -= hintWidth + 6;
        }
        g.drawString(font, ellipsize(font, title(snapshot), titleWidth), textX, titleY, 0xFFFFFFFF, false);
        g.drawString(font, ellipsize(font, detail(snapshot), textWidth), textX, detailY, 0xFFB8B8B8, false);

        // 进度条：有总量就是实心比例，没有总量就是来回跑的方块
        g.fill(textX, barY, textX + textWidth, barY + BAR_HEIGHT, 0xFF2A2A2A);
        float fraction = fraction(snapshot);
        if (fraction >= 0.0f) {
            g.fill(textX, barY, textX + Math.round(textWidth * fraction), barY + BAR_HEIGHT, accent);
        } else if ("RUNNING".equals(text(snapshot, "phase"))) {
            int block = textWidth / 4;
            int offset = (int) (System.currentTimeMillis() / 6L % (long) (textWidth + block)) - block;
            int start = Math.max(textX, textX + offset);
            int end = Math.min(textX + textWidth, textX + offset + block);
            if (end > start) {
                g.fill(start, barY, end, barY + BAR_HEIGHT, accent);
            }
        }
        g.drawString(font, ellipsize(font, stats(snapshot), textWidth), textX, statsY, 0xFFDDDDDD, false);
    }

    private static int accentColor(Object snapshot) {
        return switch (text(snapshot, "phase")) {
            case "FAILED" -> 0xFFE05050;
            case "CANCELLED" -> 0xFFB0A090;
            case "DONE" -> 0xFF55FF55;
            default -> 0xFF9BC94A;
        };
    }

    private static String title(Object snapshot) {
        String title = text(snapshot, "title");
        if (title.isEmpty()) return "Ollama";
        return translateTask(title);
    }

    /**
     * 把 Verity 的任务标题换成中文。
     * <p>标题里可能带着模型名（「Downloading qwen3:4b」），所以按前缀匹配，
     * 只换掉前面的动作词，模型名原样保留。
     */
    private static String translateTask(String title) {
        if (title.startsWith("Downloading ")) {
            return "正在下载 " + title.substring("Downloading ".length());
        }
        if (title.startsWith("Deleting ")) {
            return "正在删除 " + title.substring("Deleting ".length());
        }
        return switch (title) {
            case "Setting up Ollama" -> "正在准备 Ollama";
            case "Installing Ollama" -> "正在安装 Ollama";
            case "Starting Ollama" -> "正在启动 Ollama";
            case "Removing Ollama" -> "正在卸载 Ollama";
            default -> title;
        };
    }

    private static String detail(Object snapshot) {
        String detail = text(snapshot, "detail");
        int queued = (int) number(snapshot, "queued");
        if (queued > 0) {
            detail = detail + "  (+" + queued + " 个队列中)";
        }
        return translateDetail(detail);
    }

    /** 把 Verity 的明细文字换成中文；带数字的百分比行原样保留。 */
    private static String translateDetail(String detail) {
        if (detail.isEmpty()) return "";
        // 「完成 12.3 MB / 45.6 MB」这类带数字的行不整句翻译，只换开头的状态词
        return switch (detail) {
            case "Waiting" -> "等待中";
            case "Starting" -> "正在启动";
            case "Contacting Ollama" -> "正在连接 Ollama";
            case "Looking for Ollama" -> "正在查找 Ollama";
            case "Launching the Ollama server" -> "正在启动 Ollama 服务";
            case "Downloading" -> "正在下载";
            case "Downloading model files" -> "正在下载模型文件";
            case "Reading model manifest" -> "正在读取模型清单";
            case "Verifying download" -> "正在校验下载";
            case "Finishing up" -> "正在收尾";
            case "Cleaning up" -> "正在清理";
            case "Done" -> "完成";
            case "Cancelled" -> "已取消";
            case "Removing model files" -> "正在删除模型文件";
            case "Stopping the Ollama server" -> "正在停止 Ollama 服务";
            case "Deleting program files" -> "正在删除程序文件";
            case "Selected as Verity's AI model" -> "已设为 Verity 使用的模型";
            case "Removed from this computer" -> "已从此电脑移除";
            case "Starting Ollama to delete the models" -> "正在启动 Ollama 以删除模型";
            default -> translateSuffix(detail);
        };
    }

    /** 处理「xxx is ready」「xxx deleted」这类以模型名开头、动作词在后的句子。 */
    private static String translateSuffix(String detail) {
        if (detail.endsWith(" is ready")) {
            return detail.substring(0, detail.length() - " is ready".length()) + " 已就绪";
        }
        if (detail.endsWith(" deleted")) {
            return detail.substring(0, detail.length() - " deleted".length()) + " 已删除";
        }
        return detail;
    }

    private static String stats(Object snapshot) {
        String phase = text(snapshot, "phase");
        return switch (phase) {
            case "FAILED" -> "打开本地 AI 设置以重试";
            case "CANCELLED" -> "已下载的部分将保留到下次";
            case "DONE" -> "完成";
            default -> runningStats(snapshot);
        };
    }

    private static String runningStats(Object snapshot) {
        long total = number(snapshot, "total");
        if (total <= 0L) return "处理中...";
        StringBuilder sb = new StringBuilder();
        sb.append((int) (fraction(snapshot) * 100.0f)).append("%  ")
                .append(bytes(number(snapshot, "done"))).append(" / ").append(bytes(total));
        double speed = decimal(snapshot, "bytesPerSecond");
        if (speed > 1.0) {
            sb.append("  ").append(bytes((long) speed)).append("/s");
            long eta = etaSeconds(snapshot);
            if (eta >= 0L) {
                sb.append("  剩余 ").append(duration(eta));
            }
        }
        return sb.toString();
    }

    private static long etaSeconds(Object snapshot) {
        long total = number(snapshot, "total");
        double speed = decimal(snapshot, "bytesPerSecond");
        if (total <= 0L || speed <= 1.0) return -1L;
        return (long) ((total - number(snapshot, "done")) / speed);
    }

    /** 右上角的按键提示，键名来自 Verity 自己，改了按键这里会跟着变。 */
    private static String hint() {
        try {
            Class<?> hud = Class.forName(HUD_CLASS);
            Object name = hud.getMethod("keyName").invoke(null);
            return "[" + name + "] 隐藏";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "[隐藏]";
        }
    }

    private static String bytes(long b) {
        if (b >= 1_000_000_000L) return String.format(java.util.Locale.ROOT, "%.2f GB", b / 1.0E9);
        if (b >= 1_000_000L) return String.format(java.util.Locale.ROOT, "%.1f MB", b / 1_000_000.0);
        if (b >= 1000L) return String.format(java.util.Locale.ROOT, "%.0f KB", b / 1000.0);
        return b + " B";
    }

    private static String duration(long seconds) {
        if (seconds >= 3600L) return seconds / 3600L + "时 " + seconds % 3600L / 60L + "分";
        if (seconds >= 60L) return seconds / 60L + "分 " + seconds % 60L + "秒";
        return seconds + "秒";
    }

    private static String ellipsize(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String dots = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(dots))) + dots;
    }

    /** 每帧路径上的错误只记一次，避免刷屏。 */
    private static boolean warned;

    private static void logOnce(String message) {
        if (warned) return;
        warned = true;
        Log.warn(message);
    }

    // ============================================================
    // 中文面板的开关（由我们自己维护）
    // ============================================================

    /** 中文面板当前是否打开。 */
    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    /** 翻转中文面板的开关，返回翻转后的状态。 */
    public static boolean toggleHud() {
        hudEnabled = !hudEnabled;
        return hudEnabled;
    }

    /** 中文面板是否可用（Verity 装了并且接管成功）。 */
    public static boolean isTakenOver() {
        return takenOver;
    }

    // ============================================================
    // 关掉 Verity 自己的面板
    // ============================================================

    /**
     * 把 Verity 的 {@code visible} 置为 false，让它不再绘制自己的英文面板。
     * <p>{@code toggle()} 会把它翻回来，所以每帧检查一次；玩家按快捷键想隐藏
     * 时由我们的 HUD 自行响应，不受影响。
     * @return 是否成功关掉
     */
    public static boolean suppressVerityHud() {
        try {
            Class<?> hud = Class.forName(HUD_CLASS);
            Field visible = hud.getDeclaredField("visible");
            visible.setAccessible(true);
            if (visible.getBoolean(null)) {
                visible.setBoolean(null, false);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        return false;
    }
}
