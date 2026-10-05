package com.cb2495.verityconfig.client;

import com.cb2495.verityconfig.VerityConfig;
import com.cb2495.verityconfig.config.ModConfig;
import com.cb2495.verityconfig.screen.HelperScreen;
import com.cb2495.verityconfig.screen.ModsListScreen;
import com.cb2495.verityconfig.screen.QandAScreen;
import com.cb2495.verityconfig.screen.VerityConfigScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
                                .then(Commands.argument("topic", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> {
                                            for (String title : HelperScreen.topicTitles()) {
                                                builder.suggest(title);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String topic = StringArgumentType.getString(ctx, "topic");
                                            // 允许直接输入中文名，内部统一转成英文 topic
                                            openHelpTopic(HelperScreen.resolveTopic(topic));
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
                        .then(Commands.literal("dshint")
                                .executes(ctx -> {
                                    boolean next = !ModConfig.isShowDeepSeekHint();
                                    ModConfig.setShowDeepSeekHint(next);
                                    DeepSeekHintHandler.refreshToggle();
                                    sendToggleMessage(next);
                                    return 1;
                                })
                                .then(Commands.literal("on")
                                        .executes(ctx -> {
                                            ModConfig.setShowDeepSeekHint(true);
                                            DeepSeekHintHandler.refreshToggle();
                                            sendToggleMessage(true);
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("off")
                                        .executes(ctx -> {
                                            ModConfig.setShowDeepSeekHint(false);
                                            DeepSeekHintHandler.refreshToggle();
                                            sendToggleMessage(false);
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("date")
                                        // 不带参数：按当前时间输出一次提示
                                        .executes(ctx -> {
                                            DeepSeekHintHandler.sendHintAt(LocalDateTime.now());
                                            return 1;
                                        })
                                        .then(Commands.argument("date", StringArgumentType.word())
                                                .then(Commands.argument("time", StringArgumentType.word())
                                                        .executes(ctx -> {
                                                            runDeepSeekDateTest(
                                                                    StringArgumentType.getString(ctx, "date"),
                                                                    StringArgumentType.getString(ctx, "time"));
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
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

    /**
     * 把给定时刻当作"现在"，输出模组本应显示的峰谷提示。
     * <p>用于查看任意时刻（含跨天、跨假期）的判断结果。
     *
     * @param date 日期，格式 {@code YYYY-MM-DD}
     * @param time 时间，格式 {@code mm.ss}，即小时.分钟
     */
    private static void runDeepSeekDateTest(String date, String time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LocalDateTime moment;
        try {
            LocalDate day = LocalDate.parse(date);
            String[] parts = time.split("\\.");
            if (parts.length != 2) throw new IllegalArgumentException("时间需要 mm.ss");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour > 23 || minute > 59) throw new IllegalArgumentException("时间超出范围");
            moment = day.atTime(hour, minute);
        } catch (Exception e) {
            mc.player.displayClientMessage(
                    Component.literal("[VerityConfig] 时间格式错误，应为 /vc dshint date YYYY-MM-DD mm.ss，例如 2026-10-01 10.00")
                            .withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        DeepSeekHintHandler.sendHintAt(moment);
    }

    /** 反馈 DeepSeek 峰谷提示开关的当前状态。 */
    private static void sendToggleMessage(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.displayClientMessage(
                Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("DeepSeek 峰谷时段提示已").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(enabled ? "开启" : "关闭")
                                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                        .append(Component.literal("。").withStyle(ChatFormatting.WHITE)),
                false);
    }

    private static void showHelpPrompt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component msg = Component.literal("[VerityConfig] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("输入 ").withStyle(ChatFormatting.WHITE))
                .append(ChatLinks.suggestCmd("/vc help", ChatLinks.CLICK_TO_FILL))
                .append(Component.literal(" 查看指令列表，或 ").withStyle(ChatFormatting.WHITE))
                .append(ChatLinks.suggestCmd("/vc cs", ChatLinks.CLICK_TO_FILL))
                .append(Component.literal(" 打开配置界面。").withStyle(ChatFormatting.WHITE));

        mc.player.displayClientMessage(msg, false);
    }

    private static void showCommandList() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.displayClientMessage(Component.literal("==== VerityConfig 指令 ====").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD), false);
        mc.player.displayClientMessage(ChatLinks.suggestLine("/vc cs", "打开配置界面"), false);
        mc.player.displayClientMessage(ChatLinks.suggestLine("/vc modls", "打开模组管理界面"), false);
        mc.player.displayClientMessage(ChatLinks.suggestLine("/vc help", "查看此列表"), false);
        mc.player.displayClientMessage(ChatLinks.suggestLine("/vc qanda", "打开常见问题解答"), false);
        mc.player.displayClientMessage(ChatLinks.suggestLine("/vc dshint", "开关 DeepSeek 峰谷时段提示"), false);
        mc.player.displayClientMessage(ChatLinks.suggestLine("/vc dshint date", "按当前时间查询一次峰谷提示"), false);
        mc.player.displayClientMessage(ChatLinks.suggestLine("/vc verity mfix", "修复 Verity 的聊天记忆（移除空 AI 消息）"), false);
        mc.player.displayClientMessage(ChatLinks.suggestLine("/vc verity mdel", "删除记忆文件（让 Verity 重新生成）"), false);
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
    // 「可点击填入指令」的组件构造已提取到 ChatLinks，与错误提示、峰谷提示共用
}