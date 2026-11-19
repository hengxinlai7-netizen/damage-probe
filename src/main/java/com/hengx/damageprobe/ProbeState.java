package com.hengx.damageprobe;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 运行时状态:命令开关 / 自检覆盖 / 计数。
 *
 * <p>配置值每 tick 都可读,但命令切换是运行时的,不写回 TOML。</p>
 */
public final class ProbeState {

    private static volatile Boolean enabledOverride;
    private static volatile boolean verbose;
    private static volatile boolean selftest;

    /** 自检用:临时覆盖 playerOnly/playerTargetOnly(避免改配置文件)。 */
    private static volatile Boolean testPlayerOnly;
    private static volatile Boolean testPlayerTargetOnly;

    /** 效果/修饰符探针的运行时覆盖。 */
    private static volatile Boolean effectsOverride;
    private static volatile Boolean modifiersOverride;
    private static volatile Boolean rejectedOnlyOverride;
    private static volatile Boolean playerRelatedOnlyOverride;

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicLong EMITTED = new AtomicLong();

    private ProbeState() {
    }

    public static long nextId() {
        return SEQUENCE.incrementAndGet();
    }

    public static long emittedCount() {
        return EMITTED.get();
    }

    public static long markEmitted() {
        return EMITTED.incrementAndGet();
    }

    public static boolean enabled() {
        Boolean o = enabledOverride;
        return o != null ? o : ProbeConfig.enabled();
    }

    public static void setEnabled(Boolean value) {
        enabledOverride = value;
    }

    public static Boolean enabledOverride() {
        return enabledOverride;
    }

    public static boolean verbose() {
        return verbose;
    }

    public static void setVerbose(boolean value) {
        verbose = value;
    }

    public static boolean toggleVerbose() {
        verbose = !verbose;
        return verbose;
    }

    public static boolean playerOnly() {
        Boolean o = testPlayerOnly;
        return o != null ? o : ProbeConfig.playerOnly();
    }

    public static boolean playerTargetOnly() {
        Boolean o = testPlayerTargetOnly;
        return o != null ? o : ProbeConfig.playerTargetOnly();
    }

    public static void setTestPlayerOnly(Boolean value) {
        testPlayerOnly = value;
    }

    public static void setTestPlayerTargetOnly(Boolean value) {
        testPlayerTargetOnly = value;
    }

    public static boolean effectsEnabled() {
        Boolean o = effectsOverride;
        return o != null ? o : ProbeConfig.probeEffects();
    }

    public static boolean modifiersEnabled() {
        Boolean o = modifiersOverride;
        return o != null ? o : ProbeConfig.probeModifiers();
    }

    public static boolean rejectedOnly() {
        Boolean o = rejectedOnlyOverride;
        return o != null ? o : ProbeConfig.probeRejectedOnly();
    }

    /** 运行时过滤覆盖；null 表示用配置文件 playerRelatedOnly（启动默认值）。 */
    public static boolean playerRelatedOnly() {
        Boolean o = playerRelatedOnlyOverride;
        return o != null ? o : ProbeConfig.probePlayerRelatedOnly();
    }

    public static void setPlayerRelatedOnly(Boolean value) {
        playerRelatedOnlyOverride = value;
    }

    public static Boolean playerRelatedOnlyOverride() {
        return playerRelatedOnlyOverride;
    }

    public static void setEffectsEnabled(Boolean value) {
        effectsOverride = value;
    }

    public static void setModifiersEnabled(Boolean value) {
        modifiersOverride = value;
    }

    public static void setRejectedOnly(Boolean value) {
        rejectedOnlyOverride = value;
    }

    public static Boolean effectsOverride() {
        return effectsOverride;
    }

    public static Boolean modifiersOverride() {
        return modifiersOverride;
    }

    public static Boolean rejectedOnlyOverride() {
        return rejectedOnlyOverride;
    }

    public static boolean selftest() {
        return selftest;
    }

    public static void setSelftest(boolean value) {
        selftest = value;
    }

    public static String describe() {
        Boolean o = enabledOverride;
        return "enabled=" + enabled() + (o != null ? "(运行时)" : "(配置=" + ProbeConfig.enabled() + ")")
                + " verbose=" + verbose
                + " toChat=" + ProbeConfig.toChat()
                + " toLog=" + ProbeConfig.toLog()
                + " playerOnly=" + playerOnly()
                + " playerTargetOnly=" + ProbeConfig.playerTargetOnly()
                + " maxLinesPerHit=" + ProbeConfig.maxLinesPerHit()
                + " output[summary=" + ProbeConfig.summary()
                + " stages=" + ProbeConfig.stages()
                + " context=" + ProbeConfig.context()
                + " attribution=" + ProbeConfig.attribution() + "]"
                + " probe[effects=" + effectsEnabled()
                + " modifiers=" + modifiersEnabled()
                + " filter=" + (playerRelatedOnly() ? "players" : "all")
                + " playerRelatedOnly=" + playerRelatedOnly()
                + " rejectedOnly=" + rejectedOnly() + "]"
                + " emitted=" + emittedCount();
    }
}
