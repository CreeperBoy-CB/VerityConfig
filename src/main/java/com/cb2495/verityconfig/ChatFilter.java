package com.cb2495.verityconfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public class ChatFilter {

    private static final String SETUP_TUTORIAL = "Need help setting up this mod? Watch this tutorial.";
    private static final String AI_TUTORIAL = "Problem setting AI? Watch this tutorial.";
    private static final String SETUP_TUTORIAL_LINK = "Setup Tutorial";

    @SubscribeEvent
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        Component message = event.getMessage();
        if (message == null) return;

        String text = message.getString();

        // 完全匹配需要替换的消息
        if (SETUP_TUTORIAL.equals(text)) {
            event.setCanceled(true);
            sendCustomHelpMessage();
            return;
        }

        // 其他仅屏蔽
        if (text.contains(AI_TUTORIAL) || text.contains(SETUP_TUTORIAL_LINK)) {
            event.setCanceled(true);
        }
    }

    private static void sendCustomHelpMessage() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component helpMsg = Component.literal("[VerityConfig] ")
                .append(Component.literal("如果你在游玩过程中遇到任何问题，随时输入 ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("/vc qanda").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" 获取帮助，如果没有在列表里找到你的问题，也可以在QQ交流群中提问。")
                        .withStyle(ChatFormatting.YELLOW));

        mc.player.displayClientMessage(helpMsg, false);
    }
}