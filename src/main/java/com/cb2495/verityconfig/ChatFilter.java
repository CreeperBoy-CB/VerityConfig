package com.cb2495.verityconfig;

import net.minecraft.network.chat.Component;
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

        // 三条教程提示，使用关键词组合匹配，避免因格式拆分导致完整短语匹配失败
        if (containsAll(text, "Need help setting up this mod", "Watch this tutorial")) {
            event.setCanceled(true);
            return;
        }
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
}