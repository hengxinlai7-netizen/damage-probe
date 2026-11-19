package com.hengx.damageprobe.probe;

import com.hengx.damageprobe.DamageProbe;
import com.hengx.damageprobe.ProbeConfig;
import com.hengx.damageprobe.core.ProbeEngine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 效果/修饰符探针的日志与聊天栏输出。 */
public final class ProbeOutput {

    private ProbeOutput() {
    }

    public static void emit(ProbeEvent.Category category, ProbeEvent.Result result, String text) {
        String prefix = "[" + category.prefix + "] ";
        if (ProbeConfig.probeToLog()) {
            DamageProbe.LOGGER.info(prefix + "{}", text);
        }
        if (ProbeConfig.probeToChat()) {
            chat(category, result, prefix + text);
        }
    }

    public static void emitOmitted(ProbeEvent.Category category, int omitted) {
        String text = "…省略 " + omitted + " 条";
        String prefix = "[" + category.prefix + "] ";
        if (ProbeConfig.probeToLog()) {
            DamageProbe.LOGGER.info(prefix + text);
        }
        if (ProbeConfig.probeToChat()) {
            chat(category, ProbeEvent.Result.NONE, prefix + text);
        }
    }

    private static void chat(ProbeEvent.Category category, ProbeEvent.Result result, String text) {
        MinecraftServer server = ProbeEngine.server();
        if (server == null) {
            return;
        }
        ChatFormatting color;
        if (result == ProbeEvent.Result.REJECTED) {
            color = ChatFormatting.RED;
        }
        else if (result == ProbeEvent.Result.REMOVED || result == ProbeEvent.Result.ALL_REMOVED) {
            color = ChatFormatting.YELLOW;
        }
        else {
            color = category == ProbeEvent.Category.EFFECT
                    ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA;
        }
        Component component = Component.literal(text).withStyle(color);

        Runnable task = () -> {
            try {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!player.hasPermissions(2) && !server.isSingleplayer()) {
                        continue;
                    }
                    player.sendSystemMessage(component);
                }
            }
            catch (Throwable t) {
                DamageProbe.LOGGER.warn("[伤害探针] 发送效果/修饰符聊天输出失败", t);
            }
        };
        if (server.isSameThread()) {
            task.run();
        }
        else {
            server.execute(task);
        }
    }
}
