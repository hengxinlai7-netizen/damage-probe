package com.hengx.damageprobe;

import org.slf4j.Logger;

import com.hengx.damageprobe.command.ProbeCommand;
import com.hengx.damageprobe.core.DamageProbeEvents;
import com.hengx.damageprobe.test.DamageModifierFixture;
import com.hengx.damageprobe.test.SelfTest;
import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * 伤害探针 1.0.0 —— 观测一次伤害从"意图"到"实际掉血"的全过程。
 *
 * <p>本模组只读事件、只在 {@code ServerTickEvent.END} 读血量,不修改伤害。
 * 唯一的 Mixin 是给 {@code LivingHurtEvent#setAmount} 加一个 HEAD 注入,
 * 用来记录"谁调用了 setAmount"的调用栈;不改变任何行为。</p>
 */
@Mod(DamageProbe.MODID)
public class DamageProbe {

    public static final String MODID = "damageprobe";
    public static final String VERSION = "1.2.1";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DamageProbe() {
        ProbeConfig.register();

        MinecraftForge.EVENT_BUS.register(new DamageProbeEvents());
        MinecraftForge.EVENT_BUS.addListener(ProbeCommand::register);

        // 只有显式打开自检开关时才加载测试触发器/测试 handler,正常游戏零存在感。
        if (Boolean.getBoolean("damageprobe.selftest")) {
            ProbeState.setSelftest(true);
            MinecraftForge.EVENT_BUS.register(new DamageModifierFixture());
            MinecraftForge.EVENT_BUS.register(new SelfTest());
            LOGGER.info("[伤害探针] 自检模式已启用 (-Ddamageprobe.selftest=true)");
        }

        LOGGER.info("[伤害探针] v{} 已加载; 配置文件 config/damageprobe-common.toml", VERSION);
    }
}
