package com.cb2495.verityconfig;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.commands.Commands;

@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = VerityConfig.MODID, value = Dist.CLIENT)
public class ClientCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("vc")
                        .executes(ctx -> {
                            showHelpPrompt();
                            return 1;
                        })
                        .then(Commands.literal("cs")
                                .executes(ctx -> {
                                    openConfigScreen();
                                    return 1;
                                })
                        )
                        .then(Commands.literal("configscreen")
                                .executes(ctx -> {
                                    openConfigScreen();
                                    return 1;
                                })
                        )
                        .then(Commands.literal("help")
                                .executes(ctx -> {
                                    showCommandList();
                                    return 1;
                                })
                                .then(Commands.argument("topic", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String topic = StringArgumentType.getString(ctx, "topic");
                                            openHelpTopic(topic);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("qanda")
                                .executes(ctx -> {
                                    Minecraft.getInstance().setScreen(new QandAScreen());
                                    return 1;
                                })
                        )
        );
    }

    private static void openConfigScreen() {
        Minecraft.getInstance().setScreen(new VerityConfigScreen());
    }

    private static void showHelpPrompt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.displayClientMessage(
                Component.literal("[VerityConfig] 输入 /vc help 查看指令列表，或 /vc cs 打开配置界面。")
                        .withStyle(ChatFormatting.YELLOW),
                false
        );
    }

    private static void showCommandList() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.displayClientMessage(Component.literal("==== VerityConfig 指令 ====").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD), false);
        mc.player.displayClientMessage(Component.literal("/vc cs - 打开配置界面"), false);
        mc.player.displayClientMessage(Component.literal("/vc help <主题> - 打开指定帮助主题"), false);
        mc.player.displayClientMessage(Component.literal("/vc help - 查看此列表"), false);
    }

    private static void openHelpTopic(String topic) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 检查帮助资源是否存在（Windows 和 Android 任意一个存在即可）
        String pcPath = "help/" + topic + "_pc.txt";
        String mobilePath = "help/" + topic + "_mobile.txt";
        boolean exists = doesResourceExist(pcPath) || doesResourceExist(mobilePath);

        if (exists) {
            mc.setScreen(new HelperScreen(topic));
        } else {
            mc.player.displayClientMessage(
                    Component.literal("[VerityConfig] 未找到帮助主题: " + topic)
                            .withStyle(ChatFormatting.RED),
                    false
            );
        }
    }

    private static boolean doesResourceExist(String path) {
        try {
            ResourceLocation res = new ResourceLocation(VerityConfig.MODID, path);
            return Minecraft.getInstance().getResourceManager().getResource(res).isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}