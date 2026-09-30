package com.cb2495.verityconfig;

import com.cb2495.verityconfig.util.ModConfig;
import com.cb2495.verityconfig.util.VerityMemoryManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
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
                        )
                        .then(new HiddenLiteralBuilder("test")
                                .then(new HiddenLiteralBuilder("dsdate")
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
     * 测试指令：把给定时刻当作"现在"，输出模组本应显示的峰谷提示。
     * <p>用于验证任意时刻（含跨天、跨假期）的判断结果。
     *
     * @param date 日期，格式 {@code YYYY-MM-DD}
     * @param time 时间，格式 {@code mm:ss}，即小时:分钟
     */
    private static void runDeepSeekDateTest(String date, String time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        LocalDateTime moment;
        try {
            LocalDate day = LocalDate.parse(date);
            String[] parts = time.split(":");
            if (parts.length != 2) throw new IllegalArgumentException("时间需要 mm:ss");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour > 23 || minute > 59) throw new IllegalArgumentException("时间超出范围");
            moment = day.atTime(hour, minute);
        } catch (Exception e) {
            mc.player.displayClientMessage(
                    Component.literal("[VerityConfig] 时间格式错误，应为 /vc test dsdate YYYY-MM-DD mm:ss，例如 2026-10-01 10:00")
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
        mc.player.displayClientMessage(suggestLine("/vc dshint", "开关 DeepSeek 峰谷时段提示"), false);
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

    /**
     * 不参与 Tab 补全的字面量命令节点。
     * <p>Brigadier 默认会把所有子节点列入补全，覆盖 {@code listSuggestions}
     * 返回空列表即可让该节点（及其子节点）不出现在补全中，
     * 同时仍可正常解析与执行。
     */
    private static class HiddenLiteralBuilder extends LiteralArgumentBuilder<CommandSourceStack> {

        HiddenLiteralBuilder(String literal) {
            super(literal);
        }

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            LiteralCommandNode<CommandSourceStack> node = new LiteralCommandNode<>(
                    getLiteral(),
                    getCommand(),
                    getRequirement(),
                    getRedirect(),
                    getRedirectModifier(),
                    isFork()) {
                @Override
                public java.util.concurrent.CompletableFuture<Suggestions> listSuggestions(
                        CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
                    // 返回空补全，使该节点不出现在 Tab 列表中
                    return Suggestions.empty();
                }
            };
            // 与父类 build() 一致：把子节点挂到新节点上，否则子命令会全部丢失
            for (CommandNode<CommandSourceStack> argument : getArguments()) {
                node.addChild(argument);
            }
            return node;
        }
    }
}