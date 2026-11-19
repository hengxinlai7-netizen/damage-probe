package com.hengx.damageprobe.command;

import com.hengx.damageprobe.ProbeConfig;
import com.hengx.damageprobe.ProbeState;
import com.hengx.damageprobe.core.ProbeEngine;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * {@code /damageprobe ...}。
 *
 * <ul>
 *   <li>{@code /damageprobe verbose} 切换详细模式(等价 on/off)</li>
 *   <li>{@code /damageprobe on|off} 运行时开关(不写 TOML)</li>
 *   <li>{@code /damageprobe status} 当前配置/计数</li>
 * </ul>
 */
public final class ProbeCommand {

    private ProbeCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("damageprobe")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("verbose")
                                .executes(ctx -> {
                                    boolean on = ProbeState.toggleVerbose();
                                    return reply(ctx.getSource(), "详细模式: " + (on ? "开 (stages+context)" : "关"));
                                })
                                .then(Commands.literal("on").executes(ctx -> {
                                    ProbeState.setVerbose(true);
                                    return reply(ctx.getSource(), "详细模式: 开 (stages+context)");
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    ProbeState.setVerbose(false);
                                    return reply(ctx.getSource(), "详细模式: 关");
                                })))
                        .then(Commands.literal("on").executes(ctx -> {
                            ProbeState.setEnabled(Boolean.TRUE);
                            return reply(ctx.getSource(), "已开启 (运行时)");
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            ProbeState.setEnabled(Boolean.FALSE);
                            return reply(ctx.getSource(), "已关闭 (运行时)");
                        }))
                        .then(Commands.literal("effects")
                                .executes(ctx -> {
                                    boolean on = !ProbeState.effectsEnabled();
                                    ProbeState.setEffectsEnabled(on);
                                    return reply(ctx.getSource(), "效果探针: " + (on ? "开" : "关"));
                                })
                                .then(Commands.literal("on").executes(ctx -> {
                                    ProbeState.setEffectsEnabled(Boolean.TRUE);
                                    return reply(ctx.getSource(), "效果探针: 开");
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    ProbeState.setEffectsEnabled(Boolean.FALSE);
                                    return reply(ctx.getSource(), "效果探针: 关");
                                })))
                        .then(Commands.literal("modifiers")
                                .executes(ctx -> {
                                    boolean on = !ProbeState.modifiersEnabled();
                                    ProbeState.setModifiersEnabled(on);
                                    return reply(ctx.getSource(), "修饰符探针: " + (on ? "开" : "关"));
                                })
                                .then(Commands.literal("on").executes(ctx -> {
                                    ProbeState.setModifiersEnabled(Boolean.TRUE);
                                    return reply(ctx.getSource(), "修饰符探针: 开");
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    ProbeState.setModifiersEnabled(Boolean.FALSE);
                                    return reply(ctx.getSource(), "修饰符探针: 关");
                                })))
                        .then(Commands.literal("rejected")
                                .executes(ctx -> {
                                    boolean on = !ProbeState.rejectedOnly();
                                    ProbeState.setRejectedOnly(on);
                                    return reply(ctx.getSource(), "只显示被拒绝: " + (on ? "开" : "关"));
                                })
                                .then(Commands.literal("on").executes(ctx -> {
                                    ProbeState.setRejectedOnly(Boolean.TRUE);
                                    return reply(ctx.getSource(), "只显示被拒绝: 开");
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    ProbeState.setRejectedOnly(Boolean.FALSE);
                                    return reply(ctx.getSource(), "只显示被拒绝: 关");
                                })))
                        .then(Commands.literal("filter")
                                .executes(ctx -> reply(ctx.getSource(),
                                        "filter=" + (ProbeState.playerRelatedOnly() ? "players" : "all")
                                                + " (运行时覆盖=" + (ProbeState.playerRelatedOnlyOverride() != null ? "是" : "否") + ")"))
                                .then(Commands.literal("all").executes(ctx -> {
                                    ProbeState.setPlayerRelatedOnly(Boolean.FALSE);
                                    return reply(ctx.getSource(), "filter=all (只记录玩家相关=关，立即生效；不写配置文件)");
                                }))
                                .then(Commands.literal("players").executes(ctx -> {
                                    ProbeState.setPlayerRelatedOnly(Boolean.TRUE);
                                    return reply(ctx.getSource(), "filter=players (只记录玩家相关=开，立即生效；不写配置文件)");
                                }))
                                .then(Commands.literal("status").executes(ctx -> reply(ctx.getSource(),
                                        "filter=" + (ProbeState.playerRelatedOnly() ? "players" : "all")
                                                + " (运行时覆盖=" + (ProbeState.playerRelatedOnlyOverride() != null ? "是" : "否")
                                                + ", 配置=" + (ProbeConfig.probePlayerRelatedOnly() ? "players" : "all") + ")"))))
                        .then(Commands.literal("status").executes(ctx -> reply(ctx.getSource(),
                                ProbeState.describe() + " open=" + ProbeEngine.openCount())))
        );
    }

    private static int reply(CommandSourceStack source, String message) {
        Component component = Component.literal("[伤害探针] " + message).withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> component, false);
        return 1;
    }
}
