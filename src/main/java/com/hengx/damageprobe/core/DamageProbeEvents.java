package com.hengx.damageprobe.core;

import com.hengx.damageprobe.probe.ProbeEventStore;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 事件钩子。优先级刻意固定:
 *
 * <ul>
 *   <li>LivingAttackEvent HIGHEST:最早看到"意图伤害";LOWEST:看它最终有没有被取消。</li>
 *   <li>LivingHurtEvent HIGHEST:进入 hurt 的值;LOWEST(receiveCanceled):所有模组改完后的值。</li>
 *   <li>LivingDamageEvent LOWEST(receiveCanceled):护甲/附魔/抗性/吸收处理完的最终值。</li>
 *   <li>ServerTickEvent END LOWEST:所有实体 tick 完、原版 setHealth 已执行,读真实血量。</li>
 * </ul>
 */
public final class DamageProbeEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onLivingAttack(LivingAttackEvent event) {
        ProbeEngine.onAttack(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingAttackLowest(LivingAttackEvent event) {
        ProbeEngine.onAttackLowest(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onLivingHurt(LivingHurtEvent event) {
        ProbeEngine.onHurtHighest(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingHurtLowest(LivingHurtEvent event) {
        ProbeEngine.onHurtLowest(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onLivingDamage(LivingDamageEvent event) {
        ProbeEngine.onDamageHighest(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingDamageLowest(LivingDamageEvent event) {
        ProbeEngine.onDamageLowest(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ProbeEngine.onServerTickStart();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ProbeEngine.onServerTickEnd();
            ProbeEventStore.onServerTickEnd();
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ProbeEngine.setServer(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ProbeEventStore.flushAll();
        ProbeEventStore.clear();
        ProbeEngine.clearServer();
    }
}
