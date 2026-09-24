package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.VerityMemoryManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
                        .then(Commands.literal("modls")
                                .executes(ctx -> {
                                    ModsListScreen.returnToConfigScreen = false;
                                    Minecraft.getInstance().setScreen(new ModsListScreen());
                                    return 1;
                                })
                        )
                        .then(Commands.literal("exitgame")
                                .executes(ctx -> {
                                    Minecraft.getInstance().stop();
                                    return 1;
                                })
                        )
                        .then(Commands.literal("verity")
                                .then(Commands.literal("mfix")
                                        .executes(ctx -> {
                                            VerityMemoryManager.fix();
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("mdel")
                                        .executes(ctx -> {
                                            VerityMemoryManager.delete();
                                            return 1;
                                        })
                                )
                        )
        );
    }

    private static void openConfigScreen() {
        Minecraft.getInstance().setScreen(new VerityConfigScreen());
    }

    private static void showHelpPrompt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component msg = Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("输入 ").withStyle(ChatFormatting.WHITE))
                .append(suggestCmd("/vc help", "点击填入命令"))
                .append(Component.literal(" 查看指令列表，或 ").withStyle(ChatFormatting.WHITE))
                .append(suggestCmd("/vc cs", "点击填入命令"))
                .append(Component.literal(" 打开配置界面。").withStyle(ChatFormatting.WHITE));

        mc.player.displayClientMessage(msg, false);
    }

    private static void showCommandList() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.displayClientMessage(Component.literal("==== VerityConfig 指令 ====").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD), false);
        mc.player.displayClientMessage(suggestLine("/vc cs", "打开配置界面"), false);
        mc.player.displayClientMessage(suggestLine("/vc modls", "打开模组管理界面"), false);
        mc.player.displayClientMessage(suggestLine("/vc help", "查看此列表"), false);
        mc.player.displayClientMessage(suggestLine("/vc qanda", "打开常见问题解答"), false);
        mc.player.displayClientMessage(suggestLine("/vc verity mfix", "修复 Verity 的聊天记忆（移除空 AI 消息）"), false);
        mc.player.displayClientMessage(suggestLine("/vc verity mdel", "删除记忆文件（让 Verity 重新生成）"), false);
    }

    private static void openHelpTopic(String topic) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

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

    // ---------- 可点击填充命令的组件 ----------
    private static Component suggestCmd(String command, String hover) {
        return Component.literal(command)
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover)))
                );
    }

    private static Component suggestLine(String command, String desc) {
        return suggestCmd(command, "点击填入命令")
                .copy()
                .append(Component.literal(" - " + desc).withStyle(ChatFormatting.GRAY));
    }
}