package com.cb2495.verityconfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public class ChatFilter {

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

        // 其他两条仅屏蔽
        if (containsAll(text, "Problem setting AI", "Watch this tutorial")) {
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
                .append(Component.literal("/vc qanda")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/vc qanda"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击填入命令")))
                        ))
                .append(Component.literal(" 获取帮助，如果没有在列表里找到你的问题，也可以在 QQ 交流群中提问。").withStyle(ChatFormatting.WHITE));

        mc.player.displayClientMessage(msg, false);

        // 灰色温馨提示
        Component tip = Component.literal("温馨提示：该模组发送的消息中所有青色(淡蓝色?)字体都是可以点击并自动填入的指令，点击后可按回车直接执行")
                .withStyle(ChatFormatting.GRAY);
        mc.player.displayClientMessage(tip, false);
    }
}