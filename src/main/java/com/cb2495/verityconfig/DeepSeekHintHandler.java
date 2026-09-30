package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.DeepSeekPricing;
import com.cb2495.verityconfig.util.ModConfig;
import com.cb2495.verityconfig.util.VerityConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDate;

/**
 * DeepSeek 峰谷时段提示。
 * <p>仅在当前提供商为 DeepSeek 时生效，触发时机：
 * <ul>
 *   <li>进入存档时提示一次当前时段</li>
 *   <li>峰谷时段发生切换时提示一次</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public class DeepSeekHintHandler {

    /** 上次提示时所处的状态（是否高峰），用于检测切换。 */
    private static Boolean lastPeak = null;
    /** 上次检查的日期，用于跨天时重新刷新。 */
    private static int lastDayOfYear = -1;
    /** 进入存档后延迟若干 tick 再提示，等聊天栏就绪。 */
    private static int pendingTicks = 0;
    /** 等待预取完成已消耗的 tick 数。 */
    private static int extraWaitTicks = 0;
    /** 等待预取的最长 tick 数（5 秒）。 */
    private static final int MAX_EXTRA_WAIT_TICKS = 100;
    /** 本次进入存档是否需要提示。 */
    private static boolean pendingHint = false;
    /** 当前提供商是否为 DeepSeek；每 tick 都读配置会频繁访问磁盘，故缓存。 */
    private static boolean providerIsDeepSeek = false;
    /** 提示开关状态，同样避免每 tick 读盘。 */
    private static boolean hintEnabled = false;

    @SubscribeEvent
    public static void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // 每次进入存档时刷新提供商与开关判断，之后 tick 直接读缓存
        providerIsDeepSeek = "DeepSeek".equals(VerityConfigManager.getCurrentProvider());
        hintEnabled = ModConfig.isShowDeepSeekHint();
        if (!providerIsDeepSeek) return;
        if (!hintEnabled) return;

        // 进入存档先刷新当天数据，并预取未来数日，供跨天预测使用
        // 预取 10 天以覆盖春节等最长假期，避免假期被误判为工作日
        DeepSeekPricing.refreshIfNeeded();
        DeepSeekPricing.prefetchUpcoming(10);
        pendingHint = true;
        pendingTicks = 20;
        extraWaitTicks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!providerIsDeepSeek) return;
        if (!hintEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 跨天时重新拉取当天数据
        int day = LocalDate.now().getDayOfYear();
        if (day != lastDayOfYear) {
            lastDayOfYear = day;
            DeepSeekPricing.refreshIfNeeded();
        }

        // 当天数据未就绪时不做判断，避免用周末估算误报
        if (!DeepSeekPricing.isReady()) return;

        boolean peak = DeepSeekPricing.isPeak();

        // 进入存档后的延迟提示
        if (pendingHint) {
            // 先等基础延迟，让聊天栏就绪
            if (pendingTicks > 0) {
                pendingTicks--;
                return;
            }
            // 再等预取结束，让"下一个谷期/峰期"的时间尽量准确；
            // 最多额外等 MAX_EXTRA_WAIT_TICKS，避免网络慢时迟迟不提示
            if (DeepSeekPricing.isPrefetching() && extraWaitTicks < MAX_EXTRA_WAIT_TICKS) {
                extraWaitTicks++;
                return;
            }
            pendingHint = false;
            lastPeak = peak;
            sendHint(peak, true);
            return;
        }

        // 时段切换提示
        if (lastPeak != null && lastPeak != peak) {
            lastPeak = peak;
            sendHint(peak, false);
            return;
        }
        lastPeak = peak;
    }

    /** 离开存档时清除状态，下次进入重新提示。 */
    @SubscribeEvent
    public static void onClientPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastPeak = null;
        pendingHint = false;
        pendingTicks = 0;
        extraWaitTicks = 0;
    }

    /**
     * 重新读取开关状态，供 {@code /vc dshint} 修改配置后调用，
     * 使改动立即生效而不必等到下次进入存档。
     */
    public static void refreshToggle() {
        hintEnabled = ModConfig.isShowDeepSeekHint();
        if (!hintEnabled) {
            // 关闭时清掉待发送的提示，避免关闭后仍弹出一条
            pendingHint = false;
            pendingTicks = 0;
        }
    }

    /**
     * 发送时段提示。
     *
     * @param peak       当前是否为高峰时段
     * @param withToggle 是否附带关闭提示的说明（仅进入存档时）
     */
    private static void sendHint(boolean peak, boolean withToggle) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (peak) {
            String next = DeepSeekPricing.format(DeepSeekPricing.nextValleyStart());
            player.displayClientMessage(
                    Component.literal("[提示]").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("当前时间段为Deepseek计价峰期，可能会造成不必要的金钱开销，下一个谷期在 ")
                                    .withStyle(ChatFormatting.RED))
                            .append(Component.literal(next).withStyle(ChatFormatting.GOLD))
                            .append(Component.literal("。").withStyle(ChatFormatting.RED)),
                    false);
        } else {
            String next = DeepSeekPricing.format(DeepSeekPricing.nextPeakStart());
            player.displayClientMessage(
                    Component.literal("[提示]").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("当前时间段为Deepseek计价谷期，可以在 ")
                                    .withStyle(ChatFormatting.GREEN))
                            .append(Component.literal(next).withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(" 前低价游玩").withStyle(ChatFormatting.GREEN)),
                    false);
        }

        if (withToggle) {
            player.displayClientMessage(
                    Component.literal("[提示]").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("输入 ").withStyle(ChatFormatting.WHITE))
                            .append(suggestCmd("/vc dshint off", "点击填入命令"))
                            .append(Component.literal(" 可关闭此提示。").withStyle(ChatFormatting.WHITE)),
                    false);
        }
    }

    private static Component suggestCmd(String command, String hover) {
        return Component.literal(command)
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover)))
                );
    }
}
