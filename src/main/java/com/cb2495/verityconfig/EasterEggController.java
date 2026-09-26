package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.EasterEggAssets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 欢迎界面版本号上的彩蛋。
 * <p>交互分两段：
 * <ol>
 *   <li>点版本号 → 播放 67_effect，中央淡入 67-kid（每次点击抬高
 *       {@link #KID_ALPHA_STEP} 不透明度，同时持续以每秒
 *       {@link #KID_ALPHA_DECAY_PER_SECOND} 淡出）</li>
 *   <li>小图不透明度超过 {@link #KID_ALPHA_THRESHOLD} 时再点一次 →
 *       弹出成就提示 → 遮罩淡入 → 播放 67.ogg 并全屏淡入大图 →
 *       音乐播完后整体淡出</li>
 * </ol>
 * <p>计时全部基于 {@link System#currentTimeMillis()}：{@code render} 每帧都会
 * 调用，不需要依赖 {@code tick()}，也不受窗口失焦影响。
 */
@SuppressWarnings("removal")
public class EasterEggController {

    /** 阶段。 */
    private enum Phase {
        /** 空闲：只有小图可能出现。 */
        IDLE,
        /** 遮罩正在淡入（0.67s），结束后开始放音乐与大图。 */
        MASK_IN,
        /** 大图正在淡入（1.67s）并播放音乐。 */
        BIG_IN,
        /** 音乐播放中，画面保持完全显示。 */
        PLAYING,
        /** 整体淡出（0.67s），结束后回到空闲。 */
        FADING_OUT
    }

    // ===== 可调参数 =====
    /**
     * 小图不透明度标尺为 0~1。
     * <p>每次点击把「目标不透明度」抬高 {@link #KID_ALPHA_STEP}，
     * 显示值再用 {@link #KID_ALPHA_EASE_MS} 缓入追上目标，
     * 这样连续点击时是平滑变亮而不是每点一下跳一格。
     */
    private static final float KID_ALPHA_STEP = 0.18f;
    /** 每次点击后的缓入时长。 */
    private static final long KID_ALPHA_EASE_MS = 67;
    /** 小图每秒自然衰减的不透明度。 */
    private static final float KID_ALPHA_DECAY_PER_SECOND = 0.15f;
    /**
     * 小图不透明度超过它时，再点一次才进入第二段（切换大图并播放音乐）。
     * <p>取值较高是为了让用户先看清小图：太低的话刚点两下就跳走了。
     * <p>注意标尺是 0~1，{@code 0.80} 即 80%。
     */
    private static final float KID_ALPHA_THRESHOLD = 0.80f;
    /** 不透明度上限。 */
    private static final float KID_ALPHA_MAX = 1f;

    private static final long MASK_IN_MS = 670;
    private static final long BIG_IN_MS = 1670;
    private static final long FADE_OUT_MS = 670;

    /** 遮罩颜色 #000E2B。 */
    private static final int MASK_COLOR = 0x000E2B;

    /** 点过版本号之后，版本号本身的颜色（淡蓝），与教程按钮的边框同色。 */
    public static final int VERSION_CLICKED_COLOR = 0xFF88CCFF;

    // ===== 成就提示 =====
    /** 提示停留时长：滑入后停留这么久再收回。 */
    private static final long ACHIEVEMENT_HOLD_MS = 6700;
    /** 滑入 / 收回的动画时长。 */
    private static final long ACHIEVEMENT_SLIDE_MS = 300;
    /** 提示面板尺寸。 */
    private static final int ACHIEVEMENT_WIDTH = 190;
    private static final int ACHIEVEMENT_HEIGHT = 42;
    /** 距屏幕右下角的边距。 */
    private static final int ACHIEVEMENT_MARGIN = 12;
    /** 图标边长（留出内边距后）。 */
    private static final int ACHIEVEMENT_ICON = ACHIEVEMENT_HEIGHT - 8;
    /** 面板底色与描边。 */
    private static final int ACHIEVEMENT_BG = 0xE0202020;
    private static final int ACHIEVEMENT_BORDER = 0xFF88CCFF;
    /** 标题与正文颜色。 */
    private static final int ACHIEVEMENT_TITLE_COLOR = 0xFFFFD700;
    private static final int ACHIEVEMENT_TEXT_COLOR = 0xFFFFFFFF;

    /** 成就提示的开始时间；{@code -1} 表示未激活。 */
    private long achievementAt = -1;

    // ===== 状态 =====
    private Phase phase = Phase.IDLE;

    /** 小图目标不透明度（0~1）：每次点击抬高，衰减时同步下降。 */
    private float kidAlphaTarget = 0f;

    /** 小图实际显示的不透明度（0~1）：缓入追向目标值。 */
    private float kidAlphaShown = 0f;

    /** 本段缓入的起点与起始时间。 */
    private float kidEaseFrom = 0f;
    private long kidEaseStart = -1;

    /** 当前阶段开始的时间戳；{@code -1} 表示该阶段未激活。 */
    private long phaseStart = -1;

    /** 正在播放的背景音乐，用于判断是否播完。 */
    private SoundInstance music = null;

    /** 上一帧时间戳，用于按真实经过时间衰减小图。 */
    private long lastFrameAt = -1;

    /**
     * 是否点过版本号。
     * <p>点过之后版本号本身会变成淡蓝色，作为「这里藏着东西」的提示。
     */
    private boolean versionClicked = false;

    /** 小图当前在屏幕上的显示区域：{x, y, 边长}；未显示时为 null。 */
    private int[] kidRect = null;

    private static final ResourceLocation EFFECT_SOUND =
            new ResourceLocation(VerityConfig.MODID, "easteregg.67_effect");
    private static final ResourceLocation MUSIC_SOUND =
            new ResourceLocation(VerityConfig.MODID, "easteregg.67");

    /**
     * 点击处理。
     * <p>小图还没出现时只认版本号那一小块，避免误触；
     * 小图显形后改为只认小图自身的显示范围，点在图外不算，
     * 免得用户以为随便点哪都行。
     *
     * @return 是否消费了这次点击
     */
    public boolean onClickVersion() {
        if (phase != Phase.IDLE) {
            // 演出进行中，点击不再叠加，避免重复触发音乐
            return true;
        }
        // 还没到阈值：把目标抬高一个档，并重新开始一段缓入
        if (kidAlphaTarget <= KID_ALPHA_THRESHOLD) {
            versionClicked = true;
            kidEaseFrom = kidAlphaShown;
            kidEaseStart = now();
            kidAlphaTarget = Math.min(KID_ALPHA_MAX, kidAlphaTarget + KID_ALPHA_STEP);
            playEffect();
            return true;
        }
        // 已超过阈值：进入第二段
        versionClicked = true;
        startBigSequence();
        return true;
    }

    /**
     * 该坐标是否落在小图的显示范围内。
     * <p>小图未显示时返回 {@code false}：此时由调用方回退到版本号区域。
     */
    public boolean isInsideKid(double mouseX, double mouseY) {
        if (kidRect == null || kidAlphaShown <= 0f) return false;
        int size = kidRect[2];
        return mouseX >= kidRect[0] && mouseX <= kidRect[0] + size
                && mouseY >= kidRect[1] && mouseY <= kidRect[1] + size;
    }

    /** 小图当前是否已显形（不透明度 &gt; 0）。 */
    public boolean isKidVisible() {
        return kidAlphaShown > 0f;
    }

    /** 版本号是否已被点过（用于把它染成淡蓝色）。 */
    public boolean isVersionClicked() {
        return versionClicked;
    }

    private void playEffect() {
        try {
            SoundEvent event = SoundEvent.createVariableRangeEvent(EFFECT_SOUND);
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(event, 1.0f));
        } catch (Exception e) {
            System.err.println("[VerityConfig] 彩蛋音效播放失败: " + e.getMessage());
        }
    }

    private void startBigSequence() {
        phase = Phase.MASK_IN;
        phaseStart = now();
        // 进入演出后小图不再单独显示，避免和全屏图叠在一起
        kidAlphaShown = 0f;
        kidAlphaTarget = 0f;
        kidEaseStart = -1;
        kidRect = null;
        // 弹出成就提示
        achievementAt = now();
    }

    private void startMusic() {
        try {
            SoundEvent event = SoundEvent.createVariableRangeEvent(MUSIC_SOUND);
            music = SimpleSoundInstance.forUI(event, 1.0f);
            Minecraft.getInstance().getSoundManager().play(music);
        } catch (Exception e) {
            System.err.println("[VerityConfig] 彩蛋音乐播放失败: " + e.getMessage());
            music = null;
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** 每帧推进状态：处理小图衰减、阶段切换与音乐结束检测。 */
    private void tick() {
        long current = now();

        if (phase == Phase.IDLE) {
            // 距离上一帧的真实时间。首帧（lastFrameAt < 0）当作没经过时间，
            // 否则第一次点击会被一个巨大的 dt 立刻衰减掉。
            float elapsedSeconds = lastFrameAt > 0
                    ? Math.min(0.5f, (current - lastFrameAt) / 1000f)
                    : 0f;

            // 1. 目标值匀速衰减。它代表「用户点出来的亮度」，不点就自己退回去。
            if (elapsedSeconds > 0f && kidAlphaTarget > 0f) {
                kidAlphaTarget = Math.max(0f,
                        kidAlphaTarget - KID_ALPHA_DECAY_PER_SECOND * elapsedSeconds);
            }

            // 2. 显示值缓入追向目标。这里刻意不反过来压低 target：
            //    target 已在第 1 步按真实时间衰减过，若再让它跟着 shown 走，
            //    连点时目标永远涨不过一个台阶，阈值就永远越不过去。
            if (kidEaseStart > 0 && current - kidEaseStart < KID_ALPHA_EASE_MS) {
                float progress = clamp01((float) (current - kidEaseStart) / KID_ALPHA_EASE_MS);
                kidAlphaShown = kidEaseFrom
                        + (kidAlphaTarget - kidEaseFrom) * progress;
            } else {
                kidAlphaShown = kidAlphaTarget;
            }

            // 只在目标确实衰减到 0 时才收尾，不能用 shown 判断：
            // 缓入刚开始时 shown 还是 0，那样会把刚点出来的目标一并清掉
            if (kidAlphaTarget <= 0f) {
                kidAlphaShown = 0f;
                kidEaseStart = -1;
            }
        }
        lastFrameAt = current;

        if (phase == Phase.IDLE || phaseStart < 0) return;
        long elapsed = current - phaseStart;

        switch (phase) {
            case MASK_IN -> {
                if (elapsed >= MASK_IN_MS) {
                    // 遮罩铺满后再起音乐，两者不会互相掩盖起点
                    startMusic();
                    phase = Phase.BIG_IN;
                    phaseStart = current;
                }
            }
            case BIG_IN -> {
                if (elapsed >= BIG_IN_MS) {
                    phase = Phase.PLAYING;
                    phaseStart = current;
                }
            }
            case PLAYING -> {
                // 音乐结束（或压根没播起来）就进入淡出
                if (music == null || !isMusicPlaying()) {
                    phase = Phase.FADING_OUT;
                    phaseStart = current;
                }
            }
            case FADING_OUT -> {
                if (elapsed >= FADE_OUT_MS) {
                    reset();
                }
            }
            default -> {
            }
        }
    }

    private boolean isMusicPlaying() {
        try {
            return Minecraft.getInstance().getSoundManager().isActive(music);
        } catch (Exception e) {
            return false;
        }
    }

    /** 回到空闲状态，并停掉可能还在响的音乐。 */
    private void reset() {
        if (music != null) {
            try {
                Minecraft.getInstance().getSoundManager().stop(music);
            } catch (Exception ignored) {
            }
            music = null;
        }
        phase = Phase.IDLE;
        phaseStart = -1;
        kidAlphaShown = 0f;
        kidAlphaTarget = 0f;
        kidEaseStart = -1;
        // 清掉命中区域与成就提示：留着会让旧坐标继续吃点击、或在下次
        // 进入本界面时凭空冒出一个成就弹窗
        kidRect = null;
        achievementAt = -1;
        // versionClicked 刻意不重置：用户已经发现了这个彩蛋，
        // 再次进入欢迎界面时版本号应当保持淡蓝色
    }

    /** 离开欢迎界面时调用：停掉音乐，避免在别的界面继续响。 */
    public void onScreenClosed() {
        reset();
    }

    // ===== 渲染 =====

    /**
     * 画彩蛋。
     * <p>层级自下而上：遮罩 → 大图 → 成就提示。
     * <p>遮罩必须画在最底下：它是一层纯色底板，作用是把欢迎界面的
     * 文字与按钮盖住；若画在大图之上，大图会被整块遮没。
     */
    public void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        tick();

        if (phase == Phase.IDLE) {
            renderKid(graphics, screenWidth, screenHeight);
            renderAchievement(graphics, screenWidth, screenHeight);
            return;
        }

        // 1. 底板遮罩：把欢迎界面的文字与按钮盖住
        float maskAlpha = maskAlpha();
        if (maskAlpha > 0f) {
            graphics.fill(0, 0, screenWidth, screenHeight, withAlpha(MASK_COLOR, maskAlpha));
        }

        // 2. 大图：叠在遮罩之上
        float bigAlpha = bigImageAlpha();
        if (bigAlpha > 0f && EasterEggAssets.isReady(EasterEggAssets.ANGRY_BIRD)) {
            drawBigImage(graphics, screenWidth, screenHeight, bigAlpha);
        }

        // 3. 成就提示：最上层，压住遮罩与大图
        renderAchievement(graphics, screenWidth, screenHeight);
    }

    /**
     * 右下角的 Steam 样式成就提示。
     * <p>从右侧滑入 → 停留 {@link #ACHIEVEMENT_HOLD_MS} → 滑回右侧收起。
     * <p>用滑入滑出而不是淡入淡出：贴近 Steam 的实际表现，也更醒目。
     */
    private void renderAchievement(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (achievementAt < 0) return;

        long elapsed = now() - achievementAt;
        long total = ACHIEVEMENT_SLIDE_MS + ACHIEVEMENT_HOLD_MS + ACHIEVEMENT_SLIDE_MS;
        if (elapsed >= total) {
            achievementAt = -1;
            return;
        }
        if (!EasterEggAssets.isReady(EasterEggAssets.KID)) return;

        // 进度 0~1：0 表示完全收起在屏幕外，1 表示完全展开
        float progress;
        if (elapsed < ACHIEVEMENT_SLIDE_MS) {
            progress = (float) elapsed / ACHIEVEMENT_SLIDE_MS;
        } else if (elapsed < ACHIEVEMENT_SLIDE_MS + ACHIEVEMENT_HOLD_MS) {
            progress = 1f;
        } else {
            long outElapsed = elapsed - ACHIEVEMENT_SLIDE_MS - ACHIEVEMENT_HOLD_MS;
            progress = 1f - (float) outElapsed / ACHIEVEMENT_SLIDE_MS;
        }
        progress = clamp01(progress);

        // 从右侧滑入：完全收起时整个面板在屏幕右边界之外
        int hiddenX = screenWidth;
        int shownX = screenWidth - ACHIEVEMENT_WIDTH - ACHIEVEMENT_MARGIN;
        int panelX = (int) (hiddenX + (shownX - hiddenX) * progress);
        int panelY = screenHeight - ACHIEVEMENT_HEIGHT - ACHIEVEMENT_MARGIN;

        graphics.fill(panelX, panelY, panelX + ACHIEVEMENT_WIDTH,
                panelY + ACHIEVEMENT_HEIGHT, ACHIEVEMENT_BG);
        graphics.renderOutline(panelX, panelY, ACHIEVEMENT_WIDTH, ACHIEVEMENT_HEIGHT,
                ACHIEVEMENT_BORDER);

        // 图标：直接用 67-kid 那张图，等比缩放到面板内
        int iconX = panelX + 4;
        int iconY = panelY + 4;
        drawTexture(graphics, EasterEggAssets.KID, iconX, iconY,
                ACHIEVEMENT_ICON, ACHIEVEMENT_ICON, 1f);

        int textX = iconX + ACHIEVEMENT_ICON + 6;
        graphics.drawString(font(), "成就已达成！", textX, panelY + 7,
                ACHIEVEMENT_TITLE_COLOR, false);
        graphics.drawString(font(), "你发现了67彩蛋！", textX, panelY + 7 + 12,
                ACHIEVEMENT_TEXT_COLOR, false);
    }

    /** 取客户端的字体对象。 */
    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** 小图：宽度为屏幕高度的 2/3，居中显示。 */
    private void renderKid(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (kidAlphaShown <= 0f) {
            // 已经淡完，清掉命中区域，后续点击回退到版本号
            kidRect = null;
            return;
        }
        if (!EasterEggAssets.isReady(EasterEggAssets.KID)) {
            logDrawError("小图未就绪（缓存里没有 67-kid）");
            kidRect = null;
            return;
        }

        int size = (int) (screenHeight * (2f / 3f));
        int x = (screenWidth - size) / 2;
        int y = (screenHeight - size) / 2;
        // 记录显示区域：点击检测与绘制共用同一份坐标，避免两处算岔
        kidRect = new int[]{x, y, size};
        drawTexture(graphics, EasterEggAssets.KID, x, y, size, size, kidAlphaShown);
    }

    /** 大图：高度与屏幕一致，宽度按原始比例缩放，居中。 */
    private void drawBigImage(GuiGraphics graphics, int screenWidth, int screenHeight, float alpha) {
        int sourceWidth = EasterEggAssets.width(EasterEggAssets.ANGRY_BIRD);
        int sourceHeight = EasterEggAssets.height(EasterEggAssets.ANGRY_BIRD);
        if (sourceWidth <= 0 || sourceHeight <= 0) return;

        int drawHeight = screenHeight;
        int drawWidth = Math.round(drawHeight * ((float) sourceWidth / sourceHeight));
        // 极窄屏上按高度算可能宽过屏幕，此时改为按宽度撑满，继续保持比例
        if (drawWidth > screenWidth) {
            drawWidth = screenWidth;
            drawHeight = Math.round(drawWidth * ((float) sourceHeight / sourceWidth));
        }
        int x = (screenWidth - drawWidth) / 2;
        int y = (screenHeight - drawHeight) / 2;
        drawTexture(graphics, EasterEggAssets.ANGRY_BIRD, x, y, drawWidth, drawHeight, alpha);
    }

    /**
     * 画一张贴图，并按 alpha 做淡入淡出。
     * <p>用回 {@code GuiGraphics.blit} 而不是自己提交四边形：后者虽然
     * 顶点数据正确（日志可见），但立即提交与 {@code bufferSource} 的
     * 批次在同一帧内交错，结果无法落屏。
     * <p>alpha 改由 {@code RenderSystem.setShaderColor} 施加。它作用在
     * 着色器的全局颜色上，会与顶点色相乘，因此走 {@code blit} 也能淡入淡出。
     */
    private void drawTexture(GuiGraphics graphics, String name, int x, int y,
            int width, int height, float alpha) {
        ResourceLocation texture = EasterEggAssets.texture(name);
        int texWidth = EasterEggAssets.width(name);
        int texHeight = EasterEggAssets.height(name);
        if (texture == null || width <= 0 || height <= 0 || texWidth <= 0 || texHeight <= 0) {
            logDrawError("参数无效 name=" + name + " tex=" + texture
                    + " texW=" + texWidth + " texH=" + texHeight
                    + " drawW=" + width + " drawH=" + height);
            return;
        }

        float a = clamp01(alpha);
        if (a <= 0f) return;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale((float) width / texWidth, (float) height / texHeight, 1.0f);

        // 关掉深度测试：GUI 元素都画在同一层，深度测试会按 z 值互相遮挡，
        // 让后画的图片被先前写入的界面元素挡掉
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, a);

        graphics.blit(texture, 0, 0, 0, 0, texWidth, texHeight, texWidth, texHeight);

        // 还原全局状态，避免影响后续绘制
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        pose.popPose();
    }

    /** 遮罩不透明度：MASK_IN 期间从 0 淡入到 1，之后保持满值直到淡出阶段。 */
    private float maskAlpha() {
        long elapsed = phaseStart < 0 ? 0 : now() - phaseStart;
        return switch (phase) {
            case MASK_IN -> clamp01((float) elapsed / MASK_IN_MS);
            case FADING_OUT -> 1f - clamp01((float) elapsed / FADE_OUT_MS);
            default -> 1f;
        };
    }

    /**
     * 大图不透明度。
     * <p>淡入结束后保持显示；淡出阶段与遮罩同步淡出，
     * 这样回到欢迎界面时两者一起消失，不会有一层残留。
     */
    private float bigImageAlpha() {
        long elapsed = phaseStart < 0 ? 0 : now() - phaseStart;
        return switch (phase) {
            case MASK_IN -> 0f;
            case BIG_IN -> clamp01((float) elapsed / BIG_IN_MS);
            case PLAYING -> 1f;
            case FADING_OUT -> 1f - clamp01((float) elapsed / FADE_OUT_MS);
            default -> 0f;
        };
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * 绘制异常日志。
     * <p>只在资源缺失、参数非法这类真正出问题的情况下打印；
     * 正常绘制不输出任何日志，避免刷屏。
     */
    private static void logDrawError(String message) {
        System.err.println("[VerityConfig/彩蛋] " + message);
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = (int) (clamp01(alpha) * 255f) & 0xFF;
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /** 供界面判断是否需要拦截点击（演出期间不允许点按钮）。 */
    public boolean blocksInput() {
        return phase == Phase.MASK_IN || phase == Phase.BIG_IN
                || phase == Phase.PLAYING || phase == Phase.FADING_OUT;
    }
}
