package com.hengx.damageprobe.test;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 自检专用"伤害被改"场景：
 *
 * <ul>
 *   <li>{@code damageprobe_fixture}：NORMAL 把 amount 减半(20 → 10)。</li>
 *   <li>{@code damageprobe_multiply}：NORMAL 乘 6.42；LOW 再无条件
 *       {@code setAmount(当前值)} 一次，模拟神化 {@code AdventureEvents.onDamage} 的"空写"。
 *       探针必须只记录真正改变值的 20 → 128.4，而不是被空写覆盖成"最后改动者"。</li>
 * </ul>
 *
 * <p>位于 {@code ...test} 包，不在探针 core/mixin 的过滤范围内，所以
 * {@link com.hengx.damageprobe.core.AmountCapture} 能把它抓成改动点。</p>
 */
public final class DamageModifierFixture {

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onHurt(LivingHurtEvent event) {
        if (event.getEntity().getTags().contains("damageprobe_fixture")) {
            event.setAmount(event.getAmount() * 0.5F);
        }
        else if (event.getEntity().getTags().contains("damageprobe_multiply")) {
            event.setAmount(event.getAmount() * 6.42F);
        }
    }

    /** 模拟"末尾无条件 setAmount(当前值)"：值没变，不应计入改动。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurtEmptyWrite(LivingHurtEvent event) {
        if (event.getEntity().getTags().contains("damageprobe_multiply")) {
            event.setAmount(event.getAmount());
        }
    }
}
