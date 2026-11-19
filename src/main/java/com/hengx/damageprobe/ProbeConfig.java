package com.hengx.damageprobe;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 配置 {@code config/damageprobe-common.toml}。
 *
 * <pre>
 * [general]
 *     enabled = true
 *     toChat = true
 *     toLog = true
 *     playerOnly = false
 *     playerTargetOnly = false
 *     maxLinesPerHit = 24
 *
 * [output]
 *     summary = true
 *     stages = false
 *     context = false
 *     attribution = true
 * </pre>
 */
public final class ProbeConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue TO_CHAT;
    public static final ForgeConfigSpec.BooleanValue TO_LOG;
    public static final ForgeConfigSpec.BooleanValue PLAYER_ONLY;
    public static final ForgeConfigSpec.BooleanValue PLAYER_TARGET_ONLY;
    public static final ForgeConfigSpec.IntValue MAX_LINES_PER_HIT;

    public static final ForgeConfigSpec.BooleanValue SUMMARY;
    public static final ForgeConfigSpec.BooleanValue STAGES;
    public static final ForgeConfigSpec.BooleanValue CONTEXT;
    public static final ForgeConfigSpec.BooleanValue ATTRIBUTION;

    public static final ForgeConfigSpec.BooleanValue PROBE_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue PROBE_MODIFIERS;
    public static final ForgeConfigSpec.BooleanValue PROBE_PLAYER_RELATED_ONLY;
    public static final ForgeConfigSpec.BooleanValue PROBE_REJECTED_ONLY;
    public static final ForgeConfigSpec.BooleanValue PROBE_TO_CHAT;
    public static final ForgeConfigSpec.BooleanValue PROBE_TO_LOG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("伤害探针:记录每一次伤害的意图/实际值/真实扣血/线程/改动点/归属。").push("general");

        ENABLED = b.comment("总开关。默认 false —— 这是排查工具,平时开着一方面刷日志, 一方面要占约 1% 服务端 CPU。",
                        "排查时用 /damageprobe on 打开即可;关闭后事件钩子直接返回,不产生任何记录。")
                .define("enabled", false);

        TO_CHAT = b.comment("输出到聊天栏(只发给 OP;单人游戏发给房主)。默认 true。")
                .define("toChat", true);

        TO_LOG = b.comment("输出到日志(每行带 [伤害探针] 前缀)。默认 true。")
                .define("toLog", true);

        PLAYER_ONLY = b.comment("只记录'由玩家造成'的伤害(DamageSource 的造成者/直接来源是玩家)。默认 false。")
                .define("playerOnly", false);

        PLAYER_TARGET_ONLY = b.comment("只记录'玩家受到'的伤害。默认 false。")
                .define("playerTargetOnly", false);

        MAX_LINES_PER_HIT = b.comment("阶段明细(stages)最多输出几行,防止多段伤害刷屏。",
                        "摘要/上下文/归属是固定行数,不受这个限制。",
                        "范围 4..128。")
                .defineInRange("maxLinesPerHit", 24, 4, 128);

        b.pop();

        b.comment("输出分层。").push("output");
        SUMMARY = b.comment("输出一行摘要。默认 true。")
                .define("summary", true);
        STAGES = b.comment("输出逐阶段明细(攻击/受伤/伤害/actuallyHurt/结算)。默认 false。")
                .define("stages", false);
        CONTEXT = b.comment("输出上下文快照(攻击者属性/效果、目标护甲/血量/效果、8 个 bypass tag)。默认 false。")
                .define("context", false);
        ATTRIBUTION = b.comment("输出'理论 → 最终扣血'的归属分析。默认 true。")
                .define("attribution", true);
        b.pop();

        b.comment("状态效果 / 属性修饰符探针。").push("probe");
        PROBE_EFFECTS = b.comment("记录 MobEffect 的施加/移除。默认 true。")
                .define("effects", true);
        PROBE_MODIFIERS = b.comment("记录 AttributeModifier 的添加/移除。默认 true。")
                .define("modifiers", true);
        PROBE_PLAYER_RELATED_ONLY = b.comment("只记录与玩家相关的（施加者或目标任一是玩家）。默认 true，防止怪物互刷屏。")
                .define("playerRelatedOnly", true);
        PROBE_REJECTED_ONLY = b.comment("只记录被拒绝的施加（排查免疫问题）。默认 false。")
                .define("rejectedOnly", false);
        PROBE_TO_CHAT = b.comment("效果/修饰符记录输出到聊天栏（仅 OP/单人）。默认 true。")
                .define("toChat", true);
        PROBE_TO_LOG = b.comment("效果/修饰符记录输出到日志。默认 true。")
                .define("toLog", true);
        b.pop();

        SPEC = b.build();
    }

    private ProbeConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "damageprobe-common.toml");
    }

    public static boolean enabled() {
        try {
            return ENABLED.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public static boolean toChat() {
        try {
            return TO_CHAT.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public static boolean toLog() {
        try {
            return TO_LOG.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public static boolean playerOnly() {
        try {
            return PLAYER_ONLY.get();
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public static boolean playerTargetOnly() {
        try {
            return PLAYER_TARGET_ONLY.get();
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public static int maxLinesPerHit() {
        try {
            return MAX_LINES_PER_HIT.get();
        }
        catch (RuntimeException | LinkageError e) {
            return 24;
        }
    }

    public static boolean summary() {
        try {
            return SUMMARY.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public static boolean stages() {
        try {
            return STAGES.get();
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public static boolean context() {
        try {
            return CONTEXT.get();
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public static boolean attribution() {
        try {
            return ATTRIBUTION.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public static boolean probeEffects() {
        try {
            return PROBE_EFFECTS.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public static boolean probeModifiers() {
        try {
            return PROBE_MODIFIERS.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public static boolean probePlayerRelatedOnly() {
        try {
            return PROBE_PLAYER_RELATED_ONLY.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public static boolean probeRejectedOnly() {
        try {
            return PROBE_REJECTED_ONLY.get();
        }
        catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public static boolean probeToChat() {
        try {
            return PROBE_TO_CHAT.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    public static boolean probeToLog() {
        try {
            return PROBE_TO_LOG.get();
        }
        catch (RuntimeException | LinkageError e) {
            return true;
        }
    }
}
