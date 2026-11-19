package com.hengx.damageprobe.output;

import java.util.ArrayList;
import java.util.List;

import com.hengx.damageprobe.DamageProbe;
import com.hengx.damageprobe.ProbeConfig;
import com.hengx.damageprobe.ProbeState;
import com.hengx.damageprobe.core.HitRecord;
import com.hengx.damageprobe.core.ProbeEngine;
import com.hengx.damageprobe.core.ProbeFormatter;
import com.hengx.damageprobe.probe.ProbeEventStore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 双通道输出:日志 + 聊天栏。
 *
 * <p>分层由 {@code [output]} 控制：summary / stages / context / attribution。
 * {@code /damageprobe verbose} 等价于把 stages + context 临时打开(按命中时快照)。</p>
 *
 * <p>聊天栏只发给 OP(单人游戏发给房主)，且受 {@code toChat} 开关控制；异步伤害时
 * 用 {@code server.execute} 把发送动作 Marshal 回主线程，避免从异步线程碰玩家对象。</p>
 */
public final class Output {

    private Output() {
    }

    public static void emit(HitRecord record) {
        ProbeState.markEmitted();

        boolean verbose = record.verboseAtHit;
        boolean summary = ProbeConfig.summary();
        boolean stages = ProbeConfig.stages() || verbose;
        boolean context = ProbeConfig.context() || verbose;
        boolean attribution = ProbeConfig.attribution();

        List<String> lines = new ArrayList<>();
        if (summary) {
            lines.add(ProbeFormatter.summary(record, attribution));
        }
        if (stages) {
            int max = ProbeConfig.maxLinesPerHit();
            lines.addAll(ProbeFormatter.truncate(ProbeFormatter.details(record), max));
        }
        if (context && record.context != null) {
            lines.addAll(record.context.lines());
        }
        if (attribution && !summary) {
            String text = ProbeFormatter.attribution(record);
            if (text != null && !text.isEmpty()) {
                lines.add("归属: " + text);
            }
        }
        List<String> linked = ProbeEventStore.linked(record.tickRecorded, record.entityId, record.causingEntityId);
        for (String line : linked) {
            record.linked.add(line);
            lines.add("  [联动] " + line);
        }
        if (lines.isEmpty()) {
            return;
        }

        if (ProbeConfig.toLog()) {
            for (String line : lines) {
                DamageProbe.LOGGER.info("[伤害探针] {}", line);
            }
        }

        if (ProbeConfig.toChat()) {
            chat(record, lines);
        }
    }

    /** 渲染聊天栏组件;单独暴露出来便于自检验证颜色/文本。 */
    public static List<Component> components(HitRecord record, List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            ChatFormatting body = ChatFormatting.GRAY;
            if (record.isAsync()) {
                body = ChatFormatting.RED;
            }
            else if (line.contains("已取消")) {
                body = ChatFormatting.YELLOW;
            }
            else if (line.startsWith("归属")) {
                body = ChatFormatting.AQUA;
            }
            else if (line.startsWith("上下文")) {
                body = ChatFormatting.BLUE;
            }
            else if (line.startsWith("===") || i == 0) {
                body = ChatFormatting.WHITE;
            }
            else if (line.contains("[HEAD]") || line.contains("[TAIL]")) {
                body = ChatFormatting.LIGHT_PURPLE;
            }
            Component c = Component.literal("[伤害探针] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(line).withStyle(body));
            if (record.isAsync() && i == 0) {
                c = c.copy().append(Component.literal(" [异步!]").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            out.add(c);
        }
        return out;
    }

    /** 直接发给一个玩家(自检用假玩家捕获;正常路径见 {@link #chat}). */
    public static void sendChatTo(ServerPlayer player, List<String> lines, HitRecord record) {
        for (Component component : components(record, lines)) {
            player.sendSystemMessage(component);
        }
    }

    private static void chat(HitRecord record, List<String> lines) {
        MinecraftServer server = ProbeEngine.server();
        if (server == null) {
            return;
        }
        Runnable task = () -> {
            try {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!player.hasPermissions(2) && !server.isSingleplayer()) {
                        continue;
                    }
                    sendChatTo(player, lines, record);
                }
            }
            catch (Throwable t) {
                DamageProbe.LOGGER.warn("[伤害探针] 发送聊天栏输出失败", t);
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
