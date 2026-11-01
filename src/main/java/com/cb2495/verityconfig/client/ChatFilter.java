package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.config.ModConfig;
import com.cb2495.verityconfig.config.VerityConfigManager;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public class ChatFilter {

    /** DeepSeek 官方计价文档，「详见此处」点击后打开。 */
    private static final String PRICING_DOC_URL = "https://api-docs.deepseek.com/zh-cn/quick_start/pricing/";

    @SubscribeEvent
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (message == null) return;

        String text = message.getString();

        // Need help setting up this mod? Watch this tutorial. → 替换为自定义提示
        if (containsAll(text, "Need help setting up this mod", "Watch this tutorial")) {
            event.setCanceled(true);
            sendCustomHelpMessage();
            return;
        }

        // 以下两条仅屏蔽：
        //   AI 调用出错时 Verity 会发 "AI Setup issue? Tutorial: <链接>"
        //   首次生成时紧跟自定义提示会发一条可点击的 "Setup Tutorial"（链接）
        // 两者来自不同类、触发时机也不同，只能分成两条判断
        if (containsAll(text, "AI Setup issue", "Tutorial")) {
            event.setCanceled(true);
            return;
        }
        if (text.contains("Setup Tutorial")) {
            event.setCanceled(true);
        }
    }

    private static boolean containsAll(String text, String... keywords) {
        for (String k : keywords) {
            if (!text.contains(k)) return false;
        }
        return true;
    }

    private static void sendCustomHelpMessage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component msg = Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("如果你在游玩过程中遇到任何问题，随时输入 ").withStyle(ChatFormatting.WHITE))
                .append(ChatLinks.suggestCmd("/vc qanda", ChatLinks.CLICK_TO_FILL))
                .append(Component.literal(" 获取帮助，如果没有在列表里找到你的问题，也可以在 QQ 交流群中提问。").withStyle(ChatFormatting.WHITE));

        mc.player.displayClientMessage(msg, false);

        // 灰色温馨提示
        Component tip = Component.literal("温馨提示：该模组发送的消息中所有青色(淡蓝色?)字体都是可以点击并自动填入的指令，点击后可按回车直接执行")
                .withStyle(ChatFormatting.GRAY);
        mc.player.displayClientMessage(tip, false);

        // 峰谷提示的首次说明：只在这里说一次，之后不再重复
        sendPricingHintIntro();
    }

    /**
     * 首次下发「可关闭峰谷提示」的说明。
     * <p>峰谷提示本身每次进入存档都会发，但「怎么关」只在首次启动时说明一次；
     * 说明过就写进配置，之后不再出现。开关已关闭或当前不是 DeepSeek 时不说明，
     * 这两种情况下关掉说明会让玩家以后重新打开时再也看不到。
     * <p>不在这里发峰谷提示的正文：正文需要当天节假日数据，
     * 同步取回会卡住启动流程，交给 {@link DeepSeekHintHandler} 在进入存档后异步发出。
     */
    private static void sendPricingHintIntro() {
        if (!ModConfig.isShowDeepSeekHint()) return;
        if (ModConfig.isPricingHintIntroduced()) return;
        if (!"DeepSeek".equals(VerityConfigManager.getCurrentProvider())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.displayClientMessage(
                Component.literal("[提示]").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("DeepSeek 采用峰谷计价方式，计价规则").withStyle(ChatFormatting.WHITE))
                        .append(ChatLinks.openUrl("[详见此处]", PRICING_DOC_URL, "点击打开 DeepSeek 官方计价文档"))
                        .append(Component.literal("。若不想看到此类提示，可输入 ").withStyle(ChatFormatting.WHITE))
                        .append(ChatLinks.suggestCmd("/vc dshint off", ChatLinks.CLICK_TO_FILL))
                        .append(Component.literal(" 关闭。").withStyle(ChatFormatting.WHITE)),
                false);

        ModConfig.setPricingHintIntroduced(true);
    }
}